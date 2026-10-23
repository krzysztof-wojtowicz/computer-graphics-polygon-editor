package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.constraints.FixedLength
import com.example.polygoneditor.model.constraints.Perpendicular
import com.example.polygoneditor.solver.Locks
import com.example.polygoneditor.solver.Solver

// Starting scene – shows every requirement of the task
// Adding a polygon: polygon(...) with the vertices clockwise on screen (arcs bulge outwards),
// then shapes (bezier/arc) first, continuity after them (a shape change normalizes the continuity), constraints at any time
object DemoScene {
    fun create() = Scene().apply {
        // 1. Arcs: G0/G0 half circle on top, G1 arc tangent to the line before it
        val arcs = polygon(30 to 230, 210 to 230, 290 to 310, 210 to 390, 30 to 390)
        val (arcTop, _, arcRight, arcBottom, arcLeft) = arcs.edges
        arc(arcTop)
        arc(arcRight)
        continuity(arcs, arcs.vertices[2], Continuity.G1)
        length(arcBottom, 180.0)

        // 2. Kotlin logo: a square with a right-angled notch on the right
        //    Several constraints on one edge: left has a length and two right angles
        val kotlin = polygon(360 to 210, 560 to 210, 460 to 310, 560 to 410, 360 to 410)
        val (top, notchUpper, notchLower, bottom, left) = kotlin.edges
        length(top, 200.0)
        length(left, 200.0)
        perpendicular(top, left)
        perpendicular(bottom, left)
        perpendicular(notchUpper, notchLower)
        perpendicular(arcLeft, top) // between polygons

        // 3. Self-intersecting bow tie with Bézier sides: default C1 on the right one,
        //    explicit G1 and G0 (sharp corner) on the left one; perpendicular crossing edges,
        //    a length next to a C1 vertex (chain length – C1)
        val bowtie = polygon(630 to 220, 810 to 400, 810 to 220, 630 to 400)
        val (cross1, rightSide, cross2, leftSide) = bowtie.edges
        bezier(rightSide, 870 to 460, 870 to 160)
        bezier(leftSide, 585 to 445, 590 to 190)
        continuity(bowtie, bowtie.vertices[3], Continuity.G1)
        continuity(bowtie, bowtie.vertices[0], Continuity.G0)
        perpendicular(cross1, cross2)
        length(cross1, 255.0)

        // The starting scene satisfies everything – control points take their places for C1/G1
        check(Solver.solve(allConstraints(), Locks.None)) { "Demo scene constraints can't be satisfied" }
    }

    // ---- builder ----

    private fun Scene.length(edge: Edge, length: Double) = addConstraint(FixedLength(edge, length))

    private fun Scene.perpendicular(e1: Edge, e2: Edge) = addConstraint(Perpendicular(e1, e2))

    // Like Editor.toBezier, but with the control points given: c1 near start, c2 near end
    private fun Scene.bezier(edge: Edge, c1: Pair<Number, Number>, c2: Pair<Number, Number>) {
        changeShape(edge, EdgeShape.Bezier(ControlPoint(c1.toVec2()), ControlPoint(c2.toVec2())))
        polygons.first { edge in it.edges }.applyDefaultContinuity(edge)
    }

    private fun Scene.arc(edge: Edge) = changeShape(edge, EdgeShape.Arc)

    // Explicit class, as if chosen by the user – call after the shapes of both neighbouring edges are set
    private fun continuity(polygon: Polygon, v: Vertex, c: Continuity) {
        require(c in polygon.allowedContinuities(v)) { "${c.name} is not available at this vertex" }
        v.continuity = c
        v.continuityExplicit = true
        polygon.normalizeContinuity()
        check(v.continuity == c) { "Only one end of an arc can be G1" }
    }

    private fun Pair<Number, Number>.toVec2() = Vec2(first.toDouble(), second.toDouble())
}
