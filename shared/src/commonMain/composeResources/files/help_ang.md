# Polygon Editor – documentation

Polygon editor (assignment 1). Variant: edge **perpendicularity**, default continuity **C1**.

The documentation has four parts: user guide, assumptions, data model and algorithms.

## 1. User guide

### Mouse

| Action | Effect |
|---|---|
| LMB on a vertex / control point / edge | selects the element (details in the panel on the right) |
| LMB on empty space | clears the selection |
| Dragging a vertex | the vertex follows the cursor, the rest of the scene adjusts to the constraints |
| Dragging a control point | changes the shape of the Bézier curve; the vertices of that segment stay put (unless a constraint requires otherwise) |
| Dragging an edge | the edge (with its control points) follows the cursor |
| Dragging the inside of a polygon | moves the whole polygon |
| Shift + LMB on an edge | adds a second edge to the selection (for the perpendicularity constraint) |
| Double click on an edge | inserts a vertex in the middle of the edge |

The element under the cursor is highlighted in orange, the selected one in blue. Hit-testing order: vertex, control point, edge, interior.

### Keyboard

| Key | Effect |
|---|---|
| Delete / Backspace | deletes the selected vertex |
| F1 | opens this documentation |
| Esc | closes a dialog |

### Side panel

- **Nothing selected** – a short guide.
- **Edge** – length, shape, list of constraints (✕ removes a constraint), a **Set length…** button (dialog with the current length), shape buttons **Line / Bézier / Arc**.
- **Two edges** (Shift + LMB) – lengths, the common list of constraints, a **Make perpendicular** button.
- **Vertex** – position, continuity **G0 / G1 / C1** (unavailable classes are greyed out, with a hint why below), **Delete vertex**. A class assigned automatically is marked “(default)”.
- **Control point** – the edge it belongs to and its position.
- Always visible: line drawing algorithm choice **Library / Bresenham**, the **Help (F1)** button and an error message (e.g. a conflicting constraint).

### Scene markers

- `L 120.0` – fixed edge length, `⊥1`, `⊥2`… – perpendicularity (the same number on both edges of a pair). Icons are placed next to the edge midpoint, on the outer side.
- `G1`, `C1` next to a vertex – continuity class (G0 is not marked).
- A Bézier curve has a dashed control polygon and square control points.

### Demo scene

On startup three polygons are visible:

- **arcs** (left) – a G0/G0 arc (semicircle) and a G1 arc tangent to the neighbouring segment, the left edge perpendicular to the top edge of the logo (a constraint between polygons),
- **Kotlin logo** (middle) – several constraints on one edge (left: length and two perpendicularities), perpendicular arms of the notch,
- **“bow tie”** (right) – a self-intersecting polygon, perpendicular crossing edges, Bézier sides with default C1 as well as explicit G1 and G0.

## 2. Assumptions

### Constraints

- Constraints (fixed length, perpendicularity) can only be applied to **line segments**. Changing an edge's shape to Bézier or arc removes its constraints.
- An edge can have several constraints, but only one fixed length – a new length replaces the previous one. The same pair of edges cannot be marked perpendicular twice.
- Perpendicularity can connect edges of **different polygons** (constraints belong to the scene, not to a polygon).
- A constraint that cannot be satisfied together with the existing ones is **rejected** – the scene returns to the state before the attempt and a message appears in the panel.
- If a drag cannot be reconciled with the constraints, **the whole polygon is moved** (fallback). A translation changes neither lengths nor directions, so all constraints remain satisfied.

### Inserting and deleting vertices

- Inserting a vertex splits the edge into two **line segments**; the constraints of the split edge are removed.
- Deleting a vertex replaces the two adjacent edges with a single **line segment**; the constraints of both edges are removed.
- A polygon always has at least 3 vertices.

### Continuity

- G1 and C1 only make sense next to a curve: between two line segments only G0 is available.
- Turning an edge into a Bézier curve sets **default C1** at its ends, unless the user has already chosen a class at that vertex.
- After every change of neighbourhood (shape, insertion, deletion) disallowed classes are downgraded: C1 → G1 → G0.

### Arcs

- A G0/G0 arc is a **semicircle** centred at the edge midpoint with a radius equal to half its length. It bulges to the left of the start → end direction, i.e. outwards for a polygon whose vertices are given clockwise on screen.
- An arc can have G1 **at only one end** – setting G1 at the other end switches the first one to G0.
- An arc always adapts to its neighbour (the neighbour's tangent determines the arc), never the other way round – this avoids dependency cycles. Arc continuity is not a solver constraint, just a result computed while drawing.
- C1 is not available for an arc, and between two arcs neither is G1 (each would have to adapt to the other).
- If the neighbour's tangent lies along the chord, the arc degenerates into a line segment.

## 3. Data model

```
geometry/           Vec2, distances, even-odd test (point containment), Bézier, arcs
model/              Scene, Polygon, Edge, EdgeShape, Vertex, ControlPoint, Continuity, DemoScene
model/constraints/  Constraint, FixedLength, Perpendicular, Smooth
solver/             Solver, Locks, Projections
editor/             Editor – hit-testing, selection, dragging, commands
render/             Painter, LibraryPainter, BresenhamPainter, bresenham()
ui/                 SceneCanvas, SidePanel, LengthDialog, HelpDialog
```

### Classes

- `Point` – interface of everything that can be moved (`pos: Vec2`). Implemented by `Vertex` and `ControlPoint`, so the solver treats them the same way.
- `Vertex` – position, continuity class `continuity` (G0 / G1 / C1) and the `continuityExplicit` flag (class chosen by the user rather than the default).
- `Edge(start, end, shape)` – an edge connecting two vertices. Edges have **identity**: constraints point to objects, not indices, because indices change when vertices are inserted.
- `EdgeShape` – `Line`, `Bezier(c1, c2)` (c1 near the start, c2 near the end) or `Arc`. The arc's shape is not stored – it follows from the edge's ends and continuity.
- `Polygon` – a closed ring of edges (`edges[i]` ends where `edges[i+1]` starts). `polylineOf(edge)` returns the edge's polyline and is the **single source of shape** for drawing, filling, hit-testing and the “inside” test.
- `Scene` – a list of polygons and a list of constraints. Constraints live at scene level, so they can connect different polygons. The scene keeps things consistent: when inserting and deleting vertices and changing shapes it removes the constraints of disappearing edges.
- `Constraint` – `FixedLength(edge, length)`, `Perpendicular(e1, e2)` and `Smooth(vertex, incoming, outgoing, c1)`. `Smooth` (G1/C1) is **not stored** – on every solver run it is derived from `vertex.continuity` (`Polygon.continuityConstraints()`) for vertices next to a Bézier curve.

### State and snapshots

The only geometry state is the point positions. A scene snapshot is a `point → position` map (`Scene.snapshot()`) and `restore` brings it back. Transactions are built on this: every operation the solver cannot complete is rolled back.

### Editor

`Editor` turns user actions into operations on the model – each action is one method (`setLength`, `makePerpendicular`, `toBezier`, `toArc`, `setContinuity`, `deleteSelected`, `onPrimaryPress`, `onMove`…). It stores the selection, the element under the cursor, the drag state and a `version` counter whose change refreshes the view.

## 4. Algorithms

### Line drawing – Bresenham

Selectable in the panel: library drawing (`drawLine`) or a custom Bresenham algorithm (pixels drawn with `drawPoints`). The chosen algorithm draws the edges, including the polylines approximating Bézier curves and arcs. Helper elements (control polygon, handles, icons) are always drawn with the library.

Bresenham – the midpoint version from the lecture:

- `d = 2·dy − dx`, step E: `d += 2·dy`, step NE: `d += 2·(dy − dx)`, choose E when `d < 0`,
- 8 octants: the algorithm works on the absolute values of `dx`, `dy`, the step direction is kept in `sx`, `sy` (reflection about an axis); for `|dy| > |dx|` the axes swap roles (reflection about the diagonal).

### Bézier curves – incremental algorithm

A cubic curve V₀, V₁, V₂, V₃ is converted to the **power basis**, and points are computed with **forward differences**:

- `A0 = V0`, `A1 = 3(V1 − V0)`, `A2 = 3(V2 − 2V1 + V0)`, `A3 = V3 − 3V2 + 3V1 − V0`,
- for step `d = 1/n`: `Δ = A3·d³ + A2·d² + A1·d`, `Δ² = 6A3·d³ + 2A2·d²`, `Δ³ = 6A3·d³` (constant),
- loop: `P += Δ`, `Δ += Δ²`, `Δ² += Δ³` – three vector additions per point, no multiplications,
- the last point is exactly V₃,
- number of segments `n` – control polygon length / 4, in the range 1..200.

Consecutive points are connected with the chosen line drawing algorithm.

### Arcs

- **G0 / G0**: centre at the edge midpoint, radius = half the length, angle π.
- **G1 at the start V₀**: `t` – the neighbour's unit tangent at V₀ (direction from its point before V₀ to V₀), `n` – a vector perpendicular to `t`, `d = V₁ − V₀`. Signed radius `r = (d·d) / (2·(d·n))`, centre `S = V₀ + n·r`. The circle is tangent to `t` at V₀ and passes through V₁.
- **G1 at the end**: the same formula for the reversed arc (from V₁ to V₀ with the opposite tangent).
- The arc is drawn as a polyline (number of segments depends on angle and radius, 2..200). The ends are exact, so filling, hit-testing and the “inside” test work the same as for other edges.

### Constraint solver

The solver works by **iterative projection**. Each constraint has:

- `error()` – how far it is from being satisfied,
- `project(locks)` – moves its points so the error drops to zero, respecting the locks.

```
solve(constraints, locks):
    repeat up to 200 times:
        if all error() < 0.001 → success
        for each constraint: project(locks)
    success if all error() < 0.001
```

The solver receives the constraints stored in the scene plus `Smooth` derived from vertex continuity.

#### Locks

Every point has a lock level, and a constraint moves the points with the lowest available level:

1. **Locked** – the dragged point (when dragging an edge – all its points). Never moves, always stays under the cursor.
2. **Protected** – the vertices of the segment whose control point is being dragged, and the control points carried along with a dragged vertex. They move only when there is no other way.
3. **Free** – everything else.

#### Projections

- **Fixed segment length**: the ends move along the edge by `(|b − a| − L)`. With equal locks each moves half, otherwise only the less locked end moves.
- **Perpendicularity**: the edge with less locked ends is rotated – around its more locked end or around its midpoint – to the closer of the two directions perpendicular to the other edge. Rotation does not change length, so it does not break a fixed length. When the chosen edge is fully locked, the other one is rotated.
- **G1 / C1 continuity** at vertex V: P – the point just before V, C – the point just after V (the neighbouring vertex of a segment or a Bézier control point), `u = V − P`, `w = C − V`.
  - G1: `w` has the direction of `u` – C rotates around V keeping the arm length (or P, symmetrically),
  - C1: `w = k·u`, where `k = weight(incoming) / weight(outgoing)`, Bézier weight 3, segment weight 1,
  - the projection **never moves V** (the dragged vertex stays under the cursor) and prefers moving a control point over the far end of a segment.

#### Transactions and fallback

- **Dragging**: snapshot → move the dragged points (Locked) and the carried control points (Protected) → `solve`. On failure the snapshot is restored and the whole polygon is moved by the same vector (fallback).
- **Adding a constraint**: snapshot → add → `solve` without locks. On failure the constraint is removed, positions are restored and a message appears in the panel.
- **Changing continuity and converting to Bézier**: the vertices are locked, so the curve straightens out immediately by moving the control points. On failure the state (positions and classes) is rolled back.
- The demo scene is solved once after being built, so it starts with all constraints satisfied.
