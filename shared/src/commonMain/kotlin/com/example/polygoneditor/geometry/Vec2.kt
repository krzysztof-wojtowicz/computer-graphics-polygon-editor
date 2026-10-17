package com.example.polygoneditor.geometry

import kotlin.math.hypot

// A 2D point class or a vector
data class Vec2(val x: Double, val y: Double) {
    // Overloaded operators
    operator fun plus(o: Vec2) = Vec2(x + o.x, y + o.y)
    operator fun minus(o: Vec2) = Vec2(x - o.x, y - o.y)
    operator fun times(s: Double) = Vec2(x * s, y * s)
    operator fun div(s: Double) = Vec2(x / s, y / s)

    infix fun dot(o: Vec2) = x * o.x + y * o.y

    // Cross product
    infix fun cross(o: Vec2) = x * o.y - y * o.x

    val length: Double get() = hypot(x, y)

    fun distanceTo(o: Vec2) = (this - o).length
}

// Returns the midpoint between two points
fun midpoint(a: Vec2, b: Vec2) = (a + b) / 2.0

// Distance of point p from the ab segment
fun distanceToSegment(p: Vec2, a: Vec2, b: Vec2): Double {
    val ab = b - a
    val len2 = ab dot ab

    if (len2 == 0.0) return p.distanceTo(a)

    val t = (((p - a) dot ab) / len2).coerceIn(0.0, 1.0)
    return p.distanceTo(a + ab * t)
}

// Checks if point p is inside the polygon defined by points,
// uses the ray casting method
fun containsEvenOdd(points: List<Vec2>, p: Vec2): Boolean {
    var inside = false
    var j = points.lastIndex

    for (i in points.indices) {
        val a = points[i]
        val b = points[j]
        if ((a.y > p.y) != (b.y > p.y) &&
            p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x
        ) {
            inside = !inside
        }
        j = i
    }
    return inside
}
