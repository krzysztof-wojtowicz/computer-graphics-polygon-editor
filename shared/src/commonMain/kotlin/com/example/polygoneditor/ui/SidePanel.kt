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
import androidx.compose.runtime.Composable
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
import kotlin.math.round

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

    val edge = s.edge
    Header("Edge")
    Text("Length: ${edge.length.fmt()}")
    Text("Shape: ${edge.shape.label()}")

    Gap()
    Text("Constraints", style = MaterialTheme.typography.titleSmall)
    val constraints = editor.scene.constraintsOf(edge)
    if (constraints.isEmpty()) {
        Text("none", color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        constraints.forEach { Text("• ${it.label()}") }
    }

    // Future actions – enabled together with the solver and curves
    Gap()
    PlaceholderButton("Set length…")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        PlaceholderButton("Line", Modifier.weight(1f))
        PlaceholderButton("Bézier", Modifier.weight(1f))
        PlaceholderButton("Arc", Modifier.weight(1f))
    }
}

@Composable
private fun TwoEdgesSection(editor: Editor, s: Selection.TwoEdges) {
    editor.version // lengths change while dragging – redraw on every model change

    Header("Two edges")
    Text("First length: ${s.first.length.fmt()}")
    Text("Second length: ${s.second.length.fmt()}")

    Gap()
    PlaceholderButton("Make perpendicular")
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

// One decimal place; String.format is JVM-only, so it can't be used in commonMain
private fun Double.fmt() = (round(this * 10) / 10).toString()

private fun EdgeShape.label() = when (this) {
    EdgeShape.Line -> "Line"
    is EdgeShape.Bezier -> "Bézier"
    EdgeShape.Arc -> "Arc"
}

private fun Constraint.label() = when (this) {
    is FixedLength -> "Length ${length.fmt()}"
    is Perpendicular -> "Perpendicular"
}
