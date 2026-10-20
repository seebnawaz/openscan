package com.openscan

import com.openscan.cv.Geometry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.opencv.core.Point

/**
 * Pure-JVM checks for corner geometry — no OpenCV native libs needed, runs with
 * plain `./gradlew test`. Mirrors the asserts in reference_pipeline.py.
 */
class GeometryTest {

    @Test fun ordersScrambledCornersAsTlTrBrBl() {
        // feed them out of order: BR, BL, TL, TR
        val scrambled = listOf(Point(100.0, 90.0), Point(5.0, 95.0), Point(10.0, 10.0), Point(90.0, 5.0))
        val (tl, tr, br, bl) = Geometry.orderCorners(scrambled)
        assertEquals(Point(10.0, 10.0), tl)
        assertEquals(Point(90.0, 5.0), tr)
        assertEquals(Point(100.0, 90.0), br)
        assertEquals(Point(5.0, 95.0), bl)
    }

    @Test fun outputSizeTakesLongestEdges() {
        val rect = listOf(Point(0.0, 0.0), Point(300.0, 0.0), Point(300.0, 400.0), Point(0.0, 400.0))
        val (w, h) = Geometry.outputSize(Geometry.orderCorners(rect))
        assertEquals(300, w)
        assertEquals(400, h)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsWrongPointCount() {
        Geometry.orderCorners(listOf(Point(0.0, 0.0), Point(1.0, 1.0)))
    }
}
