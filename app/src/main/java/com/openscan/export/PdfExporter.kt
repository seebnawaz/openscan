package com.openscan.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

/**
 * feature/export — multi-page PDF, one scanned page per PDF page, letterboxed on
 * A4. NO WATERMARK, ever (the whole point vs CamScanner's free tier). Pure layout
 * math lives in [PageLayout]; this just drives PdfDocument.
 */
object PdfExporter {

    /** Writes [pages] to a PDF in cache and returns a shareable content:// Uri. */
    fun export(ctx: Context, pages: List<Bitmap>, baseName: String = "scan"): Uri {
        require(pages.isNotEmpty()) { "no pages to export" }
        val doc = PdfDocument()
        try {
            pages.forEachIndexed { i, bmp ->
                val pageInfo = PdfDocument.PageInfo
                    .Builder(PageLayout.A4_W, PageLayout.A4_H, i + 1).create()
                val page = doc.startPage(pageInfo)
                val c = page.canvas
                c.drawColor(Color.WHITE)
                val r = PageLayout.fit(bmp.width, bmp.height, PageLayout.A4_W, PageLayout.A4_H)
                c.drawBitmap(bmp, null, Rect(r.left, r.top, r.left + r.width, r.top + r.height), null)
                doc.finishPage(page)
            }
            val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
            val out = File(dir, "$baseName-${System.currentTimeMillis()}.pdf")
            out.outputStream().use { doc.writeTo(it) }
            return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
        } finally {
            doc.close()
        }
    }
}
