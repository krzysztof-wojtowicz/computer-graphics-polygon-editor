package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.arcPoints
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArcTest {
    private val a = Vec2(0.0, 0.0)
    private val b = Vec2(100.0, 0.0)
    private val s = 1 / sqrt(2.0)

    private fun unit(v: Vec2) = v / v.length

    // Every point lies on the circle (center, radius)
    private fun assertOnCircle(points: List<Vec2>, center: Vec2, radius: Double) {
        points.forEachIndexed { i, p ->
            assertTrue(abs(p.distanceTo(center) - radius) < 1e-6, "point $i $p is ${p.distanceTo(center)} from $center, expected $radius")
        }
    }

    // Direction of the segment p→q is within ~5° of the expected unit direction
    private fun assertDirection(expected: Vec2, p: Vec2, q: Vec2) {
        val cos = unit(q - p) dot expected
        assertTrue(cos > 0.996, "segment $p -> $q has direction ${unit(q - p)}, expected $expected")
    }

    private fun assertExactEnds(points: List<Vec2>) {
        assertEquals(a, points.first())
        assertEquals(b, points.last())
    }

    // ---- semicircle (G0 / G0) ----

    @Test
    fun semicircleHasHalfTheEdgeAsRadius() {
        val points = arcPoints(a, b)

        assertOnCircle(points, Vec2(50.0, 0.0), 50.0)
        assertExactEnds(points)
    }

    @Test
    fun semicircleBulgesToTheFixedSide() {
        // sweep = +π from a: for a → b going right, the arc goes through (50, -50) – up on the screen (y points down)
        val points = arcPoints(a, b)

        assertTrue(points.drop(1).dropLast(1).all { it.y < 0 }, "all inner points above the chord")
        assertTrue(points.any { it.distanceTo(Vec2(50.0, -50.0)) < 5 }, "passes near the top (50, -50)")
    }

    @Test
    fun zeroLengthTangentFallsBackToSemicircle() {
        assertEquals(arcPoints(a, b), arcPoints(a, b, startTangent = Vec2(0.0, 0.0)))
    }

    // ---- tangent arc (G1 at one end) ----

    @Test
    fun tangentArcLeavesTheStartAlongTheTangent() {
        // t at 45° up-right: r = (d·d) / (2 d·n) = 10000 / 141.4 = 70.7, center = a + n·r = (50, 50)
        val t = Vec2(s, -s)
        val points = arcPoints(a, b, startTangent = t)

        assertOnCircle(points, Vec2(50.0, 50.0), 100 * s)
        assertDirection(t, points[0], points[1])
        assertExactEnds(points)
    }

    @Test
    fun tangentNeedNotBeAUnitVector() {
        assertEquals(arcPoints(a, b, startTangent = Vec2(s, -s)), arcPoints(a, b, startTangent = Vec2(3.0, -3.0)))
    }

    @Test
    fun tangentPointingBackGivesTheLongWayRound() {
        // t up-left, away from b: the only tangent circle through b is reached the long way (sweep = 3π/2)
        val t = Vec2(-s, -s)
        val points = arcPoints(a, b, startTangent = t)

        assertOnCircle(points, Vec2(50.0, -50.0), 100 * s)
        assertDirection(t, points[0], points[1])
        assertTrue(points.any { it.x < -10 }, "goes left of a first")
        assertTrue(points.any { it.x > 110 }, "and right of b before coming back")
        assertExactEnds(points)
    }

    @Test
    fun tangentAtTheEndIsTheDirectionOfArrival() {
        // Arrives at b heading down-right, i.e. continues smoothly into a neighbour leaving b that way
        val t = Vec2(s, s)
        val points = arcPoints(a, b, endTangent = t)

        assertOnCircle(points, Vec2(50.0, 50.0), 100 * s)
        assertDirection(t, points[points.size - 2], points.last())
        assertExactEnds(points)
    }

    @Test
    fun tangentAlongTheChordGivesAStraightSegment() {
        assertEquals(listOf(a, b), arcPoints(a, b, startTangent = Vec2(1.0, 0.0)))
        assertEquals(listOf(a, b), arcPoints(a, b, startTangent = Vec2(-1.0, 0.0)))
    }

    // ---- edge cases ----

    @Test
    fun zeroLengthEdgeIsJustItsEnds() {
        assertEquals(listOf(a, a), arcPoints(a, a))
    }

    @Test
    fun bothTangentsAreRejected() {
        assertFailsWith<IllegalArgumentException> {
            arcPoints(a, b, startTangent = Vec2(1.0, 1.0), endTangent = Vec2(1.0, 1.0))
        }
    }

    @Test
    fun pointCountGrowsWithArcLengthAndStaysInRange() {
        val small = arcPoints(a, Vec2(2.0, 0.0)).size
        val medium = arcPoints(a, b).size
        val huge = arcPoints(a, Vec2(1e5, 0.0)).size

        assertEquals(3, small)   // at least 2 segments, so even a tiny arc bends
        assertTrue(medium in 4..200)
        assertEquals(201, huge)  // at most 200 segments
    }
}
