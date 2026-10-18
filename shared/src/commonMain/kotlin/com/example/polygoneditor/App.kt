package com.example.polygoneditor

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.polygoneditor.editor.Editor
import com.example.polygoneditor.model.DemoScene
import com.example.polygoneditor.ui.SceneCanvas
import com.example.polygoneditor.ui.SidePanel

@Composable
fun App() {
    val editor = remember { Editor(DemoScene.create()) }

    MaterialTheme {
        Row(Modifier.fillMaxSize()) {
            SceneCanvas(editor, Modifier.weight(1f).fillMaxHeight())
            SidePanel(editor, Modifier.width(280.dp))
        }
    }
}

