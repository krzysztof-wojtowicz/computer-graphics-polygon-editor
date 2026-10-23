package com.example.polygoneditor.solver

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.midpoint
import com.example.polygoneditor.geometry.perpendicular
import com.example.polygoneditor.model.ControlPoint
import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.EdgeShape
import com.example.polygoneditor.model.Point
import com.example.polygoneditor.model.constraints.Constraint
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.model.constraints.Smooth
import kotlin.math.abs
import kotlin.math.max

// How far is the constraint from being satisfied
fun Constraint.error(): Double = when (this) {
    is FixedLength -> abs(edge.length - length)
    is Perpendicular -> {
        val longer = max(e1.length, e2.length)
        if (longer < 1e-9) 0.0 else abs(e1.vector dot e2.vector) / longer // both zero-length: 0/0 = NaN
    }
    // How far the point after the vertex is from where it should be; no direction to follow -> nothing to fix
    is Smooth -> targetFor(after)?.distanceTo(after.pos) ?: 0.0
}

// Moves the constraint's points so that error() == 0, respects locks
fun Constraint.project(locks: Locks) {
    when (this) {
        is FixedLength -> projectLength(locks)
        is Perpendicular -> projectPerpendicular(locks)
        is Smooth -> projectSmooth(locks)
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

// ---- continuity G1 / C1 ----
// P – point before the vertex V (on the incoming edge), C – point after it (on the outgoing edge)
// u = V − P – direction of arrival, w = C − V – direction of leaving
// G1: w has the direction of u; C1: w = k·u

// Smooth is generated only next to a Bézier and never next to an arc, so both points exist
private val Smooth.before: Point get() = incoming.pointNextTo(vertex)!!
private val Smooth.after: Point get() = outgoing.pointNextTo(vertex)!!

// Derivative at the end: Bézier 3·(V − c), line (t in [0, 1]) just its vector – so C1 means w·wOut = u·wIn
private fun weight(e: Edge) = if (e.shape is EdgeShape.Bezier) 3.0 else 1.0

private val Smooth.ratio: Double get() = weight(incoming) / weight(outgoing)

// Where `moving` (before or after) has to stand so the constraint holds, the other point stays
// G1 rotates it around V keeping its distance; null when the other side has no direction (zero length)
private fun Smooth.targetFor(moving: Point): Vec2? {
    val v = vertex.pos
    val u = v - before.pos
    val w = after.pos - v

    return if (moving === after) {
        when {
            c1 -> v + u * ratio
            u.length < 1e-9 -> null
            else -> v + u / u.length * w.length
        }
    } else {
        when {
            c1 -> v - w / ratio
            w.length < 1e-9 -> null
            else -> v - w / w.length * u.length
        }
    }
}

// Moves P or C, never V (a dragged vertex has to stay under the cursor)
// A control point goes first: otherwise dragging a vertex next to a line would move the line's far end
// instead of turning the handle; among the same kind – the less locked one, on a tie C
// Both locked -> nothing moves, the error stays and solve fails (fallback)
private fun Smooth.projectSmooth(locks: Locks) {
    val moving = listOf(after, before)
        .filter { locks.levelOf(it) != LockLevel.Locked }
        .minWithOrNull(compareBy({ it !is ControlPoint }, { locks.levelOf(it) })) ?: return

    targetFor(moving)?.let { moving.pos = it }
}
