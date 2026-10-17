package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.containsEvenOdd
import com.example.polygoneditor.geometry.midpoint

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
    operator fun contains(p: Vec2) = containsEvenOdd(vertices.map { it.pos }, p)

    companion object {
        const val MIN_VERTICES = 3
    }
}
