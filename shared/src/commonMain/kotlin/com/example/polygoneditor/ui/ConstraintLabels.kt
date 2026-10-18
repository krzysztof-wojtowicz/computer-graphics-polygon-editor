package com.example.polygoneditor.ui

import com.example.polygoneditor.model.Scene
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular

// Number of each perpendicular pair (1, 2, …) – both edges of a pair ask about the same object, so they get the same number
internal fun Scene.perpendicularNumbers(): Map<Perpendicular, Int> =
    constraints.filterIsInstance<Perpendicular>()
        .withIndex()
        .associate { (i, c) -> c to i + 1 }

// Short label for the icon on the canvas: "L 120.0", "⊥1"
internal fun Constraint.shortLabel(numbers: Map<Perpendicular, Int>) = when (this) {
    is FixedLength -> "L ${length.fmt()}"
    is Perpendicular -> "⊥${numbers[this]}"
}

// Label for the side panel – the same number as on the canvas
internal fun Constraint.label(numbers: Map<Perpendicular, Int>) = when (this) {
    is FixedLength -> "Length ${length.fmt()}"
    is Perpendicular -> "Perpendicular ⊥${numbers[this]}"
}
