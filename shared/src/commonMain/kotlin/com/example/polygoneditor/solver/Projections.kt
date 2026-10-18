package com.example.polygoneditor.solver

import com.example.polygoneditor.geometry.midpoint
import com.example.polygoneditor.geometry.perpendicular
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import kotlin.math.abs
import kotlin.math.max

// How far is the constraint from being satisfied
fun Constraint.error(): Double = when (this) {
    is FixedLength -> abs(edge.length - length)
    is Perpendicular -> {
        val longer = max(e1.length, e2.length)
        if (longer < 1e-9) 0.0 else abs(e1.vector dot e2.vector) / longer // both zero-length: 0/0 = NaN
    }
}

// Moves the constraint's points so that error() == 0, respects locks
fun Constraint.project(locks: Locks) {
    when (this) {
        is FixedLength -> projectLength(locks)
        is Perpendicular -> projectPerpendicular(locks)
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

// Chooses which edge has to rotate to make it perpendicular
private fun Perpendicular.projectPerpendicular(locks: Locks) {
    if (e1.length < 1e-9 || e2.length < 1e-9) return // zero-length edge has no direction

    // The less locked edge rotates, on a tie e2 (the one selected second)
    val (moving, reference) = if (locks.score(e1) < locks.score(e2)) e1 to e2 else e2 to e1

    // Chosen edge fully locked -> try the other one; both fully locked -> error stays, solve fails
    if (!rotatePerpendicular(moving, reference, locks)) {
        rotatePerpendicular(reference, moving, locks)
    }
}

// How locked the edge is: sum of its ends' levels (Free 0, Protected 1, Locked 2)
private fun Locks.score(e: Edge) = levelOf(e.start).ordinal + levelOf(e.end).ordinal

// Rotates edge to the perpendicular direction closer to its current one, keeping its length.
// Returns false if both ends are locked and the edge can't rotate.
private fun rotatePerpendicular(moving: Edge, reference: Edge, locks: Locks): Boolean {
    val startLevel = locks.levelOf(moving.start)
    val endLevel = locks.levelOf(moving.end)
    if (startLevel == LockLevel.Locked && endLevel == LockLevel.Locked) return false

    val w = moving.vector
    val n = (reference.vector / reference.length).perpendicular()
    // Of the two perpendicular directions (n and -n) take the one making the smaller angle with w
    val direction = if ((w dot n) >= 0) n else n * -1.0
    val newVector = direction * w.length // perpendicular, same length

    // Pivot: the more locked end stays; equal levels -> rotate around the midpoint
    when {
        startLevel > endLevel -> moving.end.pos = moving.start.pos + newVector
        startLevel < endLevel -> moving.start.pos = moving.end.pos - newVector
        else -> {
            val m = midpoint(moving.start.pos, moving.end.pos)
            moving.start.pos = m - newVector / 2.0
            moving.end.pos = m + newVector / 2.0
        }
    }
    return true
}