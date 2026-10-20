package com.openscan.export

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * feature/export — multi-page PDF, one scanned page per page, JPEG-compressed
 * (via [JpegPdf]) so files stay small. NO WATERMARK. The old PdfDocument path
 * stored bitmaps losslessly and produced 27 MB for 2 pages; this keeps it tiny.
 */
object PdfExporter {

    /** Writes [pages] to a JPEG-embedded PDF in cache; returns a shareable Uri. */
    fun export(ctx: Context, pages: List<Bitmap>, quality: Int = 80, baseName: String = "scan"): Uri {
        require(pages.isNotEmpty()) { "no pages to export" }
        val jpegPages = pages.map { bmp ->
            val bos = ByteArrayOutputStream()
            bmp.compress(Bitmap.CompressFormat.JPEG, quality, bos)
            JpegPdf.JpegPage(bos.toByteArray(), bmp.width, bmp.height)
        }
        val bytes = JpegPdf.build(jpegPages)
        val dir = File(ctx.cacheDir, "exports").apply { mkdirs() }
        val out = File(dir, "$baseName-${System.currentTimeMillis()}.pdf")
        out.writeBytes(bytes)
        return FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", out)
    }
}
