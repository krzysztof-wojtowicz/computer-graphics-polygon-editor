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

/** What's being dragged */
private sealed interface Drag {
    class MoveVertex(val vertex: Vertex) : Drag
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

    // ---- mouse actions ----

    fun onPrimaryPress(p: Vec2) {
        message = null
        drag = when (val hit = hitTest(p)) {
            is Hit.OnVertex -> Drag.MoveVertex(hit.vertex)
            is Hit.OnEdge -> Drag.MovePolygon(hit.polygon, p)
            is Hit.Inside -> Drag.MovePolygon(hit.polygon, p)
            null -> null
        }
    }

    fun onMove(p: Vec2) {
        when (val d = drag) {
            is Drag.MoveVertex -> d.vertex.pos = p
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

    fun onSecondaryPress(p: Vec2) {
        message = null
        when (val hit = hitTest(p)) {
            is Hit.OnVertex ->
                if (!hit.polygon.removeVertex(hit.vertex)) {
                    message = "Polygon has to have at least ${Polygon.MIN_VERTICES} vertices."
                }

            is Hit.OnEdge -> hit.polygon.insertVertexOnEdge(hit.edge)
            is Hit.Inside, null -> return
        }
        hovered = hitTest(p)
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
