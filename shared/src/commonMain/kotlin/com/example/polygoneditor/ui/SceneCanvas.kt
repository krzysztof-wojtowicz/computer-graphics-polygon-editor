package com.example.polygoneditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.Hit
import com.example.polygoneditor.editor.LineAlgorithm
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.midpoint
import com.example.polygoneditor.geometry.perpendicular
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.render.BresenhamPainter
import com.example.polygoneditor.render.LibraryPainter
import com.example.polygoneditor.render.Painter

private val FillColor = Color(0x223B82F6)
private val EdgeColor = Color(0xFF1F2937)
private val VertexColor = Color(0xFFDC2626)
private val HighlightColor = Color(0xFFF59E0B)
private val SelectedColor = Color(0xFF2563EB)
private val ControlColor = Color(0xFF6B7280)
private val LabelBackground = Color.White.copy(alpha = 0.85f)
private val LabelStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF7C3AED))

// Distance (in dp) of a constraint icon from the middle of its edge
private const val ICON_OFFSET = 14.0

// Max distance (in dp) between the two presses of a double click
private const val DOUBLE_CLICK_SLOP = 4.0

private fun Density.toScene(o: Offset) = Vec2(o.x.toDouble() / density, o.y.toDouble() / density)
private fun Density.toScreen(v: Vec2) = Offset((v.x * density).toFloat(), (v.y * density).toFloat())

@Composable
fun SceneCanvas(editor: Editor, modifier: Modifier = Modifier) {
    // remember: SceneCanvas runs again on every recomposition, the requester has to survive it
    val focusRequester = remember { FocusRequester() }
    // @Composable, so it can't be created inside the draw block
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier
            .clipToBounds()
            .background(Color.White)
            .onKeyEvent { e ->
                // macOS "delete" key is Backspace, fn+delete is Delete
                if (e.type == KeyEventType.KeyDown && (e.key == Key.Delete || e.key == Key.Backspace)) {
                    editor.deleteSelected()
                    true
                } else {
                    false
                }
            }
            .focusRequester(focusRequester) // must come before focusable()
            .focusable()
            .pointerInput(editor) {
                awaitPointerEventScope {
                    var lastPressTime = 0L
                    var lastPressPos: Vec2? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = toScene(event.changes.first().position)
                        // Any click on the canvas takes keyboard focus (e.g. away from a text field in the panel)
                        if (event.type == PointerEventType.Press) focusRequester.requestFocus()
                        when (event.type) {
                            PointerEventType.Press -> when {
                                event.buttons.isPrimaryPressed -> {
                                    val shift = event.keyboardModifiers.isShiftPressed
                                    val time = event.changes.first().uptimeMillis
                                    editor.onPrimaryPress(pos, shift)

                                    // Double click = second press soon after the previous one, near the same spot
                                    val last = lastPressPos
                                    val isDoubleClick = !shift && last != null &&
                                            time - lastPressTime <= viewConfiguration.doubleTapTimeoutMillis &&
                                            pos.distanceTo(last) <= DOUBLE_CLICK_SLOP

                                    if (isDoubleClick) {
                                        editor.onDoubleClick(pos)
                                        lastPressPos = null // a third quick click starts a new sequence
                                    } else {
                                        lastPressTime = time
                                        lastPressPos = pos
                                    }
                                }
                            }

                            PointerEventType.Move -> editor.onMove(pos)
                            PointerEventType.Release ->
                                if (!event.buttons.isPrimaryPressed) editor.onRelease()
                        }
                    }
                }
            }
    ) {
        editor.version // reading this state subscribes the canvas to redraw on every model change
        val hovered = editor.hovered
        val selection = editor.selection
        val stroke = 1.dp.toPx()
        val vertexRadius = 5.dp.toPx()
        val painter: Painter = when (editor.lineAlgorithm) {
            LineAlgorithm.Library -> LibraryPainter(this, stroke)
            LineAlgorithm.Bresenham -> BresenhamPainter(this)
        }

        // Draw each polygon
        for (poly in editor.scene.polygons) {
            // Get outline points for fill
            val outline = poly.outline().map { toScreen(it) }

            // Draw fill of the polygon
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(outline[0].x, outline[0].y)
                outline.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            drawPath(
                path,
                if (hovered is Hit.Inside && hovered.polygon === poly) FillColor.copy(alpha = 0.25f) else FillColor
            )

            // Draw edges of the polygon
            for (edge in poly.edges) {
                val isHovered = hovered is Hit.OnEdge && hovered.polygon === poly && hovered.edge == edge
                val isSelected = edge in selection
                val color = when {
                    isSelected -> SelectedColor
                    isHovered -> HighlightColor
                    else -> EdgeColor
                }

                // we draw each segment of given edge
                edge.polyline.map { toScreen(it) }.zipWithNext { a, b -> painter.line(a, b, color) }
            }

            // Draw control polygon for Bézier curves
            val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
            val half = 4.dp.toPx() // half of the size of control point
            for (edge in poly.edges) {
                val s = edge.shape as? EdgeShape.Bezier ?: continue

                // start – c1 – c2 – end, dashed
                listOf(edge.start.pos, s.c1.pos, s.c2.pos, edge.end.pos)
                    .map { toScreen(it) }
                    .zipWithNext { a, b -> drawLine(ControlColor, a, b, stroke, pathEffect = dash) }

                // Control points as squares centered at their positions
                for (c in listOf(s.c1, s.c2)) {
                    val isHovered = hovered is Hit.OnControlPoint && hovered.point === c
                    val isSelected = c in selection
                    val h = if (isHovered || isSelected) half * 1.5f else half
                    drawRect(
                        color = when {
                            isSelected -> SelectedColor
                            isHovered -> HighlightColor
                            else -> ControlColor
                        },
                        topLeft = toScreen(c.pos) - Offset(h, h),
                        size = Size(2 * h, 2 * h),
                    )
                }
            }

            // Draw vertices of the polygon
            for (v in poly.vertices) {
                val isHovered = hovered is Hit.OnVertex && hovered.vertex === v
                val isSelected = v in selection
                drawCircle(
                    color = when {
                        isSelected -> SelectedColor
                        isHovered -> HighlightColor
                        else -> VertexColor
                    },
                    radius = if (isHovered || isSelected) vertexRadius * 1.5f else vertexRadius,
                    center = toScreen(v.pos),
                )
            }
        }

        // Constraint icons – after all polygons, so nothing drawn later covers them
        val numbers = editor.scene.perpendicularNumbers()
        for (poly in editor.scene.polygons) {
            for (edge in poly.edges) {
                val constraints = editor.scene.constraintsOf(edge)
                if (constraints.isEmpty() || edge.length < 1e-9) continue // zero-length edge has no normal

                // Midpoint moved along the normal, to the outside of the polygon
                val mid = midpoint(edge.start.pos, edge.end.pos)
                val normal = edge.vector.perpendicular() / edge.length
                val candidate = mid + normal * ICON_OFFSET
                val center = if (candidate in poly) mid - normal * ICON_OFFSET else candidate

                drawLabel(textMeasurer, constraints.joinToString(" ") { it.shortLabel(numbers) }, toScreen(center))
            }
        }
    }
}

// Text centered at `center` on a light background, so it stays readable over fills and edges
private fun DrawScope.drawLabel(textMeasurer: TextMeasurer, text: String, center: Offset) {
    val layout = textMeasurer.measure(text, LabelStyle)
    val width = layout.size.width.toFloat()
    val height = layout.size.height.toFloat()
    val topLeft = center - Offset(width / 2, height / 2)
    val pad = 2.dp.toPx()

    drawRoundRect(
        LabelBackground,
        topLeft = topLeft - Offset(pad, pad),
        size = Size(width + 2 * pad, height + 2 * pad),
        cornerRadius = CornerRadius(2 * pad),
    )
    drawText(layout, topLeft = topLeft)
}
