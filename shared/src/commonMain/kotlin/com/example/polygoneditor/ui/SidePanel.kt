package com.example.polygoneditor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.editor.LineAlgorithm
import com.example.polygoneditor.editor.Selection
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular

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
    Text("Continuity: ${v.continuity.name}")

    Gap()
    Button(onClick = editor::deleteSelected, modifier = Modifier.fillMaxWidth()) {
        Text("Delete vertex")
    }
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
    Button(onClick = { showLengthDialog = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Set length…")
    }
    // Future actions – enabled together with curves
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PlaceholderButton("Line", Modifier.weight(1f))
        PlaceholderButton("Bézier", Modifier.weight(1f))
        PlaceholderButton("Arc", Modifier.weight(1f))
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
    for (c in constraints) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("• ${c.label()}", Modifier.weight(1f)) // pushes the button to the right edge
            TextButton(onClick = { editor.removeConstraint(c) }) { Text("✕") }
        }
    }
}

// Always visible, independent of the selection
@Composable
private fun LineAlgorithmSection(editor: Editor) {
    Text("Line drawing", style = MaterialTheme.typography.titleSmall)
    for (algorithm in LineAlgorithm.entries) {
        val selected = editor.lineAlgorithm == algorithm
        // The whole row is clickable, not only the circle
        Row(
            Modifier
                .fillMaxWidth()
                .selectable(
                    selected = selected,
                    onClick = { editor.lineAlgorithm = algorithm },
                    role = Role.RadioButton,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = null) // the row handles the click
            Text(algorithm.name, Modifier.padding(start = 8.dp))
        }
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

// A disabled button marking where an action will be added later
@Composable
private fun PlaceholderButton(text: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedButton(onClick = {}, enabled = false, modifier = modifier) {
        Text(text)
    }
}

// ---- text helpers ----

private fun EdgeShape.label() = when (this) {
    EdgeShape.Line -> "Line"
    is EdgeShape.Bezier -> "Bézier"
    EdgeShape.Arc -> "Arc"
}

private fun Constraint.label() = when (this) {
    is FixedLength -> "Length ${length.fmt()}"
    is Perpendicular -> "Perpendicular"
}
