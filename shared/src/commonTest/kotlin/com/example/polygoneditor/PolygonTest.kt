package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
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
}
