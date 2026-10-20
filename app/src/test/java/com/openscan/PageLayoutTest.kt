package com.openscan

import com.openscan.export.PageLayout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM checks for PDF page-fit math (no android.graphics). */
class PageLayoutTest {

    @Test fun portraitImageFitsCenteredWithinMargins() {
        val r = PageLayout.fit(1000, 1400, PageLayout.A4_W, PageLayout.A4_H, margin = 24)
        // must stay inside the margin box
        assertTrue(r.left >= 24)
        assertTrue(r.top >= 24)
        assertTrue(r.left + r.width <= PageLayout.A4_W - 24)
        assertTrue(r.top + r.height <= PageLayout.A4_H - 24)
        // aspect ratio preserved (1000:1400 = 0.714)
        assertEquals(1000.0 / 1400.0, r.width.toDouble() / r.height, 0.01)
        // centered
        assertEquals(PageLayout.A4_W - (r.left + r.width), r.left)
    }

    @Test fun wideImageIsLimitedByWidth() {
        val r = PageLayout.fit(3000, 500, PageLayout.A4_W, PageLayout.A4_H)
        assertEquals(PageLayout.A4_W - 24, r.left + r.width)  // hits horizontal margin
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZeroSizedImage() {
        PageLayout.fit(0, 100, PageLayout.A4_W, PageLayout.A4_H)
    }
}
