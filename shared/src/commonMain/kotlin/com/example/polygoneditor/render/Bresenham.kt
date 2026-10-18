package com.example.polygoneditor.render

import kotlin.math.abs

/**
 * Bresenham line
 * Calls [plot] for every calculated pixel of the segment
 */
inline fun bresenham(x0: Int, y0: Int, x1: Int, y1: Int, plot: (Int, Int) -> Unit) {
    // Mirroring across the axes: the algorithm works on absolute values,
    // the direction of each step is kept in sx, sy
    val sx = if (x1 >= x0) 1 else -1
    val sy = if (y1 >= y0) 1 else -1
    val dx = abs(x1 - x0)
    val dy = abs(y1 - y0)

    if (dx >= dy) {
        // |slope| <= 1: x is the driving axis
        midpointLine(dx, dy) { major, minor -> plot(x0 + sx * major, y0 + sy * minor) }
    } else {
        // |slope| > 1: mirroring across the diagonal – x and y swap roles
        midpointLine(dy, dx) { major, minor -> plot(x0 + sx * minor, y0 + sy * major) }
    }
}

/**
 * The Midpoint Line function from lecture,
 * generalized for both cases under and over 45 degrees
 */
@PublishedApi
internal inline fun midpointLine(dMajor: Int, dMinor: Int, plot: (major: Int, minor: Int) -> Unit) {
    var d = 2 * dMinor - dMajor
    val incrE = 2 * dMinor
    val incrNE = 2 * (dMinor - dMajor)

    var minor = 0
    plot(0, 0)

    for (major in 1..dMajor) {
        // midpoint above line -> choose E
        if (d < 0) {
            d += incrE
        }
        // midpoint below line -> choose NE
        else {
            d += incrNE
            minor++
        }

        plot(major, minor)
    }
}
