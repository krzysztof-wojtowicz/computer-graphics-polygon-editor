package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.geometry.containsEvenOdd
import com.example.polygoneditor.geometry.midpoint

// Closed polygon, the edge i represents connection between vertex i and i+1 mod n
class Polygon(points: List<Vec2>) {
    init {
        require(points.size >= MIN_VERTICES) { "Polygon has to have minimum of $MIN_VERTICES vertices" }
    }

    private val _vertices: MutableList<Vertex> = points.map(::Vertex).toMutableList()

    val vertices: List<Vertex> get() = _vertices;

    val edgeCount: Int get() = _vertices.size

    fun edge(i: Int): Pair<Vertex, Vertex> = _vertices[i] to _vertices[(i + 1) % _vertices.size]

    fun translate(delta: Vec2) {
        _vertices.forEach { it.pos += delta }
    }

    fun canRemoveVertex() = _vertices.size > MIN_VERTICES

    fun removeVertex(v: Vertex): Boolean = canRemoveVertex() && _vertices.remove(v)

    // Inserts new vertex in the middle of the [edgeIndex] edge
    fun insertVertexOnEdge(edgeIndex: Int): Vertex {
        val (a, b) = edge(edgeIndex)
        val v = Vertex(midpoint(a.pos, b.pos))
        _vertices.add(edgeIndex + 1, v)
        return v
    }

    // Checks if point p is inside this polygon
    operator fun contains(p: Vec2) = containsEvenOdd(_vertices.map { it.pos }, p)

    companion object {
        const val MIN_VERTICES = 3
    }
}
