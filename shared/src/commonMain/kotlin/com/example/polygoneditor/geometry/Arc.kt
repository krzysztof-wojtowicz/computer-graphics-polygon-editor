package com.example.polygoneditor.geometry

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Returns list of points that represents segments of an arc from point [a] to [b],
 * the tangent of one of the points can be specified, then the arc will have **G1** continuity there
 * */
fun arcPoints(a: Vec2, b: Vec2, startTangent: Vec2? = null, endTangent: Vec2? = null): List<Vec2> {
    require(startTangent == null || endTangent == null) { "At least one tangent has to be null" }

    // If endTangent specified, then it's the same as arc from b to a with startTangent = -endTangent
    if (endTangent != null) {
        return arcPoints(b, a, startTangent = endTangent * -1.0).reversed()
    }

    val d = b - a
    val len = d.length
    // zero-length edge
    if (len < 1e-9) return listOf(a, b)

    // No tangent specified (or a zero-length one)
    if (startTangent == null || startTangent.length < 1e-9) {
        val center = midpoint(a, b)
        val radius = len / 2
        val startAngle = angleOf(a - center)
        val sweep = PI
        return arcPolyline(center, radius, startAngle, sweep, a, b)
    }

    val t = startTangent / startTangent.length
    val n = t.perpendicular()
    val dn = d dot n
    if (abs(dn) < 1e-6 * len) return listOf(a, b)

    val r = (d dot d) / (2 * dn)
    val center = a + n * r
    val startAngle = angleOf(a - center)
    var sweep = angleOf(b - center) - startAngle
    if (r > 0 && sweep <= 0) sweep += 2 * PI
    if (r < 0 && sweep >= 0) sweep -= 2 * PI

    return arcPolyline(center, abs(r), startAngle, sweep, a, b)
}

/**
 * Returns angle of vector [v]
 */
private fun angleOf(v: Vec2) = atan2(v.y, v.x)

// Returns list of points that create arc polyline along the circle (center, radius) from startAngle
// by sweep radians
private fun arcPolyline(center: Vec2, radius: Double, startAngle: Double, sweep: Double, a: Vec2, b: Vec2): List<Vec2> {
    // how many segments polyline has
    val n = (abs(sweep) * radius / 4).toInt().coerceIn(2, 200)

    return buildList {
        add(a)
        for (i in 1 until n) {
            val angle = startAngle + sweep * i / n
            add(center + Vec2(cos(angle), sin(angle)) * radius)
        }
        add(b)
    }
}
