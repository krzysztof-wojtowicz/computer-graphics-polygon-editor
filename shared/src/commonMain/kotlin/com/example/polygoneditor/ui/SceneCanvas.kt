package com.example.polygoneditor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.Hit
import com.example.polygoneditor.geometry.Vec2

private val FillColor = Color(0x223B82F6)
private val EdgeColor = Color(0xFF1F2937)
private val VertexColor = Color(0xFFDC2626)
private val HighlightColor = Color(0xFFF59E0B)

private val SelectedColor = Color(0xFF2563EB)

private fun Density.toScene(o: Offset) = Vec2(o.x.toDouble() / density, o.y.toDouble() / density)
private fun Density.toScreen(v: Vec2) = Offset((v.x * density).toFloat(), (v.y * density).toFloat())

@Composable
fun SceneCanvas(editor: Editor, modifier: Modifier = Modifier) {
    Canvas(
        modifier
            .clipToBounds()
            .background(Color.White)
            .pointerInput(editor) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        val pos = toScene(event.changes.first().position)
                        when (event.type) {
                            PointerEventType.Press -> when {
                                event.buttons.isPrimaryPressed -> editor.onPrimaryPress(pos)
                                event.buttons.isSecondaryPressed -> editor.onSecondaryPress(pos)
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
                drawLine(
                    color = when {
                        isSelected -> SelectedColor
                        isHovered -> HighlightColor
                        else -> EdgeColor
                    },
                    toScreen(edge.start.pos), toScreen(edge.end.pos),
                    strokeWidth = if (isHovered || isSelected) stroke * 2 else stroke,
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
