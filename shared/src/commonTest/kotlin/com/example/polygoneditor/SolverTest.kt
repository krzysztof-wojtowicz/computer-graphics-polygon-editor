package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.Polygon
import com.example.polygoneditor.model.Vertex
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.solver.LockLevel
import com.example.polygoneditor.solver.Locks
import com.example.polygoneditor.solver.Solver
import com.example.polygoneditor.solver.error
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SolverTest {
    private fun v(x: Number, y: Number) = Vertex(Vec2(x.toDouble(), y.toDouble()))

    private fun assertClose(expected: Vec2, actual: Vec2, tolerance: Double = 1e-9) {
        assertTrue(
            abs(expected.x - actual.x) <= tolerance && abs(expected.y - actual.y) <= tolerance,
            "expected $expected, got $actual",
        )
    }

    private fun assertLength(expected: Double, edge: Edge, tolerance: Double = Solver.EPS) {
        assertTrue(abs(edge.length - expected) < tolerance, "expected length $expected, got ${edge.length}")
    }

    // ---- Locks ----

    @Test
    fun levelOfFollowsTheSets() {
        val a = v(0, 0)
        val b = v(1, 0)
        val c = v(2, 0)
        val locks = Locks(locked = setOf(a), protected = setOf(b))

        assertEquals(LockLevel.Locked, locks.levelOf(a))
        assertEquals(LockLevel.Protected, locks.levelOf(b))
        assertEquals(LockLevel.Free, locks.levelOf(c))
        assertEquals(LockLevel.Free, locks.levelOf(v(0, 0)), "same position, different vertex")
    }

    @Test
    fun sharesGiveTheWholeMoveToTheLessLockedEnd() {
        val locked = v(0, 0)
        val protected = v(1, 0)
        val free = v(2, 0)
        val free2 = v(3, 0)
        val locked2 = v(4, 0)
        val locks = Locks(locked = setOf(locked, locked2), protected = setOf(protected))

        assertEquals(0.5 to 0.5, locks.shares(free, free2))
        assertEquals(0.0 to 1.0, locks.shares(locked, free))
        assertEquals(1.0 to 0.0, locks.shares(free, locked))
        assertEquals(1.0 to 0.0, locks.shares(free, protected))
        assertEquals(0.0 to 1.0, locks.shares(locked, protected))
        assertEquals(0.0 to 0.0, locks.shares(locked, locked2))
    }

    // ---- solve: basics ----

    @Test
    fun noConstraintsIsSolvedImmediately() {
        assertTrue(Solver.solve(emptyList(), Locks.None))
    }

    @Test
    fun satisfiedConstraintIsNotTouched() {
        val edge = Edge(v(0, 0), v(100, 0))

        assertTrue(Solver.solve(listOf(FixedLength(edge, 100.0)), Locks.None))

        assertEquals(Vec2(0.0, 0.0), edge.start.pos)
        assertEquals(Vec2(100.0, 0.0), edge.end.pos)
    }

    // ---- FixedLength ----

    @Test
    fun tooLongEdgeWithFreeEndsShrinksSymmetrically() {
        val edge = Edge(v(0, 0), v(120, 0))

        assertTrue(Solver.solve(listOf(FixedLength(edge, 100.0)), Locks.None))

        assertClose(Vec2(10.0, 0.0), edge.start.pos)
        assertClose(Vec2(110.0, 0.0), edge.end.pos)
    }

    @Test
    fun tooShortEdgeWithFreeEndsGrowsSymmetrically() {
        val edge = Edge(v(0, 0), v(80, 0))

        assertTrue(Solver.solve(listOf(FixedLength(edge, 100.0)), Locks.None))

        assertClose(Vec2(-10.0, 0.0), edge.start.pos)
        assertClose(Vec2(90.0, 0.0), edge.end.pos)
    }

    @Test
    fun lockedEndStaysAndFreeEndMovesAlongTheEdge() {
        val a = v(0, 0)
        val edge = Edge(a, v(60, 80)) // length 100, direction (0.6, 0.8)

        assertTrue(Solver.solve(listOf(FixedLength(edge, 50.0)), Locks(locked = setOf(a))))

        assertEquals(Vec2(0.0, 0.0), a.pos)
        assertClose(Vec2(30.0, 40.0), edge.end.pos)
    }

    @Test
    fun chainFollowsTheDraggedEnd() {
        // A – B – C in a line, both edges 100 long; A is dragged 20 to the left
        val a = v(-20, 0)
        val b = v(100, 0)
        val c = v(200, 0)
        val ab = Edge(a, b)
        val bc = Edge(b, c)

        val solved = Solver.solve(listOf(FixedLength(ab, 100.0), FixedLength(bc, 100.0)), Locks(locked = setOf(a)))

        assertTrue(solved)
        assertEquals(Vec2(-20.0, 0.0), a.pos)
        assertLength(100.0, ab)
        assertLength(100.0, bc)
    }

    @Test
    fun squareKeepsAllLengthsWhenOneVertexIsMoved() {
        val square = Polygon(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0)))
        val constraints = square.edges.map { FixedLength(it, 100.0) }
        val v0 = square.vertices[0]
        v0.pos = Vec2(20.0, -15.0) // the user drags v0

        assertTrue(Solver.solve(constraints, Locks(locked = setOf(v0))))

        assertEquals(Vec2(20.0, -15.0), v0.pos)
        square.edges.forEach { assertLength(100.0, it) }
    }

    // ---- failures ----

    @Test
    fun impossibleTriangleFails() {
        // 1 + 1 < 5: no triangle has such sides, the solver can only oscillate
        val tri = Polygon(listOf(Vec2(0.0, 0.0), Vec2(1.0, 0.0), Vec2(0.0, 1.0)))
        val (e0, e1, e2) = tri.edges
        val constraints = listOf(FixedLength(e0, 1.0), FixedLength(e1, 1.0), FixedLength(e2, 5.0))

        assertFalse(Solver.solve(constraints, Locks.None))
        assertTrue(constraints.any { it.error() >= Solver.EPS })
    }

    @Test
    fun bothEndsLockedWithWrongLengthFailsAndMovesNothing() {
        val a = v(0, 0)
        val b = v(120, 0)

        assertFalse(Solver.solve(listOf(FixedLength(Edge(a, b), 100.0)), Locks(locked = setOf(a, b))))

        assertEquals(Vec2(0.0, 0.0), a.pos)
        assertEquals(Vec2(120.0, 0.0), b.pos)
    }

    @Test
    fun zeroLengthEdgeDoesNotProduceNaN() {
        val a = v(5, 5)
        val b = v(5, 5)

        assertFalse(Solver.solve(listOf(FixedLength(Edge(a, b), 10.0)), Locks.None))

        assertEquals(Vec2(5.0, 5.0), a.pos)
        assertEquals(Vec2(5.0, 5.0), b.pos)
    }

    // ---- Perpendicular ----

    private fun assertPerpendicular(e1: Edge, e2: Edge) {
        val error = Perpendicular(e1, e2).error()
        assertTrue(error < Solver.EPS, "edges are not perpendicular, error $error")
    }

    // Edge of length 100 starting at (x, y), going at `degrees` from the x axis
    private fun edgeAt(x: Number, y: Number, degrees: Double): Edge {
        val rad = degrees * kotlin.math.PI / 180
        val start = v(x, y)
        return Edge(start, Vertex(start.pos + Vec2(cos(rad), sin(rad)) * 100.0))
    }

    @Test
    fun perpendicularErrorIsTheOverhangInDp() {
        val e1 = Edge(v(0, 0), v(100, 0))
        val e2 = Edge(v(100, 0), v(160, 80)) // example from the explanation

        assertEquals(60.0, Perpendicular(e1, e2).error(), 1e-9)

        e2.end.pos = Vec2(100.0, 100.0)
        assertEquals(0.0, Perpendicular(e1, e2).error(), 1e-9)
    }

    @Test
    fun rotatesToTheCloserPerpendicularDirection() {
        val horizontal = Edge(v(0, 0), v(100, 0))

        for ((degrees, expectedSign) in listOf(80.0 to 1.0, 100.0 to 1.0, -80.0 to -1.0, -100.0 to -1.0, 260.0 to -1.0)) {
            val e2 = edgeAt(200, 0, degrees)

            assertTrue(Solver.solve(listOf(Perpendicular(horizontal, e2)), Locks.None))

            assertPerpendicular(horizontal, e2)
            assertTrue(e2.vector.y * expectedSign > 0, "$degrees° should end at ${expectedSign * 90}°, got ${e2.vector}")
        }
    }

    @Test
    fun rotationKeepsTheLengthAndOnlyTheSecondEdgeMovesOnATie() {
        val e1 = Edge(v(0, 0), v(100, 0))
        val e2 = edgeAt(200, 0, 70.0)

        assertTrue(Solver.solve(listOf(Perpendicular(e1, e2)), Locks.None))

        assertLength(100.0, e2)
        assertEquals(Vec2(0.0, 0.0), e1.start.pos)
        assertEquals(Vec2(100.0, 0.0), e1.end.pos)
    }

    @Test
    fun equallyLockedEndsRotateAroundTheMidpoint() {
        val e1 = Edge(v(0, 0), v(100, 0))
        val e2 = Edge(v(100, 0), v(160, 80)) // separate vertices from e1, midpoint (130, 40)

        assertTrue(Solver.solve(listOf(Perpendicular(e1, e2)), Locks.None))

        assertClose(Vec2(130.0, -10.0), e2.start.pos)
        assertClose(Vec2(130.0, 90.0), e2.end.pos)
    }

    @Test
    fun lockedEndIsThePivot() {
        val e1 = Edge(v(0, 0), v(100, 0))
        val e2 = edgeAt(200, 0, 60.0)
        val pivot = e2.start
        // e1 fully locked, so e2 is the less locked edge and rotates – around its locked start
        val locks = Locks(locked = setOf(e1.start, e1.end, pivot))

        assertTrue(Solver.solve(listOf(Perpendicular(e1, e2)), locks))

        assertEquals(Vec2(200.0, 0.0), pivot.pos)
        assertClose(Vec2(200.0, 100.0), e2.end.pos, 1e-6)
    }

    @Test
    fun draggedEdgeStaysAndTheOtherOneRotatesWhicheverOrder() {
        for (draggedFirst in listOf(true, false)) {
            val dragged = Edge(v(0, 0), v(100, 30))
            val other = edgeAt(200, 0, 45.0)
            val constraint = if (draggedFirst) Perpendicular(dragged, other) else Perpendicular(other, dragged)

            assertTrue(Solver.solve(listOf(constraint), Locks(locked = setOf(dragged.start, dragged.end))))

            assertEquals(Vec2(0.0, 0.0), dragged.start.pos)
            assertEquals(Vec2(100.0, 30.0), dragged.end.pos)
            assertPerpendicular(dragged, other)
        }
    }

    @Test
    fun adjacentEdgesRotateAroundTheLockedSharedVertex() {
        val a = v(0, 0)
        val shared = v(100, 0)
        val b = v(160, 80)
        val e1 = Edge(a, shared)
        val e2 = Edge(shared, b)

        assertTrue(Solver.solve(listOf(Perpendicular(e1, e2)), Locks(locked = setOf(shared))))

        assertEquals(Vec2(0.0, 0.0), a.pos)
        assertEquals(Vec2(100.0, 0.0), shared.pos)
        assertClose(Vec2(100.0, 100.0), b.pos)
    }

    @Test
    fun edgesOfDifferentPolygonsBecomePerpendicular() {
        val p1 = Polygon(listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(50.0, 80.0)))
        val p2 = Polygon(listOf(Vec2(200.0, 0.0), Vec2(300.0, 40.0), Vec2(250.0, 120.0)))

        assertTrue(Solver.solve(listOf(Perpendicular(p1.edges[0], p2.edges[0])), Locks.None))

        assertPerpendicular(p1.edges[0], p2.edges[0])
    }

    @Test
    fun perpendicularsAndLengthsMakeARectangle() {
        // A rough quadrilateral -> right angles at three corners + two side lengths = 100 x 50 rectangle
        val quad = Polygon(listOf(Vec2(0.0, 0.0), Vec2(100.0, 10.0), Vec2(110.0, 60.0), Vec2(-5.0, 45.0)))
        val (e0, e1, e2, e3) = quad.edges
        val constraints = listOf(
            Perpendicular(e0, e1), Perpendicular(e1, e2), Perpendicular(e2, e3),
            FixedLength(e0, 100.0), FixedLength(e1, 50.0),
        )

        assertTrue(Solver.solve(constraints, Locks.None))

        assertPerpendicular(e0, e1)
        assertPerpendicular(e1, e2)
        assertPerpendicular(e2, e3)
        assertLength(100.0, e0)
        assertLength(50.0, e1)
        // Not constraints themselves, they follow from the others – the solver guarantees EPS only
        // for the given constraints, derived values accumulate a slightly bigger error
        val derived = 0.01
        assertTrue(Perpendicular(e3, e0).error() < derived)
        assertLength(100.0, e2, derived) // opposite sides of a rectangle
        assertLength(50.0, e3, derived)
    }

    @Test
    fun bothEdgesFullyLockedAndNotPerpendicularFails() {
        val e1 = Edge(v(0, 0), v(100, 0))
        val e2 = edgeAt(200, 0, 45.0)
        val locks = Locks(locked = setOf(e1.start, e1.end, e2.start, e2.end))

        assertFalse(Solver.solve(listOf(Perpendicular(e1, e2)), locks))
    }

    @Test
    fun zeroLengthEdgesDoNotProduceNaN() {
        val point = Edge(v(5, 5), v(5, 5))
        val e2 = edgeAt(200, 0, 45.0)
        val endBefore = e2.end.pos

        assertEquals(0.0, Perpendicular(point, e2).error())
        assertEquals(0.0, Perpendicular(point, Edge(v(1, 1), v(1, 1))).error())
        assertTrue(Solver.solve(listOf(Perpendicular(point, e2)), Locks.None))
        assertEquals(endBefore, e2.end.pos)
    }
}
