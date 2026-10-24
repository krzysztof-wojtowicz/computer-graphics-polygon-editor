package com.example.polygoneditor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

// Entry point for desktop application
fun main() = application {
    var showHelp by remember { mutableStateOf(false) }

    // It opens a new window and displays App content inside
    Window(
        onCloseRequest = ::exitApplication,
        title = "Polygon Editor",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center),
        ),
        // Window level: F1 works even when nothing has focus yet (e.g. right after the start)
        onPreviewKeyEvent = { e ->
            val isF1 = e.type == KeyEventType.KeyDown && e.key == Key.F1
            if (isF1) showHelp = true
            isF1
        },
    ) {
        window.minimumSize = Dimension(800, 600)

        App(showHelp, onShowHelpChange = { showHelp = it })
    }
}
