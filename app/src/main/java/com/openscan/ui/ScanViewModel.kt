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

/** Where in the capture flow the user is. */
sealed interface Screen {
    data object Camera : Screen
    data object Working : Screen
    /** A freshly scanned page under review before it's added to the document. */
    data class Review(val warpedBmp: Bitmap, val output: Bitmap, val filter: Filter) : Screen
    /** The multi-page document so far. */
    data class Pages(val pages: List<Page>) : Screen
    data class Error(val message: String) : Screen
}

class ScanViewModel : ViewModel() {
    private val _screen = MutableStateFlow<Screen>(Screen.Camera)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val doc = ScanDocument()
    private var reviewWarped: Mat? = null   // Mat behind the page under review

    fun goCamera() { _screen.value = Screen.Camera }
    fun goPages() { _screen.value = Screen.Pages(doc.pages) }

    /** A frame arrived (camera capture or gallery pick) → run the CV pipeline. */
    fun onFrame(bitmap: Bitmap) = process { bitmap }

    fun onGalleryPick(ctx: Context, uri: Uri) = process { loadBitmap(ctx, uri) }

    private fun process(load: () -> Bitmap) {
        _screen.value = Screen.Working
        viewModelScope.launch {
            try {
                _screen.value = withContext(Dispatchers.Default) {
                    val src = DocumentCv.toMat(load())
                    val warped = DocumentCv.warp(src, DocumentCv.detect(src))
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

    /** Re-render the page under review with a different filter. */
    fun previewFilter(filter: Filter) {
        val w = reviewWarped ?: return
        val cur = _screen.value as? Screen.Review ?: return
        viewModelScope.launch {
            val out = withContext(Dispatchers.Default) { DocumentCv.toBitmap(DocumentCv.enhance(w, filter)) }
            _screen.value = cur.copy(output = out, filter = filter)
        }
    }

    /** Accept the reviewed page into the document, then back to camera for the next. */
    fun addReviewedPage() {
        val r = _screen.value as? Screen.Review ?: return
        doc.add(r.warpedBmp, r.output, r.filter)
        reviewWarped = null
        _screen.value = Screen.Camera
    }

    fun removePage(id: Long) { doc.remove(id); _screen.value = Screen.Pages(doc.pages) }
    fun movePage(from: Int, to: Int) { doc.move(from, to); _screen.value = Screen.Pages(doc.pages) }

    /** Export every page to a no-watermark PDF; returns a shareable Uri via [onReady]. */
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

    private fun loadBitmap(ctx: Context, uri: Uri): Bitmap {
        ctx.contentResolver.openInputStream(uri).use { input ->
            val bytes = input!!.readBytes()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2000) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: error("could not decode image")
        }
    }
}
