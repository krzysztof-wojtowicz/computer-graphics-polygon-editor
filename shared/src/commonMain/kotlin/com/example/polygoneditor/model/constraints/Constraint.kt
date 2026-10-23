package com.example.polygoneditor.model.constraints

import com.example.polygoneditor.model.Edge
import com.example.polygoneditor.model.Vertex

sealed interface Constraint {
    val edges: List<Edge>
}

class FixedLength(val edge: Edge, val length: Double) : Constraint {
    init {
        require(length > 0) { "The length has to be bigger than 0" }
    }

    override val edges get() = listOf(edge)
}

class Perpendicular(val e1: Edge, val e2: Edge) : Constraint {
    init {
        require(e1 !== e2) { "Edge can't be perpendicular to itself" }
    }

    override val edges get() = listOf(e1, e2)
}

// G1 (c1 = false) or C1 (c1 = true) at vertex between incoming and outgoing edge
// Derived from vertex.continuity at every solve (Polygon.continuityConstraints) – never stored in scene.constraints
class Smooth(val vertex: Vertex, val incoming: Edge, val outgoing: Edge, val c1: Boolean) : Constraint {
    override val edges get() = listOf(incoming, outgoing)
}
