package com.example.polygoneditor

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.Polygon
import com.example.polygoneditor.model.Vertex
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.solver.LockLevel
import com.example.polygoneditor.solver.Locks
import com.example.polygoneditor.solver.Solver
import com.example.polygoneditor.solver.error
import kotlin.math.abs
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

    private fun assertLength(expected: Double, edge: Edge) {
        assertTrue(abs(edge.length - expected) < Solver.EPS, "expected length $expected, got ${edge.length}")
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
}
