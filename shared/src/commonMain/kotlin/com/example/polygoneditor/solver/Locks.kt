package com.example.polygoneditor.solver

import com.example.polygoneditor.model.Point

enum class LockLevel { Free, Protected, Locked }

class Locks(
    private val locked: Set<Point> = emptySet(),
    private val protected: Set<Point> = emptySet()
) {
    fun levelOf(p: Point): LockLevel = when {
        p in locked -> LockLevel.Locked
        p in protected -> LockLevel.Protected
        else -> LockLevel.Free
    }
    fun shares(a: Point, b: Point): Pair<Double, Double> = when {
        a in locked && b in locked -> 0.0 to 0.0
        levelOf(a) < levelOf(b) -> 1.0 to 0.0
        levelOf(a) > levelOf(b) -> 0.0 to 1.0
        else -> 0.5 to 0.5
    }

    companion object {
        val None = Locks()
    }
}