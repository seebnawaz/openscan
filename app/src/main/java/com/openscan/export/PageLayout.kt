package com.openscan.export

/** Pure page-fit math — unit-testable on the JVM, no android.graphics. */
object PageLayout {

    /** A4 at 72 dpi (PdfDocument points). */
    const val A4_W = 595
    const val A4_H = 842

    data class Rect(val left: Int, val top: Int, val width: Int, val height: Int)

    /**
     * Fit an [imgW]×[imgH] image centered inside a [pageW]×[pageH] page,
     * preserving aspect ratio (letterbox). [margin] points on every side.
     */
    fun fit(imgW: Int, imgH: Int, pageW: Int, pageH: Int, margin: Int = 24): Rect {
        require(imgW > 0 && imgH > 0) { "image size must be positive" }
        val availW = (pageW - 2 * margin).coerceAtLeast(1)
        val availH = (pageH - 2 * margin).coerceAtLeast(1)
        val scale = minOf(availW.toDouble() / imgW, availH.toDouble() / imgH)
        val w = (imgW * scale).toInt().coerceAtLeast(1)
        val h = (imgH * scale).toInt().coerceAtLeast(1)
        return Rect(left = (pageW - w) / 2, top = (pageH - h) / 2, width = w, height = h)
    }
}
