package com.example.polygoneditor.solver

import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import kotlin.math.abs

// How far is the constraint from being satisfied
fun Constraint.error(): Double = when (this) {
    is FixedLength -> abs(edge.length - length)
    is Perpendicular -> TODO()
}

// Moves the constraint's points so that error() == 0, respects locks
fun Constraint.project(locks: Locks) {
    when (this) {
        is FixedLength -> projectLength(locks)
        is Perpendicular -> TODO()
    }
}

// Moves the ends along the edge until its length equals `length`
private fun FixedLength.projectLength(locks: Locks) {
    val a = edge.start
    val b = edge.end
    val v = b.pos - a.pos
    val len = v.length
    if (len < 1e-9) return // zero-length edge has no direction – dividing by len would give NaN

    // Vector along the edge, as long as the error: points a → b when the edge is too long, b → a when too short
    val correction = v * ((len - length) / len)

    val (wa, wb) = locks.shares(a, b)
    a.pos += correction * wa // too long: a moves towards b
    b.pos -= correction * wb //           b moves towards a
}