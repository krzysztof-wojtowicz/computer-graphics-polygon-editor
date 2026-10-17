package com.example.polygoneditor.model

sealed interface EdgeShape {
    data object Line : EdgeShape

    // c1 near start, c2 near end
    class Bezier(val c1: ControlPoint, val c2: ControlPoint) : EdgeShape

    data object Arc : EdgeShape
}

class Edge(val start: Vertex, val end: Vertex, var shape: EdgeShape = EdgeShape.Line) {
    val controlPoints: List<ControlPoint>
        get() = when (val s = shape) {
            is EdgeShape.Bezier -> listOf<ControlPoint>(s.c1, s.c2)
            is EdgeShape.Line, EdgeShape.Arc -> emptyList<ControlPoint>()
        }
}
