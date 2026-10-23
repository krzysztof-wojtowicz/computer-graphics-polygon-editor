package com.example.polygoneditor

import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.Continuity
import com.example.polygoneditor.model.DemoScene
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.solver.Solver
import com.example.polygoneditor.solver.error
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DemoSceneTest {
    private val scene = DemoScene.create()

    @Test
    fun startsWithAllConstraintsSatisfied() {
        val constraints = scene.allConstraints()
        assertTrue(constraints.isNotEmpty())
        assertTrue(constraints.all { it.error() < Solver.EPS })
    }

    @Test
    fun showsTheRequiredElements() {
        val shapes = scene.polygons.flatMap { p -> p.edges.map { it.shape } }

        assertEquals(3, scene.polygons.size)
        assertTrue(scene.constraints.any { it is FixedLength })
        assertTrue(scene.constraints.any { it is Perpendicular })
        assertTrue(shapes.any { it is EdgeShape.Bezier })
        assertTrue(shapes.any { it is EdgeShape.Arc })
    }

    @Test
    fun hasSeveralConstraintsOnOneEdge() {
        val edges = scene.polygons.flatMap { it.edges }
        assertTrue(edges.any { scene.constraintsOf(it).size >= 3 })
    }

    @Test
    fun middlePolygonIsTheKotlinLogo() {
        val logo = scene.polygons[1]
        val (_, notchUpper, notchLower) = logo.edges

        assertEquals(5, logo.vertices.size)
        assertTrue(logo.edges.all { it.shape is EdgeShape.Line })
        assertTrue(scene.constraints.filterIsInstance<Perpendicular>().any { it.e1 === notchUpper && it.e2 === notchLower })
    }

    @Test
    fun hasPerpendicularBetweenPolygons() {
        val between = scene.constraints.filterIsInstance<Perpendicular>().any { c ->
            scene.polygons.none { p -> c.e1 in p.edges && c.e2 in p.edges }
        }
        assertTrue(between)
    }

    @Test
    fun showsDefaultAndExplicitContinuity() {
        val vertices = scene.polygons.flatMap { it.vertices }

        assertTrue(vertices.any { it.continuity == Continuity.C1 && !it.continuityExplicit })
        assertTrue(vertices.any { it.continuity == Continuity.G1 && it.continuityExplicit })
        // G1 at an arc: the arc follows its neighbour, no solver constraint
        assertTrue(scene.polygons.any { p ->
            p.edges.any { e -> e.shape is EdgeShape.Arc && e.start.continuity == Continuity.G1 }
        })
    }

    @Test
    fun hasSelfIntersectingPolygon() {
        val selfIntersecting = scene.polygons.any { p ->
            val segments = p.edges.map { it.start.pos to it.end.pos }
            segments.indices.any { i ->
                // non-neighbouring edges only
                (i + 2 until segments.size).any { j ->
                    (i != 0 || j != segments.lastIndex) && crosses(segments[i], segments[j])
                }
            }
        }
        assertTrue(selfIntersecting)
    }

    @Test
    fun draggingInTheDemoKeepsConstraints() {
        val editor = Editor(scene)
        val bowtie = scene.polygons[2]
        val v = bowtie.vertices[1] // end of the C1 Bézier and of the edge with a length
        val before = bowtie.vertices.associateWith { it.pos }
        val delta = Vec2(15.0, 10.0)

        editor.onPrimaryPress(v.pos)
        editor.onMove(v.pos + delta)
        editor.onRelease()

        assertTrue(scene.allConstraints().all { it.error() < Solver.EPS })
        assertNull(editor.message)
        // solved, not the fallback – that would move every vertex by delta
        assertTrue(bowtie.vertices.any { (it.pos - (before.getValue(it) + delta)).length > 1e-6 })
    }

    private fun crosses(s: Pair<Vec2, Vec2>, t: Pair<Vec2, Vec2>): Boolean {
        val (a, b) = s
        val (c, d) = t
        val d1 = (b - a) cross (c - a)
        val d2 = (b - a) cross (d - a)
        val d3 = (d - c) cross (a - c)
        val d4 = (d - c) cross (b - c)
        return d1 * d2 < 0 && d3 * d4 < 0
    }
}
