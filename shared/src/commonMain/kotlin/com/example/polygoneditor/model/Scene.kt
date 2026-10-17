package com.example.polygoneditor.model

import com.example.polygoneditor.geometry.Vec2
import com.example.polygoneditor.model.constraints.Constraint

typealias Snapshot = Map<Point, Vec2>

class Scene {
    val polygons: List<Polygon>
        field = mutableListOf<Polygon>()

    val constraints: List<Constraint>
        field = mutableListOf<Constraint>()

    // Builder for polygon objects
    fun polygon(vararg points: Pair<Number, Number>): Polygon =
        Polygon(points.map { (x, y) -> Vec2(x.toDouble(), y.toDouble()) })
            .also { polygons += it }

    // Returns list of all points on scene
    fun allPoints(): List<Point> = polygons.flatMap { it.points }

    // Create map with values: vertex/control point -> position
    fun snapshot(): Snapshot = allPoints().associateWith { it.pos }

    // Restores the points positions from given snapshot s
    fun restore(s: Snapshot) = s.forEach { (point, pos) -> point.pos = pos }

    // Adds constraint to the list
    fun addConstraint(c: Constraint) {
        require(c.edges.all { e -> polygons.any { p -> e in p.edges } }) {
            "Constraint refers to an edge that is not in the scene"
        }

        constraints += c
    }

    // Removes constraint from the list
    fun removeConstraint(c: Constraint): Boolean = constraints.remove(c)

    // Returns list of constraints for given edge
    fun constraintsOf(edge: Edge): List<Constraint> = constraints.filter { c -> edge in c.edges }

    // Removes the constraints for given edges
    private fun dropConstraintsOf(edges: List<Edge>): Boolean =
        constraints.removeAll { c -> c.edges.any { it in edges } }

    // Inserts vertex and takes care of constraints
    fun insertVertex(polygon: Polygon, edge: Edge): Vertex {
        val v = polygon.insertVertexOnEdge(edge)

        dropConstraintsOf(listOf(edge))

        return v
    }

    // Removes given vertex from polygon
    fun removeVertex(polygon: Polygon, v: Vertex): Boolean {
        val (incoming, outgoing) = polygon.edgesAt(v)

        if (!polygon.removeVertex(v)) return false

        dropConstraintsOf(listOf(incoming, outgoing))
        return true
    }
}
