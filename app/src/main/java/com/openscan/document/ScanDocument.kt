package com.openscan.document

import android.graphics.Bitmap
import com.openscan.cv.DocumentCv.Filter

/** One scanned page: the warped image + the chosen filter's rendered output. */
data class Page(
    val id: Long,
    val warped: Bitmap,      // perspective-corrected, pre-filter
    val output: Bitmap,      // [filter] applied to [warped]
    val filter: Filter,
)

/**
 * feature/document — in-memory multi-page document. Room persistence is a later
 * milestone; this keeps the model behind one type so swapping storage in is local.
 * ponytail: in-memory list, add Room when pages must survive process death.
 */
class ScanDocument {
    private val _pages = mutableListOf<Page>()
    val pages: List<Page> get() = _pages.toList()
    private var nextId = 0L

    fun add(warped: Bitmap, output: Bitmap, filter: Filter): Page =
        Page(nextId++, warped, output, filter).also { _pages.add(it) }

    fun replace(id: Long, output: Bitmap, filter: Filter) {
        val i = _pages.indexOfFirst { it.id == id }
        if (i >= 0) _pages[i] = _pages[i].copy(output = output, filter = filter)
    }

    fun remove(id: Long) { _pages.removeAll { it.id == id } }

    /** Move page at [from] to [to] (drag-reorder). */
    fun move(from: Int, to: Int) {
        if (from in _pages.indices && to in _pages.indices) _pages.add(to, _pages.removeAt(from))
    }

    fun outputs(): List<Bitmap> = _pages.map { it.output }
    fun isEmpty() = _pages.isEmpty()
    fun size() = _pages.size
}
