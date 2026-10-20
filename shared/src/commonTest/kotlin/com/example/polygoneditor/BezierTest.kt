package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.bezierPoints
import com.example.polygoneditor.geometry.bezierSegments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BezierTest {
    private val v0 = Vec2(0.0, 0.0)
    private val v1 = Vec2(30.0, 120.0)
    private val v2 = Vec2(170.0, -60.0)
    private val v3 = Vec2(200.0, 50.0)

    // Reference: the Bernstein form computed directly for a given t
    private fun bernstein(v0: Vec2, v1: Vec2, v2: Vec2, v3: Vec2, t: Double): Vec2 {
        val s = 1 - t
        return v0 * (s * s * s) + v1 * (3 * s * s * t) + v2 * (3 * s * t * t) + v3 * (t * t * t)
    }

    private fun assertClose(expected: Vec2, actual: Vec2, eps: Double = 1e-9, message: String = "") =
        assertTrue(expected.distanceTo(actual) < eps, "$message expected $expected, got $actual")

    @Test
    fun matchesBernsteinForm() {
        for (n in listOf(1, 2, 3, 10, 100, 1000)) {
            val points = bezierPoints(v0, v1, v2, v3, n)
            points.forEachIndexed { i, p ->
                assertClose(bernstein(v0, v1, v2, v3, i.toDouble() / n), p, message = "n = $n, i = $i:")
            }
        }
    }

    @Test
    fun hasNPlusOnePoints() {
        for (n in 1..20) assertEquals(n + 1, bezierPoints(v0, v1, v2, v3, n).size)
    }

    @Test
    fun endsExactlyAtTheVertices() {
        // Exactly – no drift allowed, the next edge starts at v3
        for (n in listOf(1, 7, 1000)) {
            val points = bezierPoints(v0, v1, v2, v3, n)
            assertEquals(v0, points.first())
            assertEquals(v3, points.last())
        }
    }

    @Test
    fun singleSegmentIsTheChord() {
        assertEquals(listOf(v0, v3), bezierPoints(v0, v1, v2, v3, 1))
    }

    @Test
    fun controlPointsAtThirdsGiveEvenlySpacedLine() {
        // This is how a line becomes a Bézier – right after the change it has to look the same
        val a = Vec2(10.0, 20.0)
        val b = Vec2(130.0, -40.0)
        val c1 = a + (b - a) / 3.0
        val c2 = a + (b - a) * (2.0 / 3.0)
        val n = 6
        bezierPoints(a, c1, c2, b, n).forEachIndexed { i, p ->
            assertClose(a + (b - a) * (i.toDouble() / n), p, message = "i = $i:")
        }
    }

    @Test
    fun zeroSegmentsIsRejected() {
        assertFailsWith<IllegalArgumentException> { bezierPoints(v0, v1, v2, v3, 0) }
    }

    @Test
    fun segmentCountGrowsWithTheControlPolygonAndStaysInRange() {
        val p = Vec2(10.0, 10.0)
        assertEquals(1, bezierSegments(p, p, p, p)) // zero length – still one segment

        // Control polygon of length 3 * 40 = 120 -> one segment per 4 units
        assertEquals(30, bezierSegments(Vec2(0.0, 0.0), Vec2(40.0, 0.0), Vec2(80.0, 0.0), Vec2(120.0, 0.0)))

        assertEquals(200, bezierSegments(Vec2(0.0, 0.0), Vec2(1e4, 0.0), Vec2(-1e4, 0.0), Vec2(1e4, 0.0)))
    }
}
