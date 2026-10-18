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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.Hit
import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.render.BresenhamPainter
import com.example.polygoneditor.render.Painter

private val FillColor = Color(0x223B82F6)
private val EdgeColor = Color(0xFF1F2937)
private val VertexColor = Color(0xFFDC2626)
private val HighlightColor = Color(0xFFF59E0B)
private val SelectedColor = Color(0xFF2563EB)

// Max distance (in dp) between the two presses of a double click
private const val DOUBLE_CLICK_SLOP = 4.0

private fun Density.toScene(o: Offset) = Vec2(o.x.toDouble() / density, o.y.toDouble() / density)
private fun Density.toScreen(v: Vec2) = Offset((v.x * density).toFloat(), (v.y * density).toFloat())

@Composable
fun SceneCanvas(editor: Editor, modifier: Modifier = Modifier) {
    // remember: SceneCanvas runs again on every recomposition, the requester has to survive it
    val focusRequester = remember { FocusRequester() }

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
        val stroke = 2.dp.toPx()
        val vertexRadius = 5.dp.toPx()
        val painter: Painter = BresenhamPainter(this)

        // Draw each polygon
        for (poly in editor.scene.polygons) {
            val pts = poly.vertices.map { toScreen(it.pos) }

            // Draw fill of the polygon
            val path = Path().apply {
                fillType = PathFillType.EvenOdd
                moveTo(pts[0].x, pts[0].y)
                pts.drop(1).forEach { lineTo(it.x, it.y) }
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
                painter.line(
                    toScreen(edge.start.pos),
                    toScreen(edge.end.pos),
                    color = when {
                        isSelected -> SelectedColor
                        isHovered -> HighlightColor
                        else -> EdgeColor
                    }
                )
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
    }
}
