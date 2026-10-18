package com.example.polygoneditor.render

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.roundToInt

interface Painter {
    fun line(a: Offset, b: Offset, color: Color)
}

class LibraryPainter(private val scope: DrawScope, private val strokeWidth: Float) : Painter {
    override fun line(
        a: Offset,
        b: Offset,
        color: Color
    ) {
        scope.drawLine(color, a, b, strokeWidth)
    }

}

class BresenhamPainter(private val scope: DrawScope) : Painter {
    override fun line(
        a: Offset,
        b: Offset,
        color: Color
    ) {
        val points = mutableListOf<Offset>()
        bresenham(a.x.roundToInt(), a.y.roundToInt(), b.x.roundToInt(), b.y.roundToInt()) { x, y ->
            points += Offset(x + 0.5f, y + 0.5f) // center of the pixel, so the 1 px point isn't smeared over 4 pixels
        }
        // Points mode: every pixel separately – the library doesn't connect them
        scope.drawPoints(points, PointMode.Points, color, strokeWidth = 1f)
    }

}