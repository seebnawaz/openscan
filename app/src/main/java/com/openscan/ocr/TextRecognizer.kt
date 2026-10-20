package com.openscan.ocr

import android.graphics.Bitmap

/**
 * feature/ocr — the OCR seam. F-Droid rules out Google ML Kit (proprietary, needs
 * Play Services), so the concrete engine is Tesseract via tesseract4android (FOSS).
 * Keeping it behind this interface means a Play-Store flavor could still drop ML Kit
 * in later without touching callers.
 *
 * Implementation lands in M4 (TesseractRecognizer). Declared now so the F-Droid
 * decision is encoded in the architecture, not just in a doc.
 */
interface TextRecognizer {
    /** Recognize text in a warped page; returns plain text (empty if none found). */
    suspend fun recognize(page: Bitmap, lang: String = "eng"): String

    /** Words with bounding boxes, for building a searchable-PDF text layer. */
    suspend fun recognizeWords(page: Bitmap, lang: String = "eng"): List<Word>

    data class Word(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int, val confidence: Float)
}
