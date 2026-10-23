package com.example.polygoneditor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.distanceToSegment
import com.example.polygoneditor.model.Continuity
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Point
import com.example.polygoneditor.model.Polygon
import com.example.polygoneditor.model.Scene
import com.example.polygoneditor.model.Vertex
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.solver.Locks
import com.example.polygoneditor.solver.Solver

enum class LineAlgorithm { Library, Bresenham }

/** What's under the cursor */
sealed interface Hit {
    val polygon: Polygon

    data class OnVertex(override val polygon: Polygon, val vertex: Vertex) : Hit
    data class OnEdge(override val polygon: Polygon, val edge: Edge) : Hit
    data class Inside(override val polygon: Polygon) : Hit
    data class OnControlPoint(override val polygon: Polygon, val edge: Edge, val point: ControlPoint) : Hit
}

/** What the user has selected */
sealed interface Selection {
    data object None : Selection
    data class OnVertex(val polygon: Polygon, val vertex: Vertex) : Selection
    data class OnEdge(val polygon: Polygon, val edge: Edge) : Selection
    data class TwoEdges(val first: Edge, val second: Edge) : Selection
    data class OnControlPoint(val polygon: Polygon, val edge: Edge, val point: ControlPoint) : Selection

    operator fun contains(e: Edge): Boolean = when (this) {
        is None, is OnVertex, is OnControlPoint -> false
        is OnEdge -> e === edge
        is TwoEdges -> e === first || e === second
    }

    operator fun contains(v: Vertex): Boolean = when (this) {
        is None, is OnEdge, is TwoEdges, is OnControlPoint -> false
        is OnVertex -> v === vertex
    }

    operator fun contains(c: ControlPoint): Boolean = when (this) {
        is None, is OnEdge, is TwoEdges, is OnVertex -> false
        is OnControlPoint -> c === point
    }
}

/** What's being dragged */
private sealed interface Drag {
    class MoveVertex(val vertex: Vertex, val polygon: Polygon) : Drag
    class MoveEdge(val edge: Edge, var last: Vec2, val polygon: Polygon) : Drag
    class MovePolygon(val polygon: Polygon, var last: Vec2) : Drag
    class MoveControlPoint(val point: ControlPoint, val edge: Edge, val polygon: Polygon) : Drag
}

/**
 * Editor controls the redrawing of UI using version, it's incremented after every change
 */
class Editor(val scene: Scene) {
    var version by mutableIntStateOf(0)
        private set

    var hovered: Hit? = null
        private set

    var message by mutableStateOf<String?>(null)
        private set

    private var drag: Drag? = null

    var selection by mutableStateOf<Selection>(Selection.None)
        private set

    var lineAlgorithm by mutableStateOf(LineAlgorithm.Library)

    // Gets the object which is under the cursor
    fun hitTest(p: Vec2): Hit? {
        // We look for vertex > controlPoint > edge > polygon in that order
        val polygons = scene.polygons.asReversed()

        // Vertex
        for (poly in polygons) {
            poly.vertices.firstOrNull { it.pos.distanceTo(p) <= VERTEX_HIT_RADIUS }
                ?.let { return Hit.OnVertex(poly, it) }
        }

        // Control point
        for (poly in polygons) {
            for (edge in poly.edges) {
                // controlPoints is empty for a line, [c1, c2] for a Bézier curve
                edge.controlPoints.firstOrNull { it.pos.distanceTo(p) <= VERTEX_HIT_RADIUS }
                    ?.let { return Hit.OnControlPoint(poly, edge, it) }
            }
        }

        // Edge
        for (poly in polygons) {
            poly.edges.firstOrNull {
                poly.polylineOf(it).zipWithNext().any { (a, b) -> distanceToSegment(p, a, b) <= EDGE_HIT_TOLERANCE }
            }
                ?.let { return Hit.OnEdge(poly, it) }
        }

        // Polygon
        return polygons.firstOrNull { p in it }?.let { Hit.Inside(it) }
    }

    // Helper fun that converts hit to selection
    private fun Hit?.toSelection(): Selection = when (this) {
        is Hit.OnVertex -> Selection.OnVertex(polygon, vertex)
        is Hit.OnEdge -> Selection.OnEdge(polygon, edge)
        is Hit.Inside -> Selection.None
        is Hit.OnControlPoint -> Selection.OnControlPoint(polygon, edge, point)
        null -> Selection.None
    }

    // Handles extension of selection with shift click
    private fun extendSelection(hit: Hit?): Selection {
        // Not an edge -> behaves like normal click
        if (hit !is Hit.OnEdge) return hit.toSelection()

        return when (val s = selection) {
            is Selection.None, is Selection.OnVertex, is Selection.OnControlPoint -> hit.toSelection()
            is Selection.OnEdge -> {
                if (hit.edge in s) s
                else Selection.TwoEdges(s.edge, hit.edge)
            }

            is Selection.TwoEdges -> {
                if (hit.edge in s) s
                else Selection.TwoEdges(s.first, hit.edge)
            }
        }
    }

    // Deletes selected vertex
    fun deleteSelected() {
        val s = selection as? Selection.OnVertex ?: return

        message = null
        if (!scene.removeVertex(s.polygon, s.vertex)) {
            message = "Polygon has to have at least ${Polygon.MIN_VERTICES} vertices."
            return
        }

        selection = Selection.None
        hovered = null
        drag = null
        changed()
    }

    // Moves the points by delta and then lets the solver fix the rest of the scene
    // according to constraints, if solver fails, moves the whole polygon
    private fun moveWithSolver(
        points: List<Point>, delta: Vec2, polygon: Polygon,
        protected: Set<Point> = emptySet(),
        carried: List<Point> = emptyList()
    ) {
        val s = scene.snapshot()

        points.forEach { it.pos += delta }
        carried.forEach { it.pos += delta }
        if (!Solver.solve(scene.allConstraints(), Locks(locked = points.toSet(), protected = protected + carried))) {
            scene.restore(s)
            polygon.translate(delta)
        }
    }

    // Adds new constraint and lets the solver fix the rest of the scene
    // if it's not possible, restores state before addition
    private fun addWithSolver(c: Constraint, replaced: List<Constraint> = emptyList()) {
        val s = scene.snapshot()

        replaced.forEach { scene.removeConstraint(it) }
        scene.addConstraint(c)

        if (!Solver.solve(scene.allConstraints(), Locks.None)) {
            scene.restore(s)
            scene.removeConstraint(c)
            replaced.forEach { scene.addConstraint(it) }
            message = "Constraint conflicts with existing ones."
            return
        }

        changed()
    }

    // Adds FixedLength constraint for given edge
    fun setLength(edge: Edge, length: Double) {
        message = null
        if (length <= 0 || !length.isFinite()) {
            message = "Length must be a positive number."
            return
        }
        if (edge.shape !is EdgeShape.Line) {
            message = "FixedLength constraint only applies to straight edges."
            return
        }

        addWithSolver(
            FixedLength(edge, length),
            scene.constraintsOf(edge).filterIsInstance<FixedLength>()
        )
    }

    // Adds Perpendicular constraint for two given edges
    fun makePerpendicular(e1: Edge, e2: Edge) {
        message = null
        if (e1 === e2) {
            message = "Select two different edges."
            return
        }
        if (e1.shape !is EdgeShape.Line || e2.shape !is EdgeShape.Line) {
            message = "Perpendicular constraint only applies to straight edges."
            return
        }
        val duplicate = scene.constraints.filterIsInstance<Perpendicular>().any {
            (it.e1 === e1 && it.e2 === e2) || (it.e1 === e2 && it.e2 === e1) // in either order
        }
        if (duplicate) {
            message = "These edges are already perpendicular."
            return
        }

        addWithSolver(Perpendicular(e1, e2))
    }

    // Handles edge shape change to bezier
    // Both ends get the default C1 (unless the user chose their class) and the new control points follow it
    fun toBezier(edge: Edge) {
        message = null
        if (edge.shape is EdgeShape.Bezier) return
        val polygon = polygonOf(edge)

        // Adds two control points, one at 1/3 of ab, second at 2/3 of ab
        val a = edge.start.pos
        val b = edge.end.pos
        val c1 = ControlPoint(a + (b - a) / 3.0)
        val c2 = ControlPoint(a + (b - a) * (2.0 / 3.0))

        scene.changeShape(edge, EdgeShape.Bezier(c1, c2))

        // Default C1 only where C1 makes sense (not next to an arc)
        val ends = listOf(edge.start, edge.end)
        ends.filter { !it.continuityExplicit && Continuity.C1 in polygon.allowedContinuities(it) }
            .forEach { it.continuity = Continuity.C1 }

        // Vertices stay, the neighbours' control points move only if they have to – the new curve adapts
        val s = scene.snapshot()
        val locks = Locks(locked = polygon.vertices.toSet(), protected = (polygon.points - setOf(c1, c2)).toSet())
        if (!Solver.solve(scene.allConstraints(), locks)) {
            // G0 adds no constraint, so the restored positions satisfy everything again
            scene.restore(s)
            ends.forEach { it.continuity = Continuity.G0 }
            message = "C1 couldn't be applied – the ends of the curve were set to G0."
        }
        changed()
    }

    private fun polygonOf(edge: Edge) = scene.polygons.first { edge in it.edges }

    // Handles edge shape change to line
    fun toLine(edge: Edge) {
        message = null
        if (edge.shape is EdgeShape.Line) return

        scene.changeShape(edge, EdgeShape.Line)
        changed()
    }

    // Handles edge shape change to arc
    fun toArc(edge: Edge) {
        message = null
        if (edge.shape is EdgeShape.Arc) return

        scene.changeShape(edge, EdgeShape.Arc)
        changed()
    }

    // Sets the continuity class at vertex v
    // Only one end of an arc can be G1 – setting it here switches the other end to G0
    fun setContinuity(polygon: Polygon, v: Vertex, c: Continuity) {
        message = null
        if (c !in polygon.allowedContinuities(v)) {
            message = "${c.name} is not available at this vertex."
            return
        }

        if (v.continuity == c) {
            // Same class, but now the user's choice – the "(default)" label has to go
            if (!v.continuityExplicit) {
                v.continuityExplicit = true
                changed()
            }
            return
        }

        // Transaction: positions + the classes (the one-end rule may switch another vertex too)
        val s = scene.snapshot()
        val classes = polygon.vertices.associateWith { it.continuity }
        v.continuity = c

        if (c == Continuity.G1) {
            for (edge in polygon.edgesAt(v).toList().filter { it.shape is EdgeShape.Arc }) {
                val other = if (edge.start === v) edge.end else edge.start
                if (other.continuity == Continuity.G1) {
                    other.continuity = Continuity.G0
                    message = "Only one end of an arc can be G1 – the other end was set to G0."
                }
            }
        }

        // The curve straightens at once – control points move, the vertex stays
        val locks = Locks(locked = setOf(v), protected = (polygon.vertices - v).toSet())
        if (!Solver.solve(scene.allConstraints(), locks)) {
            scene.restore(s)
            classes.forEach { (vertex, old) -> vertex.continuity = old }
            message = "${c.name} conflicts with the constraints of the neighbouring edges."
            return
        }

        v.continuityExplicit = true // chosen by the user, not a default – only once the change went through
        changed()
    }

    // Removes constraint
    fun removeConstraint(c: Constraint) {
        scene.removeConstraint(c)
        changed()
    }

    // ---- mouse actions ----

    // On left click
    fun onPrimaryPress(p: Vec2, shift: Boolean = false) {
        message = null
        val hit = hitTest(p)

        selection = if (shift) extendSelection(hit) else hit.toSelection()

        drag = if (shift) null else when (hit) {
            is Hit.OnVertex -> Drag.MoveVertex(hit.vertex, hit.polygon)
            is Hit.OnEdge -> Drag.MoveEdge(hit.edge, p, hit.polygon)
            is Hit.Inside -> Drag.MovePolygon(hit.polygon, p)
            is Hit.OnControlPoint -> Drag.MoveControlPoint(hit.point, hit.edge, hit.polygon)
            null -> null
        }
    }

    // On mouse move
    fun onMove(p: Vec2) {
        when (val d = drag) {
            is Drag.MoveVertex -> {
                val delta = p - d.vertex.pos
                val carried = d.polygon.controlPointsAt(d.vertex)
                moveWithSolver(listOf(d.vertex), delta, d.polygon, carried = carried)
            }

            is Drag.MoveControlPoint ->
                moveWithSolver(
                    listOf(d.point), p - d.point.pos, d.polygon,
                    protected = setOf(d.edge.start, d.edge.end)
                )

            is Drag.MoveEdge -> {
                val points = d.edge.points
                // The edge's own c1/c2 are on these lists too – they're already in points, don't move them twice
                val carried =
                    (d.polygon.controlPointsAt(d.edge.start) + d.polygon.controlPointsAt(d.edge.end))
                        .filter { it !in points }
                moveWithSolver(points, p - d.last, d.polygon, carried = carried)
                d.last = p
            }

            is Drag.MovePolygon -> {
                d.polygon.translate(p - d.last)
                d.last = p
            }

            null -> {
                val h = hitTest(p)
                if (h == hovered) return
                hovered = h
            }
        }
        changed()
    }

    fun onRelease() {
        drag = null
    }

    // On double left click – called right after onPrimaryPress of the second click
    fun onDoubleClick(p: Vec2) {
        val hit = hitTest(p) as? Hit.OnEdge ?: return
        val v = scene.insertVertex(hit.polygon, hit.edge)
        selection = Selection.OnVertex(hit.polygon, v)
        drag = null           // MoveEdge from onPrimaryPress refers to the edge that no longer exists
        hovered = hitTest(p)  // the old hovered edge no longer exists either
        changed()
    }

    private fun changed() {
        version++
    }

    companion object {
        const val VERTEX_HIT_RADIUS = 8.0
        const val EDGE_HIT_TOLERANCE = 5.0
    }
}
