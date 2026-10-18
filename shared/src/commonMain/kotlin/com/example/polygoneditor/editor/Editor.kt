package com.example.polygoneditor.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.distanceToSegment
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.Polygon
import com.example.polygoneditor.model.Scene
import com.example.polygoneditor.model.Vertex

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
    class MoveVertex(val vertex: Vertex) : Drag
    class MoveEdge(val edge: Edge, var last: Vec2) : Drag
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

    // ---- mouse actions ----

    // On left click
    fun onPrimaryPress(p: Vec2, shift: Boolean = false) {
        message = null
        val hit = hitTest(p)

        selection = if (shift) extendSelection(hit) else hit.toSelection()

        drag = if (shift) null else when (hit) {
            is Hit.OnVertex -> Drag.MoveVertex(hit.vertex)
            is Hit.OnEdge -> Drag.MoveEdge(hit.edge, p)
            is Hit.Inside -> Drag.MovePolygon(hit.polygon, p)
            null -> null
        }
    }

    fun onMove(p: Vec2) {
        when (val d = drag) {
            is Drag.MoveVertex -> d.vertex.pos = p
            is Drag.MoveEdge -> {
                val delta = p - d.last
                d.edge.points.forEach { it.pos += delta }
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

    // On right click
    fun onSecondaryPress(p: Vec2) {
        message = null
        when (val hit = hitTest(p)) {
            is Hit.OnVertex ->
                if (!scene.removeVertex(hit.polygon, hit.vertex)) {
                    message = "Polygon has to have at least ${Polygon.MIN_VERTICES} vertices."
                }

            is Hit.OnEdge -> scene.insertVertex(hit.polygon, hit.edge)
            is Hit.Inside, null -> return
        }
        hovered = hitTest(p)
        selection = Selection.None
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
