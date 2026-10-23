package com.example.polygoneditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.LineAlgorithm
import com.example.polygoneditor.editor.Selection
import com.example.polygoneditor.model.Continuity
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength

@Composable
fun SidePanel(editor: Editor, modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (val s = editor.selection) {
            Selection.None -> NothingSelected()
            is Selection.OnVertex -> VertexSection(editor, s)
            is Selection.OnEdge -> EdgeSection(editor, s)
            is Selection.TwoEdges -> TwoEdgesSection(editor, s)
            is Selection.OnControlPoint -> ControlPointSection(editor, s)
        }
        Spacer(Modifier.weight(1f))
        LineAlgorithmSection(editor)
        editor.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun NothingSelected() {
    Header("Nothing selected")
    Text("Click – select a vertex or an edge")
    Text("Drag – move a vertex, an edge or the whole polygon")
    Text("Shift + click – select a second edge")
    Text("Double click on an edge – insert a vertex")
    Text("Delete – remove the selected vertex")
}

@Composable
private fun VertexSection(editor: Editor, s: Selection.OnVertex) {
    editor.version // position changes while dragging – redraw on every model change

    val v = s.vertex
    Header("Vertex ${s.polygon.vertices.indexOf(v) + 1} of ${s.polygon.vertices.size}")
    Text("Position: (${v.pos.x.fmt()}, ${v.pos.y.fmt()})")

    Gap()
    ContinuitySection(editor, s)

    Gap()
    Button(onClick = editor::deleteSelected, modifier = Modifier.fillMaxWidth()) {
        Text("Delete vertex")
    }
}

@Composable
private fun ControlPointSection(editor: Editor, s: Selection.OnControlPoint) {
    editor.version // position changes while dragging – redraw on every model change

    val c = s.point
    val shape = s.edge.shape as? EdgeShape.Bezier
    Header("Control point")
    Text("Edge ${s.polygon.edges.indexOf(s.edge) + 1}, ${if (c === shape?.c1) "near start" else "near end"}")
    Text("Position: (${c.pos.x.fmt()}, ${c.pos.y.fmt()})")
    Text("Drag to bend the curve", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun EdgeSection(editor: Editor, s: Selection.OnEdge) {
    editor.version // length changes while dragging – redraw on every model change

    // View-only state: forgotten when the selection changes (EdgeSection leaves the composition)
    var showLengthDialog by remember { mutableStateOf(false) }

    val edge = s.edge
    val constraints = editor.scene.constraintsOf(edge)
    Header("Edge")
    Text("Length: ${edge.length.fmt()}")
    Text("Shape: ${edge.shape.label()}")

    Gap()
    ConstraintList(editor, constraints)

    Gap()
    Button(
        onClick = { showLengthDialog = true },
        enabled = edge.shape is EdgeShape.Line, // constraints apply only to lines
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Set length…")
    }
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        ShapeButton("Line", edge.shape is EdgeShape.Line, { editor.toLine(edge) }, Modifier.weight(1f))
        ShapeButton("Bézier", edge.shape is EdgeShape.Bezier, { editor.toBezier(edge) }, Modifier.weight(1f))
        ShapeButton("Arc", edge.shape is EdgeShape.Arc, { editor.toArc(edge) }, Modifier.weight(1f))
    }

    // No show()/hide() in Compose: the dialog exists only while the condition holds
    if (showLengthDialog) {
        LengthDialog(
            // the length already set, otherwise the current one
            initial = constraints.filterIsInstance<FixedLength>().firstOrNull()?.length ?: edge.length,
            onConfirm = {
                editor.setLength(edge, it)
                showLengthDialog = false
            },
            onDismiss = { showLengthDialog = false },
        )
    }
}

@Composable
private fun TwoEdgesSection(editor: Editor, s: Selection.TwoEdges) {
    editor.version // lengths change while dragging – redraw on every model change

    Header("Two edges")
    Text("First length: ${s.first.length.fmt()}")
    Text("Second length: ${s.second.length.fmt()}")

    Gap()
    // A constraint between the two edges would appear twice – distinct() keeps one
    ConstraintList(editor, (editor.scene.constraintsOf(s.first) + editor.scene.constraintsOf(s.second)).distinct())

    Gap()
    // Order matters: the second edge is the one that rotates
    Button(onClick = { editor.makePerpendicular(s.first, s.second) }, modifier = Modifier.fillMaxWidth()) {
        Text("Make perpendicular")
    }
}

@Composable
private fun ConstraintList(editor: Editor, constraints: List<Constraint>) {
    Text("Constraints", style = MaterialTheme.typography.titleSmall)
    if (constraints.isEmpty()) {
        Text("none", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val numbers = editor.scene.perpendicularNumbers()
    for (c in constraints) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• ${c.label(numbers)}", Modifier.weight(1f)) // pushes the button to the right edge
            TextButton(onClick = { editor.removeConstraint(c) }) { Text("✕") }
        }
    }
}

// G0 / G1 / C1 radios, the ones the neighbouring edges don't allow are disabled
@Composable
private fun ContinuitySection(editor: Editor, s: Selection.OnVertex) {
    val v = s.vertex
    val allowed = s.polygon.allowedContinuities(v)
    val nextToArc = s.polygon.edgesAt(v).toList().any { it.shape is EdgeShape.Arc }

    Text("Continuity", style = MaterialTheme.typography.titleSmall)
    for (c in Continuity.entries) {
        val selected = v.continuity == c
        // A class the editor set by itself (C1 after turning an edge into a curve) – not chosen by the user
        val text = if (selected && c != Continuity.G0 && !v.continuityExplicit) "${c.name} (default)" else c.name
        RadioRow(text, selected, c in allowed, { editor.setContinuity(s.polygon, v, c) })
    }

    // Why some options are disabled
    val hint = when {
        allowed.size == Continuity.entries.size -> null
        allowed.size == 1 && nextToArc -> "Between two arcs only G0"
        allowed.size == 1 -> "G1 and C1 only next to a curve"
        else -> "An arc allows only G0 and G1"
    }
    hint?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

// Always visible, independent of the selection
@Composable
private fun LineAlgorithmSection(editor: Editor) {
    Text("Line drawing", style = MaterialTheme.typography.titleSmall)
    for (algorithm in LineAlgorithm.entries) {
        RadioRow(algorithm.name, editor.lineAlgorithm == algorithm, onClick = { editor.lineAlgorithm = algorithm })
    }
}

// ---- small building blocks ----

@Composable
private fun Header(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun Gap() {
    Spacer(Modifier.height(6.dp))
}

// The whole row is clickable, not only the circle
@Composable
private fun RadioRow(text: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled) // the row handles the click
        Text(
            text,
            Modifier.padding(start = 8.dp),
            color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
        )
    }
}

// Three buttons in a row are narrow – the default 24 dp side padding would wrap "Bézier"
private val NarrowPadding = PaddingValues(horizontal = 4.dp)

// The current shape is a filled button, the others outlined
@Composable
private fun ShapeButton(text: String, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    if (current) {
        Button(onClick = onClick, modifier = modifier, contentPadding = NarrowPadding) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier, contentPadding = NarrowPadding) { Text(text) }
    }
}

// ---- text helpers ----

private fun EdgeShape.label() = when (this) {
    EdgeShape.Line -> "Line"
    is EdgeShape.Bezier -> "Bézier"
    EdgeShape.Arc -> "Arc"
}
