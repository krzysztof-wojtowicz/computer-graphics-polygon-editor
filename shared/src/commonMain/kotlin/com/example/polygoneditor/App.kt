package com.example.polygoneditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.model.DemoScene
import com.example.polygoneditor.ui.SceneCanvas

@Composable
fun App() {
    val editor = remember { Editor(DemoScene.create()) }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {
            SceneCanvas(editor, Modifier.weight(1f).fillMaxHeight())
            SidePanel(editor)
        }
    }
}

@Composable
private fun SidePanel(editor: Editor) {
    Column(
        Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        editor.message?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
