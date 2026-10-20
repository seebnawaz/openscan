package com.openscan.capture

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.openscan.cv.DocumentCv
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Point

/**
 * Runs document detection on each preview frame and reports the 4 corners
 * (in preview-image pixel coords) so the UI can draw a live outline.
 *
 * CameraX is configured for RGBA_8888 output so the buffer maps straight to a Mat.
 * ponytail: detect() already downsamples to 500px, so this is cheap enough to run
 * per-frame; throttle with STRATEGY_KEEP_ONLY_LATEST so we never queue up.
 */
class EdgeAnalyzer(
    private val onCorners: (corners: List<Point>, frameW: Int, frameH: Int) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(image: ImageProxy) {
        try {
            // RGBA_8888 output: single plane whose rowStride may exceed width*4
            // (row padding). Build the bitmap at the padded pixel-width, then the
            // detector sees the real [image.width] because we report it below.
            val plane = image.planes[0]
            val pixelStride = plane.pixelStride
            val paddedW = plane.rowStride / pixelStride
            val bmp = android.graphics.Bitmap.createBitmap(
                paddedW, image.height, android.graphics.Bitmap.Config.ARGB_8888
            )
            bmp.copyPixelsFromBuffer(plane.buffer)
            // crop off the right-edge padding so corner coords match the preview
            val cropped = android.graphics.Bitmap.createBitmap(bmp, 0, 0, image.width, image.height)
            val mat = Mat()
            Utils.bitmapToMat(cropped, mat)
            val corners = DocumentCv.detect(mat)
            onCorners(corners, image.width, image.height)
            mat.release()
            bmp.recycle()
            cropped.recycle()
        } catch (_: Exception) {
            // a dropped frame is fine — the next one will report
        } finally {
            image.close()
        }
    }
}
