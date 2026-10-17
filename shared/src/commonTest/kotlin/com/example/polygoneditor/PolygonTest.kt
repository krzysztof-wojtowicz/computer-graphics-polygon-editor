package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.Polygon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PolygonTest {
    private fun square() = Polygon(listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)))

    @Test
    fun insertVertexOnEdgePutsItInTheMiddle() {
        val p = square()
        val v = p.insertVertexOnEdge(0)
        assertEquals(Vec2(5.0, 0.0), v.pos)
        assertEquals(5, p.vertices.size)
        assertEquals(v, p.vertices[1])
    }

    @Test
    fun cannotRemoveBelowThreeVertices() {
        val p = Polygon(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0)))
        assertFalse(p.removeVertex(p.vertices[0]))
        assertEquals(3, p.vertices.size)
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
