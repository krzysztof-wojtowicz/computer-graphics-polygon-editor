package com.example.polygoneditor

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

// Entry point for desktop application
fun main() = application {
    // It opens a new window and displays App content inside
    Window(
        onCloseRequest = ::exitApplication,
        title = "Polygon Editor",
    ) {
        App()
    }
}