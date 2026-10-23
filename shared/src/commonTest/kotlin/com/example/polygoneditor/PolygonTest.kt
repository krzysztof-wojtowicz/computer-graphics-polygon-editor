package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.arcPoints
import com.example.polygoneditor.geometry.bezierPoints
import com.example.polygoneditor.geometry.bezierSegments
import com.example.polygoneditor.model.Continuity
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Polygon
import com.example.polygoneditor.model.Vertex
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PolygonTest {
    private fun square() = Polygon(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)))

    // Every edge ends where the next one starts, the last one returns to the first
    private fun assertClosedRing(p: Polygon) {
        val n = p.edges.size
        for (i in 0 until n) {
            assertSame(p.edges[i].end, p.edges[(i + 1) % n].start, "ring broken at edge $i")
        }
    }

    @Test
    fun constructionBuildsClosedRingOfEdges() {
        val p = square()
        assertEquals(4, p.edges.size)
        assertClosedRing(p)
        assertEquals(
            listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)),
            p.vertices.map { it.pos },
        )
    }

    @Test
    fun insertVertexOnEdgePutsItInTheMiddle() {
        val p = square()
        val v = p.insertVertexOnEdge(p.edges[0])
        assertEquals(Vec2(5.0, 0.0), v.pos)
        assertEquals(5, p.vertices.size)
        assertEquals(v, p.vertices[1])
    }

    @Test
    fun insertVertexReplacesEdgeWithTwoNewOnes() {
        val p = square()
        val e = p.edges[1]
        val v = p.insertVertexOnEdge(e)

        assertEquals(5, p.edges.size)
        assertFalse(e in p.edges, "old edge must be gone")
        val (incoming, outgoing) = p.edgesAt(v)
        assertSame(e.start, incoming.start)
        assertSame(e.end, outgoing.end)
        assertEquals(EdgeShape.Line, incoming.shape)
        assertEquals(EdgeShape.Line, outgoing.shape)
        assertClosedRing(p)
    }

    @Test
    fun edgesAtReturnsIncomingThenOutgoing() {
        val p = square()
        assertEquals(p.edges[1] to p.edges[2], p.edgesAt(p.vertices[2]))
        // v0: incoming edge is the last one in the list – wrap-around
        assertEquals(p.edges[3] to p.edges[0], p.edgesAt(p.vertices[0]))
    }

    @Test
    fun removeVertexJoinsItsNeighboursWithOneNewEdge() {
        val p = square()
        val v = p.vertices[2]
        val (incoming, outgoing) = p.edgesAt(v)

        assertTrue(p.removeVertex(v))

        assertEquals(3, p.edges.size)
        assertFalse(v in p.vertices)
        assertFalse(incoming in p.edges)
        assertFalse(outgoing in p.edges)
        assertTrue(p.edges.any { it.start === incoming.start && it.end === outgoing.end })
        assertClosedRing(p)
    }

    @Test
    fun removeFirstVertexWrapsAroundTheRing() {
        val p = square()
        val (v0, v1, _, v3) = p.vertices

        assertTrue(p.removeVertex(v0))

        assertEquals(3, p.edges.size)
        assertFalse(v0 in p.vertices)
        assertTrue(p.edges.any { it.start === v3 && it.end === v1 })
        assertClosedRing(p)
    }

    @Test
    fun cannotRemoveBelowThreeVertices() {
        val p = Polygon(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0)))
        assertFalse(p.removeVertex(p.vertices[0]))
        assertEquals(3, p.vertices.size)
    }

    @Test
    fun foreignVertexOrEdgeIsRejected() {
        val p = square()
        val other = square()
        assertFailsWith<IllegalArgumentException> { p.edgesAt(Vertex(Vec2(0.0, 0.0))) }
        assertFailsWith<IllegalArgumentException> { p.removeVertex(other.vertices[0]) }
        assertFailsWith<IllegalArgumentException> { p.insertVertexOnEdge(other.edges[0]) }
    }

    @Test
    fun translateMovesVerticesAndControlPointsExactlyOnce() {
        val p = square()
        val c1 = ControlPoint(Vec2(3.0, -5.0))
        val c2 = ControlPoint(Vec2(7.0, -5.0))
        p.edges[0].shape = EdgeShape.Bezier(c1, c2)
        assertEquals(6, p.points.size)

        p.translate(Vec2(1.0, 2.0))

        assertEquals(Vec2(1.0, 2.0), p.vertices[0].pos)
        assertEquals(Vec2(11.0, 12.0), p.vertices[2].pos)
        assertEquals(Vec2(4.0, -3.0), c1.pos)
        assertEquals(Vec2(8.0, -3.0), c2.pos)
    }

    @Test
    fun containsUsesEvenOddRuleForSelfIntersection() {
        // "bow tie": the center (5, 5) is where the edges cross, points in the wings are inside
        val bow = Polygon(listOf(Vec2(0.0, 0.0), Vec2(10.0, 10.0), Vec2(10.0, 0.0), Vec2(0.0, 10.0)))
        assertTrue(Vec2(9.0, 5.0) in bow)
        assertTrue(Vec2(1.0, 5.0) in bow)
        assertFalse(Vec2(5.0, 1.0) in bow)
    }

    // ---- shape of an edge ----

    @Test
    fun polylineOfLineIsItsEnds() {
        val p = square()
        val e0 = p.edges[0]

        assertEquals(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), p.polylineOf(e0))
    }

    @Test
    fun polylineOfBezierIsTheIncrementalCurve() {
        val p = square()
        val e0 = p.edges[0]
        val c1 = Vec2(3.0, -5.0)
        val c2 = Vec2(7.0, -5.0)
        e0.shape = EdgeShape.Bezier(ControlPoint(c1), ControlPoint(c2))

        val a = Vec2(0.0, 0.0)
        val b = Vec2(10.0, 0.0)
        assertEquals(bezierPoints(a, c1, c2, b, bezierSegments(a, c1, c2, b)), p.polylineOf(e0))
    }

    @Test
    fun polylineOfArcWithG0EndsIsASemicircle() {
        val p = square()
        val e0 = p.edges[0]
        e0.shape = EdgeShape.Arc

        assertEquals(arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), p.polylineOf(e0))
    }

    @Test
    fun outlineFollowsTheArc() {
        val p = square()
        p.edges[0].shape = EdgeShape.Arc // bulges up, outside the square: through (5, -5)

        assertTrue(Vec2(5.0, -3.0) in p)  // between the chord and the arc
        assertFalse(Vec2(5.0, -6.0) in p) // beyond the arc
    }

    // ---- arc tangents (G1 at one end) ----

    // Square with e0 as an arc: v0 (0,0) → v1 (10,0)
    private fun squareWithArc() = square().apply { edges[0].shape = EdgeShape.Arc }

    @Test
    fun arcWithG1AtStartFollowsTheIncomingLine() {
        val p = squareWithArc()
        p.vertices[3].pos = Vec2(-10.0, 10.0) // e3 now comes into v0 heading up-right
        p.vertices[0].continuity = Continuity.G1

        // The tangent direction itself is checked in ArcTest – here only that the right tangent is passed
        assertEquals(
            arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0), startTangent = Vec2(10.0, -10.0)),
            p.polylineOf(p.edges[0]),
        )
    }

    @Test
    fun arcWithG1AtStartFollowsTheBezierControlPoint() {
        val p = squareWithArc()
        // e3: (0,10) → (0,0), c2 at (-5, 5): the curve arrives at v0 heading a − c2 = (5, -5)
        p.edges[3].shape = EdgeShape.Bezier(ControlPoint(Vec2(0.0, 7.0)), ControlPoint(Vec2(-5.0, 5.0)))
        p.vertices[0].continuity = Continuity.G1

        assertEquals(
            arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0), startTangent = Vec2(5.0, -5.0)),
            p.polylineOf(p.edges[0]),
        )
    }

    @Test
    fun arcWithG1AtEndArrivesAlongTheOutgoingEdge() {
        val p = squareWithArc()
        p.vertices[2].pos = Vec2(20.0, 10.0) // e1 now leaves v1 heading down-right
        p.vertices[1].continuity = Continuity.G1

        assertEquals(
            arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0), endTangent = Vec2(10.0, 10.0)),
            p.polylineOf(p.edges[0]),
        )
    }

    @Test
    fun arcNextToAnotherArcStaysASemicircle() {
        val p = squareWithArc()
        p.edges[3].shape = EdgeShape.Arc
        p.vertices[0].continuity = Continuity.G1 // not allowed there, but even if set the arc has no tangent to follow

        assertEquals(arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0)), p.polylineOf(p.edges[0]))
    }

    @Test
    fun arcWithG1AtBothEndsUsesTheStart() {
        val p = squareWithArc()
        p.vertices[3].pos = Vec2(-10.0, 10.0)
        p.vertices[0].continuity = Continuity.G1
        p.vertices[1].continuity = Continuity.G1

        assertEquals(
            arcPoints(Vec2(0.0, 0.0), Vec2(10.0, 0.0), startTangent = Vec2(10.0, -10.0)),
            p.polylineOf(p.edges[0]),
        )
    }

    // ---- allowed continuity classes ----

    private val all = listOf(Continuity.G0, Continuity.G1, Continuity.C1)
    private val g0g1 = listOf(Continuity.G0, Continuity.G1)
    private val g0 = listOf(Continuity.G0)

    private fun bezier() = EdgeShape.Bezier(ControlPoint(Vec2(0.0, 0.0)), ControlPoint(Vec2(0.0, 0.0)))

    @Test
    fun betweenTwoLinesOnlyG0() {
        val p = square()
        assertEquals(g0, p.allowedContinuities(p.vertices[0]))
    }

    @Test
    fun nextToABezierEverything() {
        val p = square()
        p.edges[0].shape = bezier()
        assertEquals(all, p.allowedContinuities(p.vertices[0])) // bezier + line
        p.edges[3].shape = bezier()
        assertEquals(all, p.allowedContinuities(p.vertices[0])) // bezier + bezier
    }

    @Test
    fun nextToAnArcNoC1() {
        val p = squareWithArc()
        assertEquals(g0g1, p.allowedContinuities(p.vertices[0])) // arc + line
        p.edges[3].shape = bezier()
        assertEquals(g0g1, p.allowedContinuities(p.vertices[0])) // arc + bezier
    }

    @Test
    fun betweenTwoArcsOnlyG0() {
        val p = squareWithArc()
        p.edges[3].shape = EdgeShape.Arc
        assertEquals(g0, p.allowedContinuities(p.vertices[0]))
    }

    // ---- normalization ----

    @Test
    fun normalizeLowersToTheBestAllowedClass() {
        val p = squareWithArc()
        p.vertices[0].continuity = Continuity.C1 // arc + line: C1 -> G1
        p.vertices[2].continuity = Continuity.G1 // two lines: G1 -> G0

        p.normalizeContinuity()

        assertEquals(Continuity.G1, p.vertices[0].continuity)
        assertEquals(Continuity.G0, p.vertices[2].continuity)
    }

    @Test
    fun normalizeKeepsG1OnlyAtTheStartOfAnArc() {
        val p = squareWithArc()
        p.vertices[0].continuity = Continuity.G1
        p.vertices[1].continuity = Continuity.G1

        p.normalizeContinuity()

        assertEquals(Continuity.G1, p.vertices[0].continuity)
        assertEquals(Continuity.G0, p.vertices[1].continuity)
    }
}
