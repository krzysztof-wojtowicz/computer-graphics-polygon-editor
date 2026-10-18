package com.example.polygoneditor.model

// Starting scene
object DemoScene {
    fun create() = Scene().apply {
        polygon(80 to 80, 320 to 80, 380 to 260, 40 to 240)
        polygon(100 to 150, 100 to 200, 150 to 150, 200 to 200)
    }
}
