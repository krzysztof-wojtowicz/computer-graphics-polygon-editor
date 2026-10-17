package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2

interface Point {
    var pos: Vec2
}

class Vertex(override var pos: Vec2) : Point {
    var continuity = Continuity.G0
    var continuityExplicit = false
}

class ControlPoint(override var pos: Vec2) : Point

enum class Continuity { G0, G1, C1 }
