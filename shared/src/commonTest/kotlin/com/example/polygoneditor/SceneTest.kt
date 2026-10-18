package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Scene
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SceneTest {
    // Square with edges e0: v0→v1, e1: v1→v2, e2: v2→v3, e3: v3→v0
    private fun sceneWithSquare() = Scene().apply { polygon(0 to 0, 10 to 0, 10 to 10, 0 to 10) }

    @Test
    fun splittingEdgeDropsItsConstraints() {
        val scene = sceneWithSquare()
        val poly = scene.polygons[0]
        val e0 = poly.edges[0]
        scene.addConstraint(FixedLength(e0, 10.0))

        scene.insertVertex(poly, e0)

        assertTrue(scene.constraints.isEmpty())
    }

    @Test
    fun removingVertexDropsConstraintsOfBothItsEdges() {
        val scene = sceneWithSquare()
        val poly = scene.polygons[0]
        val (e0, e1) = poly.edges
        scene.addConstraint(FixedLength(e0, 10.0))
        scene.addConstraint(FixedLength(e1, 10.0))

        assertTrue(scene.removeVertex(poly, poly.vertices[1])) // v1 joins e0 and e1

        assertTrue(scene.constraints.isEmpty())
    }

    @Test
    fun perpendicularGoesAwayWhenOnlyOneOfItsEdgesDisappears() {
        val scene = sceneWithSquare()
        val poly = scene.polygons[0]
        val (e0, _, e2) = poly.edges
        scene.addConstraint(Perpendicular(e0, e2))

        scene.insertVertex(poly, e0)

        assertTrue(scene.constraints.isEmpty())
    }

    @Test
    fun constraintsOnOtherEdgesSurviveStructuralChanges() {
        val scene = sceneWithSquare()
        val poly = scene.polygons[0]
        val (e0, _, e2, e3) = poly.edges
        val length = FixedLength(e2, 10.0)
        val perpendicular = Perpendicular(e2, e3)
        scene.addConstraint(length)
        scene.addConstraint(perpendicular)

        val v = scene.insertVertex(poly, e0)  // touches only e0
        scene.removeVertex(poly, v)           // touches only the two halves of e0

        assertEquals(listOf(length, perpendicular), scene.constraints)
    }

    @Test
    fun constraintBetweenPolygonsIsDroppedWhenEitherEdgeDisappears() {
        val scene = sceneWithSquare()
        val other = scene.polygon(20 to 0, 30 to 0, 30 to 10, 20 to 10)
        val a = scene.polygons[0]
        scene.addConstraint(Perpendicular(a.edges[0], other.edges[1]))

        scene.removeVertex(other, other.vertices[2]) // v2 of the second square ends its e1

        assertTrue(scene.constraints.isEmpty())
    }

    @Test
    fun failedRemoveKeepsConstraints() {
        val scene = Scene().apply { polygon(0 to 0, 10 to 0, 0 to 10) }
        val tri = scene.polygons[0]
        val c = FixedLength(tri.edges[0], 10.0)
        scene.addConstraint(c)

        assertFalse(scene.removeVertex(tri, tri.vertices[1]))

        assertEquals(listOf(c), scene.constraints)
        assertEquals(3, tri.edges.size)
    }

    @Test
    fun constraintsOfReturnsOnlyConstraintsOfThatEdge() {
        val scene = sceneWithSquare()
        val (e0, e1, e2) = scene.polygons[0].edges
        val length = FixedLength(e0, 10.0)
        val perpendicular = Perpendicular(e0, e1)
        scene.addConstraint(length)
        scene.addConstraint(perpendicular)

        assertEquals(listOf(length, perpendicular), scene.constraintsOf(e0))
        assertEquals(listOf(perpendicular), scene.constraintsOf(e1))
        assertTrue(scene.constraintsOf(e2).isEmpty())
    }

    @Test
    fun addConstraintRejectsEdgesOutsideTheScene() {
        val scene = sceneWithSquare()
        val foreign = sceneWithSquare().polygons[0].edges[0]
        assertFailsWith<IllegalArgumentException> { scene.addConstraint(FixedLength(foreign, 10.0)) }

        // stale edge: it was replaced by two halves, e.g. an outdated hover/selection
        val poly = scene.polygons[0]
        val stale = poly.edges[0]
        scene.insertVertex(poly, stale)
        assertFailsWith<IllegalArgumentException> { scene.addConstraint(FixedLength(stale, 10.0)) }

        assertTrue(scene.constraints.isEmpty())
    }

    @Test
    fun restoreBringsBackAllPointsIncludingControlPoints() {
        val scene = sceneWithSquare()
        val poly = scene.polygons[0]
        val c1 = ControlPoint(Vec2(3.0, -5.0))
        val c2 = ControlPoint(Vec2(7.0, -5.0))
        poly.edges[0].shape = EdgeShape.Bezier(c1, c2)

        val snapshot = scene.snapshot()
        assertEquals(6, snapshot.size)

        poly.translate(Vec2(5.0, 5.0))
        poly.vertices[2].pos = Vec2(100.0, 100.0)
        scene.restore(snapshot)

        assertEquals(
            listOf(Vec2(0.0, 0.0), Vec2(10.0, 0.0), Vec2(10.0, 10.0), Vec2(0.0, 10.0)),
            poly.vertices.map { it.pos },
        )
        assertEquals(Vec2(3.0, -5.0), c1.pos)
        assertEquals(Vec2(7.0, -5.0), c2.pos)
    }
}
