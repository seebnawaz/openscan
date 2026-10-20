package com.openscan.export

import java.io.ByteArrayOutputStream

/**
 * Minimal PDF writer that embeds pre-compressed JPEG bytes directly (DCTDecode).
 * Android's PdfDocument re-encodes drawn bitmaps losslessly (FlateDecode), which
 * made a 2-page scan 27 MB. Embedding JPEG keeps a page at tens of KB.
 *
 * One image per page; each page is sized so the image fills it (long edge mapped
 * to A4's long edge in points), so there's no letterbox waste. Pure bytes in,
 * valid PDF bytes out — deterministic and testable.
 *
 * Object numbering: 1=Catalog, 2=Pages, then per page i (0-based):
 *   page = 3+3i, image = 4+3i, content = 5+3i.
 */
object JpegPdf {

    /** A JPEG-encoded page plus its pixel dimensions. */
    data class JpegPage(val jpeg: ByteArray, val width: Int, val height: Int)

    private const val A4_LONG = 842.0  // points

    fun build(pages: List<JpegPage>): ByteArray {
        require(pages.isNotEmpty()) { "no pages" }
        val out = ByteArrayOutputStream()
        val offsets = ArrayList<Int>()  // offsets[k] = byte offset of object (k+1)
        fun write(s: String) = out.write(s.toByteArray(Charsets.ISO_8859_1))
        fun obj(n: Int) { require(offsets.size == n - 1); offsets.add(out.size()) }

        write("%PDF-1.4\n")

        obj(1); write("1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n")

        val kids = pages.indices.joinToString(" ") { "${3 + it * 3} 0 R" }
        obj(2); write("2 0 obj\n<< /Type /Pages /Kids [ $kids ] /Count ${pages.size} >>\nendobj\n")

        pages.forEachIndexed { i, p ->
            val pageNum = 3 + i * 3
            val imgNum = pageNum + 1
            val contentNum = pageNum + 2
            val scale = A4_LONG / maxOf(p.width, p.height)
            val pw = p.width * scale
            val ph = p.height * scale

            obj(pageNum); write(
                "$pageNum 0 obj\n<< /Type /Page /Parent 2 0 R " +
                    "/MediaBox [0 0 ${fmt(pw)} ${fmt(ph)}] " +
                    "/Resources << /XObject << /Im0 $imgNum 0 R >> >> " +
                    "/Contents $contentNum 0 R >>\nendobj\n"
            )

            obj(imgNum); write(
                "$imgNum 0 obj\n<< /Type /XObject /Subtype /Image /Width ${p.width} " +
                    "/Height ${p.height} /ColorSpace /DeviceRGB /BitsPerComponent 8 " +
                    "/Filter /DCTDecode /Length ${p.jpeg.size} >>\nstream\n"
            )
            out.write(p.jpeg)
            write("\nendstream\nendobj\n")

            val content = "q ${fmt(pw)} 0 0 ${fmt(ph)} 0 0 cm /Im0 Do Q"
            obj(contentNum)
            write("$contentNum 0 obj\n<< /Length ${content.length} >>\nstream\n$content\nendstream\nendobj\n")
        }

        val xrefPos = out.size()
        val size = offsets.size + 1  // +1 for free object 0
        write("xref\n0 $size\n0000000000 65535 f \n")
        offsets.forEach { write("%010d 00000 n \n".format(it)) }
        write("trailer\n<< /Size $size /Root 1 0 R >>\nstartxref\n$xrefPos\n%%EOF\n")
        return out.toByteArray()
    }

    private fun fmt(d: Double) = "%.2f".format(d)
}
