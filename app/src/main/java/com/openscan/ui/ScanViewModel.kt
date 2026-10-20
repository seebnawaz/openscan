package com.openscan.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openscan.cv.DocumentCv
import com.openscan.cv.DocumentCv.Filter
import com.openscan.document.Page
import com.openscan.document.ScanDocument
import com.openscan.export.PdfExporter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.core.Mat
import org.opencv.core.Point

/** Max long-edge (px) the pipeline works at. Caps camera frames (often 4000px)
 *  so detection is fast and the exported JPEG stays small. */
private const val WORK_MAX_EDGE = 2048

sealed interface Screen {
    data object Camera : Screen
    data object Working : Screen
    /** Manual crop: the [source] image with draggable [corners] (0..1 fractions). */
    data class Crop(val source: Bitmap, val corners: List<Point>) : Screen
    /** Warped page under review before it's added, with the chosen [filter]. */
    data class Review(val warpedBmp: Bitmap, val output: Bitmap, val filter: Filter) : Screen
    data class Pages(val pages: List<Page>) : Screen
    data class Error(val message: String) : Screen
}

class ScanViewModel : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Camera)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val doc = ScanDocument()
    private var sourceMat: Mat? = null      // the image being cropped (downscaled)
    private var reviewWarped: Mat? = null   // warped page under review

    fun goCamera() { _screen.value = Screen.Camera }
    fun goPages() { _screen.value = Screen.Pages(doc.pages) }

    fun onFrame(bitmap: Bitmap) = prepareCrop { bitmap }
    fun onGalleryPick(ctx: Context, uri: Uri) = prepareCrop { loadBitmap(ctx, uri) }

    /** Load → downscale → auto-detect corners → show the crop editor. */
    private fun prepareCrop(load: () -> Bitmap) {
        _screen.value = Screen.Working
        viewModelScope.launch {
            try {
                _screen.value = withContext(Dispatchers.Default) {
                    val work = downscale(load(), WORK_MAX_EDGE)
                    val mat = DocumentCv.toMat(work)
                    sourceMat = mat
                    Screen.Crop(work, DocumentCv.detect(mat))
                }
            } catch (e: Exception) {
                _screen.value = Screen.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /** User confirmed the 4 corners → warp, apply the default filter, go to review. */
    fun confirmCrop(corners: List<Point>) {
        val src = sourceMat ?: return
        _screen.value = Screen.Working
        viewModelScope.launch {
            try {
                _screen.value = withContext(Dispatchers.Default) {
                    val warped = DocumentCv.warp(src, corners)
                    reviewWarped = warped
                    Screen.Review(
                        DocumentCv.toBitmap(warped),
                        DocumentCv.toBitmap(DocumentCv.enhance(warped, Filter.MAGIC)),
                        Filter.MAGIC,
                    )
                }
            } catch (e: Exception) {
                _screen.value = Screen.Error(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun previewFilter(filter: Filter) {
        val w = reviewWarped ?: return
        val cur = _screen.value as? Screen.Review ?: return
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { DocumentCv.toBitmap(DocumentCv.enhance(w, filter)) }
            _screen.value = cur.copy(output = out, filter = filter)
        }
    }

    fun addReviewedPage() {
        val r = _screen.value as? Screen.Review ?: return
        doc.add(r.warpedBmp, r.output, r.filter)
        reviewWarped = null; sourceMat = null
        _screen.value = Screen.Camera
    }

    fun removePage(id: Long) { doc.remove(id); _screen.value = Screen.Pages(doc.pages) }
    fun movePage(from: Int, to: Int) { doc.move(from, to); _screen.value = Screen.Pages(doc.pages) }

    fun exportPdf(ctx: Context, onReady: (Uri) -> Unit, onError: (String) -> Unit) {
        if (doc.isEmpty()) { onError("no pages"); return }
        viewModelScope.launch {
            try {
                val uri = withContext(Dispatchers.IO) { PdfExporter.export(ctx, doc.outputs()) }
                onReady(uri)
            } catch (e: Exception) {
                onError(e.message ?: e.javaClass.simpleName)
            }
        }
    }

    fun pageCount() = doc.size()

    private fun downscale(bmp: Bitmap, maxEdge: Int): Bitmap {
        val long = maxOf(bmp.width, bmp.height)
        if (long <= maxEdge) return bmp
        val s = maxEdge.toFloat() / long
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
    }

    private fun loadBitmap(ctx: Context, uri: Uri): Bitmap {
        ctx.contentResolver.openInputStream(uri).use { input ->
            val bytes = input!!.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > WORK_MAX_EDGE * 2) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error("could not decode image")
        }
    }
}
