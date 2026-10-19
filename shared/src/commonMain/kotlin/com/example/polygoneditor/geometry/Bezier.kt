package com.example.polygoneditor.geometry

// Points of cubic Bézier curve, computed incrementally
// returns list of n + 1 points
fun bezierPoints(v0: Vec2, v1: Vec2, v2: Vec2, v3: Vec2, n: Int): List<Vec2> {
    require(n >= 1) { "n has to be greater than 0" }

    val d: Double = 1.0 / n
    val d2: Double = d * d
    val d3: Double = d2 * d

    val a0 = v0
    val a1 = (v1 - v0) * 3.0
    val a2 = (v2 - v1 * 2.0 + v0) * 3.0
    val a3 = v3 - v2 * 3.0 + v1 * 3.0 - v0

    // starting values
    var p = a0
    var delta = a3 * d3 + a2 * d2 + a1 * d
    var delta2 = a3 * 6.0 * d3 + a2 * 2.0 * d2
    val delta3 = a3 * 6.0 * d3

    return buildList {
        add(p)
        repeat(n) {
            p += delta
            delta += delta2
            delta2 += delta3
            if (it == n - 1) {
                add(v3)
            } else {
                add(p)
            }
        }
    }
}

// Calculate how many segments based on control polygon
// one per 4 units of control polygon's length
fun bezierSegments(v0: Vec2, v1: Vec2, v2: Vec2, v3: Vec2): Int {
    return (((v1 - v0).length + (v2 - v1).length + (v3 - v2).length) / 4).coerceIn(1.0, 200.0).toInt()
}