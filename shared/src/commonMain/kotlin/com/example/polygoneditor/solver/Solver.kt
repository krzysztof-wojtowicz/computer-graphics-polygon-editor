package com.example.polygoneditor.solver

import com.example.polygoneditor.model.constraints.Constraint

object Solver {
    const val MAX_ITERATIONS = 200
    const val EPS = 1e-3

    fun solve(constraints: List<Constraint>, locks: Locks): Boolean {
        // Tries to move all required scene elements so the constraints are met
        // tries this MAX_ITERATIONS times, each iteration closer to solution (error -> 0)
        repeat(MAX_ITERATIONS) {
            // If errors for all constraints < EPS, it's okay
            if (constraints.all { it.error() < EPS }) return true

            constraints.forEach { it.project(locks) }
        }

        return constraints.all { it.error() < EPS }
    }
}