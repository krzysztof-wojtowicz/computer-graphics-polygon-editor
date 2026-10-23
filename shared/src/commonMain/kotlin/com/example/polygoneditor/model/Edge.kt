package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2

sealed interface EdgeShape {
    data object Line : EdgeShape

    // c1 near start, c2 near end
    class Bezier(val c1: ControlPoint, val c2: ControlPoint) : EdgeShape

    data object Arc : EdgeShape
}

class Edge(val start: Vertex, val end: Vertex, var shape: EdgeShape = EdgeShape.Line) {
    val controlPoints: List<ControlPoint>
        get() = when (val s = shape) {
            is EdgeShape.Bezier -> listOf(s.c1, s.c2)
            EdgeShape.Line, EdgeShape.Arc -> emptyList()
        }

    // Ends + control points – everything that moves when the edge is dragged
    val points: List<Point> get() = listOf(start, end) + controlPoints

    // Distance between the ends (for a curve: the chord, not the curve length)
    val length: Double get() = start.pos.distanceTo(end.pos)

    // Returns vector representing edge
    val vector: Vec2 get() = end.pos - start.pos

    // The point right next to v on this edge: the other end of a line, the control point of a Bézier, none for an arc
    fun pointNextTo(v: Vertex): Point? = when (val s = shape) {
        EdgeShape.Line -> if (v === start) end else start
        is EdgeShape.Bezier -> if (v === start) s.c1 else s.c2
        EdgeShape.Arc -> null
    }
}
