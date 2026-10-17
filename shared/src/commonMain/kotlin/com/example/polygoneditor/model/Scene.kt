package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2

class Scene {
    val polygons = mutableListOf<Polygon>()

    // Builder for polygon objects
    fun polygon(vararg points: Pair<Number, Number>): Polygon =
        Polygon(points.map { (x, y) -> Vec2(x.toDouble(), y.toDouble()) })
            .also { polygons += it }
}
