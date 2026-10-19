package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.bezierPoints
import com.example.polygoneditor.geometry.bezierSegments

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

    // Returns list of points representing given edge
    // for bezier it's list of calculated bezier points
    // for line its start & end
    val polyline: List<Vec2> get() = when (val s = shape) {
        is EdgeShape.Bezier -> bezierPoints(start.pos, s.c1.pos, s.c2.pos, end.pos,
            bezierSegments(start.pos, s.c1.pos, s.c2.pos, end.pos)
        )
        EdgeShape.Line, EdgeShape.Arc -> listOf(start.pos, end.pos) // TODO() later for arc
    }
}
