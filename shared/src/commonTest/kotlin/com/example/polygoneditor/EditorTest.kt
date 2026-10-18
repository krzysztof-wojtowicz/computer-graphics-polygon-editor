package com.example.polygoneditor

import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.Selection
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Scene
import kotlin.test.Test
import kotlin.test.assertEquals
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

        editor.drag(50, 0, 50, 10)

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
}
