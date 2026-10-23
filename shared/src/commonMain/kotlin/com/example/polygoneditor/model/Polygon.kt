package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.arcPoints
import com.example.polygoneditor.geometry.bezierPoints
import com.example.polygoneditor.geometry.bezierSegments
import com.example.polygoneditor.geometry.containsEvenOdd
import com.example.polygoneditor.geometry.midpoint
import com.example.polygoneditor.model.constraints.Smooth

// Closed polygon, represented by the list of Edge objects
class Polygon(points: List<Vec2>) {
    init {
        require(points.size >= MIN_VERTICES) { "Polygon has to have minimum of $MIN_VERTICES vertices" }
    }

    val edges: List<Edge>
        field: MutableList<Edge> = points.map(::Vertex)
            .let { vs ->
                (vs + vs.first())
                    .zipWithNext { a, b -> Edge(a, b) }
            }
            .toMutableList()

    init {
        checkRing()
    }

    val vertices: List<Vertex> get() = edges.map { it.start }

    val edgeCount: Int get() = edges.size

    val points: List<Point> get() = vertices + edges.flatMap { it.controlPoints }

    // Checks if edges make up full ring
    private fun checkRing() {
        for (i in edges.indices) {
            check(edges[i].end === edges[(i + 1) % edges.size].start) {
                "Edge ring broken at $i"
            }
        }
    }

    // Translates all points when moving the polygon
    fun translate(delta: Vec2) {
        points.forEach { it.pos += delta }
    }

    fun canRemoveVertex() = edgeCount > MIN_VERTICES

    // (incoming, outgoing) edges at vertex v
    fun edgesAt(v: Vertex): Pair<Edge, Edge> {
        val i = incomingIndex(v)
        return edges[i] to edges[(i + 1) % edges.size]
    }

    private fun incomingIndex(v: Vertex): Int {
        val i = edges.indexOfFirst { it.end === v }
        require(i >= 0) { "Vertex does not belong to this polygon" }
        return i
    }

    // Removes the vertex v, replaces the in and out edges with one edge
    // Low-level: does NOT remove constraints of the removed edges – use Scene.removeVertex instead
    fun removeVertex(v: Vertex): Boolean {
        if (!canRemoveVertex()) return false

        val (inEdge, outEdge) = edgesAt(v)

        val inIdx = edges.indexOf(inEdge)
        val outIdx = edges.indexOf(outEdge)

        edges[inIdx] = Edge(inEdge.start, outEdge.end)
        edges.removeAt(outIdx)

        checkRing()
        return true
    }

    // Inserts new vertex in the middle of the given edge
    // replaces current edge with two new ones
    // Low-level: does NOT remove constraints of the replaced edges – use Scene.insertVertex instead
    fun insertVertexOnEdge(e: Edge): Vertex {
        val i = edges.indexOf(e)
        require(i >= 0) { "Edge does not belong to this polygon" }

        // Add two new edges in place of edge i
        val v = Vertex(midpoint(e.start.pos, e.end.pos))
        edges[i] = Edge(e.start, v)
        edges.add(i + 1, Edge(v, e.end))

        checkRing()
        return v
    }

    // Checks if point p is inside this polygon
    operator fun contains(p: Vec2) = containsEvenOdd(outline(), p)

    // Returns outline of this polygon
    fun outline(): List<Vec2> = edges.flatMap { polylineOf(it).dropLast(1) }

    // Returns list of control points next to v
    fun controlPointsAt(v: Vertex): List<ControlPoint> {
        val (incoming, outgoing) = edgesAt(v)

        val a = (incoming.shape as? EdgeShape.Bezier)?.c2
        val b = (outgoing.shape as? EdgeShape.Bezier)?.c1

        return listOfNotNull(a, b)
    }

    // Continuity classes that make sense at v, given its two neighbouring edges:
    // two lines → G0 only, an arc → no C1, two arcs → G0 only (each would follow the other)
    fun allowedContinuities(v: Vertex): List<Continuity> {
        val shapes = edgesAt(v).toList().map { it.shape }
        val arcs = shapes.count { it is EdgeShape.Arc }
        val curves = shapes.count { it !is EdgeShape.Line }

        return when {
            curves == 0 || arcs == 2 -> listOf(Continuity.G0)
            arcs == 1 -> listOf(Continuity.G0, Continuity.G1)
            else -> Continuity.entries
        }
    }

    // Brings every vertex back to an allowed class (C1 -> G1 -> G0), an arc keeps G1 only at its start
    // Called after every change of the neighbours: shape change, vertex insert/remove
    fun normalizeContinuity() {
        for (v in vertices) {
            val allowed = allowedContinuities(v)
            if (v.continuity !in allowed) {
                v.continuity = if (Continuity.G1 in allowed) Continuity.G1 else Continuity.G0
            }
        }
        for (e in edges) {
            if (e.shape is EdgeShape.Arc && e.start.continuity == Continuity.G1 && e.end.continuity == Continuity.G1) {
                e.end.continuity = Continuity.G0
            }
        }
    }

    // Continuity constraints for the solver, built from vertex.continuity – never stored in scene.constraints
    // Only next to a Bézier: an arc follows its neighbour by itself when drawn, two lines have nothing to bend
    fun continuityConstraints(): List<Smooth> = vertices.mapNotNull { v ->
        val (incoming, outgoing) = edgesAt(v)
        val shapes = listOf(incoming.shape, outgoing.shape)
        when {
            v.continuity == Continuity.G0 -> null
            shapes.none { it is EdgeShape.Bezier } || shapes.any { it is EdgeShape.Arc } -> null
            else -> Smooth(v, incoming, outgoing, c1 = v.continuity == Continuity.C1)
        }
    }

    // Returns the polyline of a given edge represented by list of Vec2 points
    // for bezier it's a list of bezier points
    // for a line its start and end
    // for arc, it's a list of points at arc
    fun polylineOf(edge: Edge): List<Vec2> = when (val s = edge.shape) {
        is EdgeShape.Bezier -> bezierPoints(
            edge.start.pos, s.c1.pos, s.c2.pos, edge.end.pos,
            bezierSegments(edge.start.pos, s.c1.pos, s.c2.pos, edge.end.pos)
        )

        EdgeShape.Line -> listOf(edge.start.pos, edge.end.pos)
        EdgeShape.Arc -> {
            val a = edge.start
            val b = edge.end
            val incoming = edgesAt(a).first
            val outgoing = edgesAt(b).second
            val before = incoming.pointNextTo(a)?.pos
            val after = outgoing.pointNextTo(b)?.pos

            when {
                a.continuity != Continuity.G0 && before != null ->
                    arcPoints(a.pos, b.pos, startTangent = a.pos - before)

                b.continuity != Continuity.G0 && after != null ->
                    arcPoints(a.pos, b.pos, endTangent = after - b.pos)

                else ->
                    arcPoints(a.pos, b.pos)
            }
        }
    }

    companion object {
        const val MIN_VERTICES = 3
    }
}
