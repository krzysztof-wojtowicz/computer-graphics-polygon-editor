package com.example.polygoneditor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.distanceToSegment
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
}

/** What the user has selected */
sealed interface Selection {
    data object None : Selection
    data class OnVertex(val polygon: Polygon, val vertex: Vertex) : Selection
    data class OnEdge(val polygon: Polygon, val edge: Edge) : Selection
    data class TwoEdges(val first: Edge, val second: Edge) : Selection

    operator fun contains(e: Edge): Boolean = when (this) {
        is None, is OnVertex -> false
        is OnEdge -> e === edge
        is TwoEdges -> e === first || e === second
    }

    operator fun contains(v: Vertex): Boolean = when (this) {
        is None, is OnEdge, is TwoEdges -> false
        is OnVertex -> v === vertex
    }
}

/** What's being dragged */
private sealed interface Drag {
    class MoveVertex(val vertex: Vertex, val polygon: Polygon) : Drag
    class MoveEdge(val edge: Edge, var last: Vec2, val polygon: Polygon) : Drag
    class MovePolygon(val polygon: Polygon, var last: Vec2) : Drag
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
        // We look for vertex > edge > polygon in that order
        val polygons = scene.polygons.asReversed()

        // Vertex
        for (poly in polygons) {
            poly.vertices.firstOrNull { it.pos.distanceTo(p) <= VERTEX_HIT_RADIUS }
                ?.let { return Hit.OnVertex(poly, it) }
        }

        // Edge
        for (poly in polygons) {
            poly.edges.firstOrNull {
                distanceToSegment(p, it.start.pos, it.end.pos) <= EDGE_HIT_TOLERANCE
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
        null -> Selection.None
    }

    // Handles extension of selection with shift click
    private fun extendSelection(hit: Hit?): Selection {
        // Not an edge -> behaves like normal click
        if (hit !is Hit.OnEdge) return hit.toSelection()

        return when (val s = selection) {
            is Selection.None, is Selection.OnVertex -> hit.toSelection()
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
    private fun moveWithSolver(points: List<Point>, delta: Vec2, polygon: Polygon) {
        val s = scene.snapshot()

        points.forEach { it.pos += delta }
        if (!Solver.solve(scene.constraints, Locks(locked = points.toSet()))) {
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

        if (!Solver.solve(scene.constraints, Locks.None)) {
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
            null -> null
        }
    }

    // On mouse move
    fun onMove(p: Vec2) {
        when (val d = drag) {
            is Drag.MoveVertex ->
                moveWithSolver(listOf(d.vertex), p - d.vertex.pos, d.polygon)

            is Drag.MoveEdge -> {
                moveWithSolver(d.edge.points, p - d.last, d.polygon)
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
