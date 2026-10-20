package com.openscan.cv

import org.opencv.core.Point
import kotlin.math.hypot

/**
 * Pure corner geometry — no OpenCV native calls, so it unit-tests on the JVM.
 * Mirrors reference_pipeline.py `order_corners`.
 */
object Geometry {

    /** Order 4 points as TL, TR, BR, BL. TL has min (x+y); BR max (x+y);
     *  TR min (y-x); BL max (y-x). */
    fun orderCorners(pts: List<Point>): List<Point> {
        require(pts.size == 4) { "need exactly 4 points, got ${pts.size}" }
        val tl = pts.minByOrNull { it.x + it.y }!!
        val br = pts.maxByOrNull { it.x + it.y }!!
        val tr = pts.minByOrNull { it.y - it.x }!!
        val bl = pts.maxByOrNull { it.y - it.x }!!
        return listOf(tl, tr, br, bl)
    }

    /** Target rectangle size for a warp: longest opposing edges win. */
    fun outputSize(ordered: List<Point>): Pair<Int, Int> {
        val (tl, tr, br, bl) = ordered
        val w = maxOf(dist(br, bl), dist(tr, tl))
        val h = maxOf(dist(tr, br), dist(tl, bl))
        return w.toInt().coerceAtLeast(1) to h.toInt().coerceAtLeast(1)
    }

    private fun dist(a: Point, b: Point) = hypot(a.x - b.x, a.y - b.y)
}
