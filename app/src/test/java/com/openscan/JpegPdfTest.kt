package com.openscan

import com.openscan.export.JpegPdf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM structural checks for the JPEG-PDF writer. We feed dummy "JPEG" bytes
 * (the writer doesn't decode them) and assert the PDF skeleton is well-formed:
 * header, object count, page count, xref size, trailer. Mirrors the pypdf-verified
 * structure from tools/proof/scan_jpeg.pdf.
 */
class JpegPdfTest {

    @Test fun buildsWellFormedTwoPagePdf() {
        val pages = listOf(
            JpegPdf.JpegPage(byteArrayOf(1, 2, 3, 4), 1200, 1600),
            JpegPdf.JpegPage(byteArrayOf(5, 6, 7, 8, 9), 800, 600),
        )
        val pdf = JpegPdf.build(pages).toString(Charsets.ISO_8859_1)

        assertTrue("starts with PDF header", pdf.startsWith("%PDF-1.4"))
        assertTrue("ends with EOF", pdf.trimEnd().endsWith("%%EOF"))
        assertTrue("has 2 pages", pdf.contains("/Count 2"))
        assertEquals("one Page per page", 2, Regex("/Type /Page[^s]").findAll(pdf).count())
        assertEquals("one image per page", 2, pdf.split("/Subtype /Image").size - 1)
        // 1 catalog + 1 pages + 3 objects/page*2 = 8 objects -> xref /Size 9 (incl free obj 0)
        assertTrue("xref size 9", pdf.contains("/Size 9"))
        assertTrue("DCTDecode (JPEG) filter", pdf.contains("/Filter /DCTDecode"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsEmpty() {
        JpegPdf.build(emptyList())
    }
}
