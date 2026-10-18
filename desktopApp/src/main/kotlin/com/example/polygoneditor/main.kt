package com.example.polygoneditor

import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Dimension

// Entry point for desktop application
fun main() = application {
    // It opens a new window and displays App content inside
    Window(
        onCloseRequest = ::exitApplication,
        title = "Polygon Editor",
        state = rememberWindowState(
            width = 1200.dp,
            height = 800.dp,
            position = WindowPosition.Aligned(Alignment.Center),
        ),
    ) {
        window.minimumSize = Dimension(800, 600)

        App()
    }
}
