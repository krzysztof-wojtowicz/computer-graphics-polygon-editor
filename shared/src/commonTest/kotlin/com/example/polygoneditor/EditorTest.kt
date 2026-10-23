package com.example.polygoneditor

import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.Hit
import com.example.polygoneditor.editor.Selection
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.arcPoints
import com.example.polygoneditor.model.Continuity
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Scene
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.solver.Solver
import com.example.polygoneditor.solver.error
import kotlin.test.Test
import kotlin.math.abs
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EditorTest {
    // Square with edges e0: (0,0)→(100,0), e1: →(100,100), e2: →(0,100), e3: →(0,0)
    // Edge midpoints: e0 (50,0), e1 (100,50), e2 (50,100), e3 (0,50)
    private fun editorWithSquare() = Editor(Scene().apply { polygon(0 to 0, 100 to 0, 100 to 100, 0 to 100) })

    private fun Editor.click(x: Number, y: Number, shift: Boolean = false) {
        onPrimaryPress(Vec2(x.toDouble(), y.toDouble()), shift)
        onRelease()
    }

    private fun Editor.drag(fromX: Number, fromY: Number, toX: Number, toY: Number) {
        onPrimaryPress(Vec2(fromX.toDouble(), fromY.toDouble()))
        onMove(Vec2(toX.toDouble(), toY.toDouble()))
        onRelease()
    }

    private fun Editor.vertexPositions() = scene.polygons[0].vertices.map { it.pos }

    private val squarePositions = listOf(Vec2(0.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0))

    // ---- plain click ----

    @Test
    fun clickOnVertexSelectsIt() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(100, 0)

        assertEquals(Selection.OnVertex(poly, poly.vertices[1]), editor.selection)
    }

    @Test
    fun vertexWinsOverEdgeNearTheCorner() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(3, 0) // on e0, but also within VERTEX_HIT_RADIUS of v0

        assertEquals(Selection.OnVertex(poly, poly.vertices[0]), editor.selection)
    }

    @Test
    fun clickOnEdgeSelectsIt() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(50, 0)

        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)
    }

    @Test
    fun clickInsideOrOnEmptySpaceClearsSelection() {
        val editor = editorWithSquare()

        editor.click(50, 0)
        editor.click(50, 50) // inside
        assertEquals(Selection.None, editor.selection)

        editor.click(50, 0)
        editor.click(500, 500) // empty space
        assertEquals(Selection.None, editor.selection)
    }

    // ---- dragging ----

    @Test
    fun draggingVertexMovesOnlyThatVertex() {
        val editor = editorWithSquare()

        editor.drag(100, 0, 120, -10)

        assertEquals(
            listOf(Vec2(0.0, 0.0), Vec2(120.0, -10.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0)),
            editor.vertexPositions(),
        )
    }

    @Test
    fun draggingEdgeMovesItsEndsAndControlPoints() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        val c1 = ControlPoint(Vec2(30.0, -20.0))
        val c2 = ControlPoint(Vec2(70.0, -20.0))
        e0.shape = EdgeShape.Bezier(c1, c2)

        editor.drag(50, -15, 50, -5) // grab the curve itself – its middle (t = 0.5) is (50,-15)

        assertEquals(
            listOf(Vec2(0.0, 10.0), Vec2(100.0, 10.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0)),
            editor.vertexPositions(),
        )
        assertEquals(Vec2(30.0, -10.0), c1.pos)
        assertEquals(Vec2(70.0, -10.0), c2.pos)
    }

    @Test
    fun draggingInsideMovesWholePolygon() {
        val editor = editorWithSquare()

        editor.drag(50, 50, 60, 70)

        assertEquals(squarePositions.map { it + Vec2(10.0, 20.0) }, editor.vertexPositions())
    }

    @Test
    fun moveAfterReleaseDoesNotDrag() {
        val editor = editorWithSquare()

        editor.drag(100, 0, 120, 0)
        editor.onMove(Vec2(300.0, 300.0))

        assertEquals(Vec2(120.0, 0.0), editor.vertexPositions()[1])
    }

    // ---- shift + click (table from 6c) ----

    @Test
    fun shiftClickOnSecondEdgeSelectsBothInClickOrder() {
        val editor = editorWithSquare()
        val (e0, e1) = editor.scene.polygons[0].edges

        editor.click(50, 0)
        editor.click(100, 50, shift = true)

        assertEquals(Selection.TwoEdges(e0, e1), editor.selection)
    }

    @Test
    fun shiftClickOnSameEdgeKeepsSelection() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(50, 0)
        editor.click(50, 0, shift = true)

        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)
    }

    @Test
    fun shiftClickOnThirdEdgeReplacesSecondOne() {
        val editor = editorWithSquare()
        val (e0, _, e2) = editor.scene.polygons[0].edges

        editor.click(50, 0)
        editor.click(100, 50, shift = true)
        editor.click(50, 100, shift = true)

        assertEquals(Selection.TwoEdges(e0, e2), editor.selection)
    }

    @Test
    fun shiftClickOnEdgeOfThePairKeepsSelection() {
        val editor = editorWithSquare()
        val (e0, e1) = editor.scene.polygons[0].edges

        editor.click(50, 0)
        editor.click(100, 50, shift = true)
        editor.click(50, 0, shift = true)   // first
        editor.click(100, 50, shift = true) // second

        assertEquals(Selection.TwoEdges(e0, e1), editor.selection)
    }

    @Test
    fun shiftClickOnEdgeWithoutSelectedEdgeSelectsJustThatEdge() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(50, 0, shift = true) // nothing selected
        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)

        editor.click(100, 0)               // vertex selected
        editor.click(50, 100, shift = true)
        assertEquals(Selection.OnEdge(poly, poly.edges[2]), editor.selection)
    }

    @Test
    fun shiftClickOnNonEdgeBehavesLikeNormalClick() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.click(50, 0)
        editor.click(100, 0, shift = true) // vertex
        assertEquals(Selection.OnVertex(poly, poly.vertices[1]), editor.selection)

        editor.click(50, 0)
        editor.click(50, 50, shift = true) // inside
        assertEquals(Selection.None, editor.selection)
    }

    @Test
    fun shiftClickPairsEdgesOfDifferentPolygons() {
        val editor = editorWithSquare()
        val other = editor.scene.polygon(200 to 0, 300 to 0, 300 to 100, 200 to 100)
        val e0 = editor.scene.polygons[0].edges[0]

        editor.click(50, 0)
        editor.click(250, 0, shift = true)

        assertEquals(Selection.TwoEdges(e0, other.edges[0]), editor.selection)
    }

    @Test
    fun shiftPressDoesNotStartDrag() {
        val editor = editorWithSquare()

        editor.onPrimaryPress(Vec2(50.0, 0.0), shift = true)
        editor.onMove(Vec2(50.0, 30.0))
        editor.onRelease()

        assertEquals(squarePositions, editor.vertexPositions())
    }

    // ---- double click ----

    // What SceneCanvas sends for a double click: press, release, press + double click, release
    private fun Editor.doubleClick(x: Number, y: Number) {
        val p = Vec2(x.toDouble(), y.toDouble())
        onPrimaryPress(p)
        onRelease()
        onPrimaryPress(p)
        onDoubleClick(p)
    }

    @Test
    fun doubleClickOnEdgeInsertsVertexInTheMiddleAndSelectsIt() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.doubleClick(30, 0) // anywhere on e0, the vertex goes to its middle
        editor.onRelease()

        assertEquals(5, poly.vertices.size)
        val v = poly.vertices[1]
        assertEquals(Vec2(50.0, 0.0), v.pos)
        assertEquals(Selection.OnVertex(poly, v), editor.selection)
    }

    @Test
    fun doubleClickOnEdgeCancelsEdgeDrag() {
        val editor = editorWithSquare()

        editor.doubleClick(30, 0)
        editor.onMove(Vec2(30.0, 40.0)) // button still down after the second press
        editor.onRelease()

        assertEquals(
            listOf(Vec2(0.0, 0.0), Vec2(50.0, 0.0), Vec2(100.0, 0.0), Vec2(100.0, 100.0), Vec2(0.0, 100.0)),
            editor.vertexPositions(),
        )
    }

    @Test
    fun doubleClickInsideInsertsNothingAndKeepsPolygonDrag() {
        val editor = editorWithSquare()

        editor.doubleClick(50, 50)
        editor.onMove(Vec2(60.0, 50.0))
        editor.onRelease()

        assertEquals(squarePositions.map { it + Vec2(10.0, 0.0) }, editor.vertexPositions())
    }

    // ---- delete ----

    @Test
    fun deleteRemovesSelectedVertexAndClearsSelection() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        val v1 = poly.vertices[1]

        editor.click(100, 0)
        editor.deleteSelected()

        assertEquals(3, poly.vertices.size)
        assertTrue(v1 !in poly.vertices)
        assertEquals(Selection.None, editor.selection)
        assertNull(editor.hovered)
    }

    @Test
    fun deleteOnTriangleKeepsVertexAndSelectionAndShowsMessage() {
        val editor = Editor(Scene().apply { polygon(0 to 0, 100 to 0, 0 to 100) })
        val tri = editor.scene.polygons[0]
        editor.click(100, 0)
        val versionBefore = editor.version

        editor.deleteSelected()

        assertEquals(3, tri.vertices.size)
        assertEquals(Selection.OnVertex(tri, tri.vertices[1]), editor.selection)
        assertNotNull(editor.message)
        assertEquals(versionBefore, editor.version, "nothing changed, no redraw needed")

        editor.click(500, 500)
        assertNull(editor.message, "next click clears the message")
    }

    @Test
    fun deleteWithoutSelectedVertexDoesNothing() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]

        editor.deleteSelected() // nothing selected
        editor.click(50, 0)
        editor.deleteSelected() // edge selected

        assertEquals(4, poly.vertices.size)
        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)
    }

    @Test
    fun deleteWhileDraggingVertexStopsTheDrag() {
        val editor = editorWithSquare()
        val v1 = editor.scene.polygons[0].vertices[1]

        editor.onPrimaryPress(Vec2(100.0, 0.0))
        editor.deleteSelected()
        editor.onMove(Vec2(150.0, 50.0))
        editor.onRelease()

        assertEquals(Vec2(100.0, 0.0), v1.pos, "removed vertex must not follow the mouse")
    }

    // ---- dragging with constraints (solver) ----

    private fun assertSatisfied(editor: Editor) {
        editor.scene.constraints.forEach {
            assertTrue(it.error() < Solver.EPS, "constraint not satisfied, error ${it.error()}")
        }
    }

    private fun assertClose(expected: Vec2, actual: Vec2) {
        assertTrue(abs(expected.x - actual.x) < 1e-9 && abs(expected.y - actual.y) < 1e-9, "expected $expected, got $actual")
    }

    @Test
    fun draggedVertexIsUnderTheCursorAndKeepsLengthsOfItsEdges() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        val (e0, _, _, e3) = poly.edges // the two edges of v0
        editor.scene.addConstraint(FixedLength(e0, 100.0))
        editor.scene.addConstraint(FixedLength(e3, 100.0))

        editor.drag(0, 0, -20, -10)

        assertEquals(Vec2(-20.0, -10.0), poly.vertices[0].pos)
        assertSatisfied(editor)
    }

    @Test
    fun draggedEdgeMovesExactlyByDeltaAndKeepsPerpendicularNeighbour() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        val (e0, e1) = poly.edges
        editor.scene.addConstraint(Perpendicular(e0, e1))

        editor.drag(50, 0, 70, 0) // e0 sideways: e1 would lean without the solver

        assertClose(Vec2(20.0, 0.0), e0.start.pos)
        assertClose(Vec2(120.0, 0.0), e0.end.pos)
        assertSatisfied(editor)
        assertTrue(abs(e1.vector.x) < Solver.EPS, "e1 should be vertical again, got ${e1.vector}")
    }

    @Test
    fun draggingInOnePolygonRotatesConstrainedEdgeOfAnother() {
        val editor = editorWithSquare()
        val square = editor.scene.polygons[0]
        val other = editor.scene.polygon(200 to 0, 200 to 100, 300 to 100) // e0 vertical
        editor.scene.addConstraint(Perpendicular(square.edges[0], other.edges[0]))

        editor.drag(100, 0, 100, 30) // tilts the square's e0

        assertEquals(Vec2(100.0, 30.0), square.vertices[1].pos)
        assertSatisfied(editor)
        assertTrue(abs(other.edges[0].vector.x) > 1.0, "other polygon's edge should have rotated")
    }

    @Test
    fun draggingWholePolygonDoesNotTouchConstrainedOtherPolygon() {
        val editor = editorWithSquare()
        val square = editor.scene.polygons[0]
        val other = editor.scene.polygon(200 to 0, 200 to 100, 300 to 100)
        editor.scene.addConstraint(Perpendicular(square.edges[0], other.edges[0]))
        val otherBefore = other.vertices.map { it.pos }

        editor.drag(50, 50, 80, 70)

        assertEquals(squarePositions.map { it + Vec2(30.0, 20.0) }, editor.vertexPositions())
        assertEquals(otherBefore, other.vertices.map { it.pos })
    }

    @Test
    fun whenSolverFailsTheWholePolygonFollowsTheCursor() {
        // 1 + 1 < 5: no triangle satisfies these – added directly to the scene to force a solver failure
        val editor = Editor(Scene().apply { polygon(0 to 0, 100 to 0, 0 to 100) })
        val tri = editor.scene.polygons[0]
        val (e0, e1, e2) = tri.edges
        listOf(FixedLength(e0, 1.0), FixedLength(e1, 1.0), FixedLength(e2, 5.0)).forEach(editor.scene::addConstraint)

        editor.drag(100, 0, 110, 20)

        // shape unchanged, everything moved by the drag delta (10, 20)
        assertEquals(
            listOf(Vec2(10.0, 20.0), Vec2(110.0, 20.0), Vec2(10.0, 120.0)),
            tri.vertices.map { it.pos },
        )
        assertFalse(editor.scene.constraints.all { it.error() < Solver.EPS }, "still unsatisfiable, but untouched")
    }

    // ---- adding and removing constraints ----

    private fun editorWithTriangle() = Editor(Scene().apply { polygon(0 to 0, 100 to 0, 30 to 80) })

    private fun Editor.allPositions() = scene.allPoints().map { it.pos }

    @Test
    fun setLengthChangesTheEdgeSymmetricallyAndAddsTheConstraint() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        val versionBefore = editor.version

        editor.setLength(e0, 60.0)

        assertClose(Vec2(20.0, 0.0), e0.start.pos) // both ends free -> each moves half
        assertClose(Vec2(80.0, 0.0), e0.end.pos)
        val c = editor.scene.constraintsOf(e0).single() as FixedLength
        assertEquals(60.0, c.length)
        assertNull(editor.message)
        assertTrue(editor.version > versionBefore)
    }

    @Test
    fun secondLengthOnTheSameEdgeReplacesTheFirst() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]

        editor.setLength(e0, 60.0)
        editor.setLength(e0, 80.0)

        val c = editor.scene.constraints.single() as FixedLength
        assertEquals(80.0, c.length)
        assertSatisfied(editor)
    }

    @Test
    fun conflictingLengthIsRejectedAndEverythingGoesBack() {
        val editor = editorWithTriangle()
        val (e0, e1, e2) = editor.scene.polygons[0].edges
        editor.setLength(e0, 100.0)
        editor.setLength(e1, 100.0)
        val constraintsBefore = editor.scene.constraints.toList()
        val positionsBefore = editor.allPositions()
        val versionBefore = editor.version

        editor.setLength(e2, 300.0) // 100 + 100 < 300: no such triangle

        assertEquals(positionsBefore, editor.allPositions())
        assertEquals(constraintsBefore, editor.scene.constraints)
        assertNotNull(editor.message)
        assertEquals(versionBefore, editor.version, "nothing changed, no redraw needed")
    }

    @Test
    fun conflictingReplacementBringsTheOldLengthBack() {
        val editor = editorWithTriangle()
        val (e0, e1, e2) = editor.scene.polygons[0].edges
        listOf(e0, e1, e2).forEach { editor.setLength(it, 100.0) } // equilateral
        val oldLength = editor.scene.constraintsOf(e0).single()

        editor.setLength(e0, 300.0)

        assertNotNull(editor.message)
        assertEquals(listOf(oldLength), editor.scene.constraintsOf(e0))
        assertEquals(3, editor.scene.constraints.size)
        assertSatisfied(editor)
    }

    @Test
    fun makePerpendicularMakesARightAngle() {
        val editor = editorWithTriangle()
        val (e0, e1) = editor.scene.polygons[0].edges

        editor.makePerpendicular(e0, e1)

        assertEquals(1, editor.scene.constraints.size)
        assertSatisfied(editor)
        assertNull(editor.message)
    }

    @Test
    fun perpendicularConflictingWithLengthsIsRejected() {
        val editor = editorWithTriangle()
        val (e0, e1, e2) = editor.scene.polygons[0].edges
        listOf(e0, e1, e2).forEach { editor.setLength(it, 100.0) } // equilateral: all angles 60°
        val positionsBefore = editor.allPositions()

        editor.makePerpendicular(e0, e1)

        assertNotNull(editor.message)
        assertEquals(3, editor.scene.constraints.size)
        assertEquals(positionsBefore, editor.allPositions())
    }

    @Test
    fun perpendicularNeedsTwoDifferentEdges() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]

        editor.makePerpendicular(e0, e0)

        assertNotNull(editor.message)
        assertTrue(editor.scene.constraints.isEmpty())
    }

    @Test
    fun duplicatePerpendicularIsRejectedInEitherOrder() {
        val editor = editorWithSquare()
        val (e0, e1) = editor.scene.polygons[0].edges
        editor.makePerpendicular(e0, e1)

        editor.makePerpendicular(e0, e1)
        assertNotNull(editor.message)
        editor.makePerpendicular(e1, e0)
        assertNotNull(editor.message)

        assertEquals(1, editor.scene.constraints.size)
    }

    @Test
    fun invalidLengthIsRejectedWithoutTouchingAnything() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        val versionBefore = editor.version

        for (length in listOf(0.0, -5.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            editor.setLength(e0, length)
            assertNotNull(editor.message, "length $length should be rejected")
        }

        assertTrue(editor.scene.constraints.isEmpty())
        assertEquals(squarePositions, editor.vertexPositions())
        assertEquals(versionBefore, editor.version)
    }

    @Test
    fun successfulActionClearsAnOldMessage() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]

        editor.setLength(e0, -1.0)
        editor.setLength(e0, 50.0)

        assertNull(editor.message)
    }

    @Test
    fun removeConstraintRemovesItAndMovesNothing() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.setLength(e0, 60.0)
        val positionsBefore = editor.allPositions()

        editor.removeConstraint(editor.scene.constraints.single())

        assertTrue(editor.scene.constraints.isEmpty())
        assertEquals(positionsBefore, editor.allPositions())
    }

    // ---- edge shape ----

    @Test
    fun toBezierPutsControlPointsAtThirdsOfTheEdge() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]

        editor.toBezier(e0)

        val shape = e0.shape as EdgeShape.Bezier
        assertClose(Vec2(100.0 / 3, 0.0), shape.c1.pos)
        assertClose(Vec2(200.0 / 3, 0.0), shape.c2.pos)
        assertEquals(squarePositions, editor.vertexPositions()) // vertices don't move
    }

    @Test
    fun toBezierRemovesOnlyTheConstraintsOfThatEdge() {
        val editor = editorWithSquare()
        val (e0, e1, e2) = editor.scene.polygons[0].edges
        editor.setLength(e0, 100.0)
        editor.makePerpendicular(e0, e1)
        editor.setLength(e2, 100.0)
        val e2Length = editor.scene.constraintsOf(e2).single()

        editor.toBezier(e0)

        assertEquals(listOf(e2Length), editor.scene.constraints) // e0's length and the e0–e1 pair are gone
    }

    @Test
    fun toBezierOnACurveDoesNotResetIt() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.toBezier(e0)
        val shape = e0.shape as EdgeShape.Bezier
        shape.c1.pos = Vec2(30.0, -40.0) // bent by the user

        editor.toBezier(e0)

        assertTrue(e0.shape === shape)
        assertEquals(Vec2(30.0, -40.0), shape.c1.pos)
    }

    @Test
    fun toLineRemovesControlPointsAndKeepsSelection() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.click(50, 0)
        editor.toBezier(e0)

        editor.toLine(e0)

        assertEquals(EdgeShape.Line, e0.shape)
        assertEquals(4, editor.scene.allPoints().size) // only the vertices are left
        assertTrue(e0 in editor.selection)
    }

    @Test
    fun shapeChangeBumpsVersionOnlyWhenSomethingChanges() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        val before = editor.version

        editor.toLine(e0) // already a line
        assertEquals(before, editor.version)

        editor.toBezier(e0)
        assertTrue(editor.version > before)
    }

    @Test
    fun setLengthOnACurveIsRejected() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.toBezier(e0)

        editor.setLength(e0, 50.0)

        assertTrue(editor.scene.constraints.isEmpty())
        assertNotNull(editor.message)
    }

    // ---- curves under the mouse ----

    // Square with e0 bent upwards: c1 (30,-60), c2 (70,-60); the curve's middle (t = 0.5) is (50,-45)
    private fun editorWithBentSquare(): Editor {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        e0.shape = EdgeShape.Bezier(ControlPoint(Vec2(30.0, -60.0)), ControlPoint(Vec2(70.0, -60.0)))
        return editor
    }

    private fun Editor.bezierOf(edgeIndex: Int) = scene.polygons[0].edges[edgeIndex].shape as EdgeShape.Bezier

    @Test
    fun clickOnBentCurveSelectsTheEdge() {
        val editor = editorWithBentSquare()
        val poly = editor.scene.polygons[0]

        editor.click(50, -45)

        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)
    }

    @Test
    fun clickOnTheChordOfABentCurveDoesNotSelectTheEdge() {
        val editor = editorWithBentSquare()

        editor.click(50, 0) // where the straight edge used to be – now inside the polygon

        assertEquals(Selection.None, editor.selection)
    }

    @Test
    fun areaBetweenChordAndCurveIsInsideThePolygon() {
        val editor = editorWithBentSquare()

        assertTrue(Vec2(50.0, -30.0) in editor.scene.polygons[0])
    }

    @Test
    fun clickOnControlPointSelectsIt() {
        val editor = editorWithBentSquare()
        val poly = editor.scene.polygons[0]

        editor.click(70, -60)

        assertEquals(Selection.OnControlPoint(poly, poly.edges[0], editor.bezierOf(0).c2), editor.selection)
    }

    @Test
    fun hoveringOverControlPointReportsIt() {
        val editor = editorWithBentSquare()

        editor.onMove(Vec2(31.0, -59.0))

        assertEquals(editor.bezierOf(0).c1, (editor.hovered as Hit.OnControlPoint).point)
    }

    @Test
    fun draggedControlPointIsUnderTheCursorAndVerticesStay() {
        val editor = editorWithBentSquare()

        editor.drag(30, -60, 10, -80)

        assertEquals(Vec2(10.0, -80.0), editor.bezierOf(0).c1.pos)
        assertEquals(Vec2(70.0, -60.0), editor.bezierOf(0).c2.pos)
        assertEquals(squarePositions, editor.vertexPositions())
    }

    @Test
    fun vertexWinsOverControlPointLyingOnIt() {
        val editor = editorWithBentSquare()
        val poly = editor.scene.polygons[0]
        editor.bezierOf(0).c1.pos = Vec2(2.0, 0.0)

        editor.click(1, 0)

        assertEquals(Selection.OnVertex(poly, poly.vertices[0]), editor.selection)
    }

    @Test
    fun doubleClickOnControlPointInsertsNothing() {
        val editor = editorWithBentSquare()

        editor.onPrimaryPress(Vec2(30.0, -60.0))
        editor.onRelease()
        editor.onPrimaryPress(Vec2(30.0, -60.0))
        editor.onDoubleClick(Vec2(30.0, -60.0))
        editor.onRelease()

        assertEquals(4, editor.scene.polygons[0].edgeCount)
    }

    @Test
    fun shiftClickOnEdgeAfterControlPointSelectsJustThatEdge() {
        val editor = editorWithBentSquare()
        val poly = editor.scene.polygons[0]
        editor.click(30, -60)

        editor.click(100, 50, shift = true)

        assertEquals(Selection.OnEdge(poly, poly.edges[1]), editor.selection)
    }

    // ---- control points follow their vertex ----

    // Square with two curves: e0 (v0→v1) and e1 (v1→v2); e2 and e3 stay lines
    private fun editorWithTwoCurves(): Editor {
        val editor = editorWithSquare()
        val (e0, e1) = editor.scene.polygons[0].edges
        e0.shape = EdgeShape.Bezier(ControlPoint(Vec2(30.0, -30.0)), ControlPoint(Vec2(70.0, -30.0)))
        e1.shape = EdgeShape.Bezier(ControlPoint(Vec2(130.0, 30.0)), ControlPoint(Vec2(130.0, 70.0)))
        return editor
    }

    @Test
    fun controlPointsAtVertexBetweenTwoCurves() {
        val editor = editorWithTwoCurves()
        val poly = editor.scene.polygons[0]

        // v1 is the end of e0 (-> its c2) and the start of e1 (-> its c1)
        assertEquals(listOf(editor.bezierOf(0).c2, editor.bezierOf(1).c1), poly.controlPointsAt(poly.vertices[1]))
    }

    @Test
    fun controlPointsAtVertexBetweenCurveAndLine() {
        val editor = editorWithTwoCurves()
        val poly = editor.scene.polygons[0]

        assertEquals(listOf(editor.bezierOf(0).c1), poly.controlPointsAt(poly.vertices[0])) // e3 line -> e0 curve
        assertEquals(listOf(editor.bezierOf(1).c2), poly.controlPointsAt(poly.vertices[2])) // e1 curve -> e2 line
    }

    @Test
    fun controlPointsAtVertexBetweenTwoLinesIsEmpty() {
        val editor = editorWithTwoCurves()
        val poly = editor.scene.polygons[0]

        assertTrue(poly.controlPointsAt(poly.vertices[3]).isEmpty())
    }

    @Test
    fun draggingVertexCarriesItsNeighbouringControlPoints() {
        val editor = editorWithTwoCurves()

        editor.drag(100, 0, 110, -20) // v1 by (10,-20)

        assertEquals(Vec2(80.0, -50.0), editor.bezierOf(0).c2.pos)  // next to v1 – moved
        assertEquals(Vec2(140.0, 10.0), editor.bezierOf(1).c1.pos)  // next to v1 – moved
        assertEquals(Vec2(30.0, -30.0), editor.bezierOf(0).c1.pos)  // next to v0 – stays
        assertEquals(Vec2(130.0, 70.0), editor.bezierOf(1).c2.pos)  // next to v2 – stays
    }

    @Test
    fun draggingVertexBetweenLinesMovesOnlyThatVertex() {
        val editor = editorWithTwoCurves()
        val controlsBefore = editor.scene.polygons[0].edges.flatMap { it.controlPoints }.map { it.pos }

        editor.drag(0, 100, -10, 110) // v3

        assertEquals(Vec2(-10.0, 110.0), editor.vertexPositions()[3])
        assertEquals(controlsBefore, editor.scene.polygons[0].edges.flatMap { it.controlPoints }.map { it.pos })
    }

    @Test
    fun draggingEdgeMovesEveryAttachedControlPointExactlyOnce() {
        val editor = editorWithTwoCurves()

        editor.drag(122.5, 50, 132.5, 50) // middle of the e1 curve (t = 0.5), by (10,0); e1 is v1→v2

        // e1's own control points – moved once, not twice
        assertEquals(Vec2(140.0, 30.0), editor.bezierOf(1).c1.pos)
        assertEquals(Vec2(140.0, 70.0), editor.bezierOf(1).c2.pos)
        // e0's control point next to v1 – carried along
        assertEquals(Vec2(80.0, -30.0), editor.bezierOf(0).c2.pos)
        // e0's control point next to v0 – stays
        assertEquals(Vec2(30.0, -30.0), editor.bezierOf(0).c1.pos)
    }

    // ---- arcs ----

    @Test
    fun toArcKeepsVerticesAndRemovesTheEdgesConstraints() {
        val editor = editorWithSquare()
        val (e0, e1, e2) = editor.scene.polygons[0].edges
        editor.setLength(e0, 100.0)
        editor.makePerpendicular(e0, e1)
        editor.setLength(e2, 100.0)
        val e2Length = editor.scene.constraintsOf(e2).single()

        editor.toArc(e0)

        assertEquals(EdgeShape.Arc, e0.shape)
        assertEquals(squarePositions, editor.vertexPositions())
        assertEquals(listOf(e2Length), editor.scene.constraints)
    }

    @Test
    fun toArcFromBezierDropsTheControlPoints() {
        val editor = editorWithBentSquare()
        val e0 = editor.scene.polygons[0].edges[0]

        editor.toArc(e0)

        assertEquals(EdgeShape.Arc, e0.shape)
        assertEquals(4, editor.scene.allPoints().size)
    }

    @Test
    fun toArcOnAnArcChangesNothing() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.toArc(e0)
        val before = editor.version

        editor.toArc(e0)

        assertEquals(before, editor.version)
    }

    @Test
    fun clickOnTheSemicircleSelectsTheArcEdge() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        editor.toArc(poly.edges[0]) // semicircle over e0: center (50,0), radius 50, top at (50,-50)

        editor.click(50, -50)

        assertEquals(Selection.OnEdge(poly, poly.edges[0]), editor.selection)
    }

    @Test
    fun clickOnTheChordOfAnArcGrabsThePolygon() {
        val editor = editorWithSquare()
        editor.toArc(editor.scene.polygons[0].edges[0])

        editor.drag(50, 0, 60, 0) // the chord is inside now – the whole polygon moves

        assertEquals(squarePositions.map { it + Vec2(10.0, 0.0) }, editor.vertexPositions())
    }

    @Test
    fun arcFollowsItsVertices() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        editor.toArc(poly.edges[0])

        editor.drag(100, 0, 140, 0) // v1: the chord is now (0,0)–(140,0), the semicircle grows

        assertEquals(arcPoints(Vec2(0.0, 0.0), Vec2(140.0, 0.0)), poly.polylineOf(poly.edges[0]))
    }

    // ---- continuity ----

    private fun Editor.vertex(i: Int) = scene.polygons[0].vertices[i]

    private fun Editor.setContinuity(i: Int, c: Continuity) = setContinuity(scene.polygons[0], vertex(i), c)

    @Test
    fun continuityNotAllowedAtTheVertexIsRejected() {
        val editor = editorWithSquare()
        val before = editor.version

        editor.setContinuity(0, Continuity.G1) // two lines

        assertEquals(Continuity.G0, editor.vertex(0).continuity)
        assertNotNull(editor.message)
        assertEquals(before, editor.version)
    }

    @Test
    fun c1NextToAnArcIsRejected() {
        val editor = editorWithSquare()
        editor.toArc(editor.scene.polygons[0].edges[0])

        editor.setContinuity(0, Continuity.C1)

        assertEquals(Continuity.G0, editor.vertex(0).continuity)
        assertNotNull(editor.message)
    }

    @Test
    fun setContinuityMarksTheChoiceAsExplicit() {
        val editor = editorWithBentSquare()
        val before = editor.version

        editor.setContinuity(0, Continuity.C1)

        assertEquals(Continuity.C1, editor.vertex(0).continuity)
        assertTrue(editor.vertex(0).continuityExplicit)
        assertNull(editor.message)
        assertTrue(editor.version > before)
    }

    @Test
    fun g1AtTheOtherEndOfAnArcSwitchesTheFirstToG0() {
        val editor = editorWithSquare()
        editor.toArc(editor.scene.polygons[0].edges[0])

        editor.setContinuity(0, Continuity.G1)
        editor.setContinuity(1, Continuity.G1)

        assertEquals(Continuity.G0, editor.vertex(0).continuity)
        assertEquals(Continuity.G1, editor.vertex(1).continuity)
        assertNotNull(editor.message) // the user is told why v0 changed
    }

    @Test
    fun arcFollowsItsNeighbourWhenTheNeighbourIsDragged() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        val arc = poly.edges[0]
        editor.toArc(arc)
        editor.setContinuity(0, Continuity.G1)
        val before = poly.polylineOf(arc)

        editor.drag(0, 100, -50, 100) // v3: e3 now comes into v0 heading (50, -100)

        val after = poly.polylineOf(arc)
        assertTrue(after != before, "the arc changed")
        assertEquals(arcPoints(Vec2(0.0, 0.0), Vec2(100.0, 0.0), startTangent = Vec2(50.0, -100.0)), after)
    }

    @Test
    fun arcBackToLineDropsG1() {
        val editor = editorWithSquare()
        val e0 = editor.scene.polygons[0].edges[0]
        editor.toArc(e0)
        editor.setContinuity(0, Continuity.G1)

        editor.toLine(e0)

        assertEquals(Continuity.G0, editor.vertex(0).continuity)
    }

    @Test
    fun bezierWithC1ToArcLowersToG1() {
        val editor = editorWithBentSquare()
        editor.setContinuity(0, Continuity.C1)

        editor.toArc(editor.scene.polygons[0].edges[0])

        assertEquals(Continuity.G1, editor.vertex(0).continuity)
    }

    @Test
    fun edgeWithG1AtBothEndsToArcKeepsOnlyTheStart() {
        val editor = editorWithBentSquare()
        editor.setContinuity(0, Continuity.G1)
        editor.setContinuity(1, Continuity.G1)

        editor.toArc(editor.scene.polygons[0].edges[0])

        assertEquals(Continuity.G1, editor.vertex(0).continuity)
        assertEquals(Continuity.G0, editor.vertex(1).continuity)
    }

    @Test
    fun insertingAVertexIntoAnArcDropsG1AtItsEnds() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        editor.toArc(poly.edges[0])
        editor.setContinuity(0, Continuity.G1)

        editor.scene.insertVertex(poly, poly.edges[0]) // the arc is replaced by two lines

        assertEquals(Continuity.G0, poly.vertices[0].continuity)
    }

    @Test
    fun removingAVertexNormalizesTheNeighbours() {
        val editor = editorWithSquare()
        val poly = editor.scene.polygons[0]
        editor.toArc(poly.edges[0])
        val v1 = editor.vertex(1)
        editor.setContinuity(1, Continuity.G1)

        editor.scene.removeVertex(poly, editor.vertex(0)) // e3 + arc become one line into v1

        assertEquals(Continuity.G0, v1.continuity)
    }
}
