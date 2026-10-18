package com.example.polygoneditor

import com.example.polygoneditor.render.bresenham
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BresenhamTest {
    private fun pixels(x0: Int, y0: Int, x1: Int, y1: Int) =
        buildList { bresenham(x0, y0, x1, y1) { x, y -> add(x to y) } }

    // One end in each of the 8 octants, starting from (0, 0)
    private val octantEnds = listOf(8 to 3, 3 to 8, -3 to 8, -8 to 3, -8 to -3, -3 to -8, 3 to -8, 8 to -3)

    // Ends included, one pixel per step of the driving axis, every step to a neighbouring pixel
    private fun assertValidLine(x0: Int, y0: Int, x1: Int, y1: Int) {
        val px = pixels(x0, y0, x1, y1)
        val name = "($x0, $y0) -> ($x1, $y1)"

        assertEquals(x0 to y0, px.first(), "$name: first pixel")
        assertEquals(x1 to y1, px.last(), "$name: last pixel")
        assertEquals(max(abs(x1 - x0), abs(y1 - y0)) + 1, px.size, "$name: pixel count")
        px.zipWithNext { (ax, ay), (bx, by) ->
            assertTrue(abs(bx - ax) <= 1 && abs(by - ay) <= 1, "$name: gap between ($ax, $ay) and ($bx, $by)")
        }
    }

    @Test
    fun lectureExampleGivesExactPixels() {
        // tie at x = 4 (ideal y = 1.5, d = 0): with d < 0 -> E, the tie goes to NE
        assertEquals(
            listOf(0 to 0, 1 to 0, 2 to 1, 3 to 1, 4 to 2, 5 to 2, 6 to 2, 7 to 3, 8 to 3),
            pixels(0, 0, 8, 3),
        )
    }

    @Test
    fun startOutsideOriginShiftsTheSameShape() {
        assertEquals(pixels(0, 0, 8, 3).map { (x, y) -> x + 5 to y + 7 }, pixels(5, 7, 13, 10))
    }

    @Test
    fun linesInAllEightOctantsAreValid() {
        for ((x1, y1) in octantEnds) assertValidLine(0, 0, x1, y1)
    }

    @Test
    fun octantsAreMirrorImagesOfTheFirstOne() {
        val first = pixels(0, 0, 8, 3)

        assertEquals(first.map { (x, y) -> -x to y }, pixels(0, 0, -8, 3), "mirror across the y axis")
        assertEquals(first.map { (x, y) -> x to -y }, pixels(0, 0, 8, -3), "mirror across the x axis")
        assertEquals(first.map { (x, y) -> -x to -y }, pixels(0, 0, -8, -3), "mirror across both axes")
        assertEquals(first.map { (x, y) -> y to x }, pixels(0, 0, 3, 8), "mirror across the diagonal")
    }

    @Test
    fun horizontalVerticalAndDiagonalLines() {
        assertEquals(listOf(0 to 0, 1 to 0, 2 to 0, 3 to 0), pixels(0, 0, 3, 0))
        assertEquals(listOf(0 to 0, -1 to 0, -2 to 0), pixels(0, 0, -2, 0))
        assertEquals(listOf(0 to 0, 0 to 1, 0 to 2, 0 to 3), pixels(0, 0, 0, 3))
        assertEquals(listOf(0 to 0, 0 to -1, 0 to -2), pixels(0, 0, 0, -2))
        assertEquals(listOf(0 to 0, 1 to 1, 2 to 2, 3 to 3), pixels(0, 0, 3, 3))
        assertEquals(listOf(0 to 0, -1 to 1, -2 to 2), pixels(0, 0, -2, 2))
    }

    @Test
    fun zeroLengthLineIsOnePixel() {
        assertEquals(listOf(4 to 7), pixels(4, 7, 4, 7))
    }

    @Test
    fun everyPixelIsAtMostHalfAPixelFromTheIdealLine() {
        // All ends on a grid around the start – covers every octant and the boundaries between them
        for (x1 in -9..9) for (y1 in -9..9) {
            assertValidLine(0, 0, x1, y1)
            if (x1 == 0 && y1 == 0) continue

            for ((x, y) in pixels(0, 0, x1, y1)) {
                // error measured along the minor axis, as in the algorithm
                val error = if (abs(x1) >= abs(y1)) abs(y - y1.toDouble() / x1 * x) else abs(x - x1.toDouble() / y1 * y)
                assertTrue(error <= 0.5 + 1e-9, "(0, 0) -> ($x1, $y1): pixel ($x, $y) is $error away")
            }
        }
    }
}
