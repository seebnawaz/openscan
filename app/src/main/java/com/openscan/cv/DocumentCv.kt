package com.openscan.cv

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.CLAHE
import org.opencv.imgproc.Imgproc

/**
 * core/cv — the whole scan pipeline: detect -> warp -> enhance.
 * Call-for-call mirror of tools/reference_pipeline.py (validated at 3.8px
 * detection error on a skewed fixture). OpenCV-Android == OpenCV-Python, so
 * the Python self-check is this code's integration test.
 */
object DocumentCv {

    enum class Filter { ORIGINAL, GRAY, BW, MAGIC }

    /** Largest 4-point contour, in full-resolution image coordinates. Falls
     *  back to the whole frame when no document-like quad is found. */
    fun detect(src: Mat): List<Point> {
        val h = src.rows().toDouble()
        val w = src.cols().toDouble()
        val scale = 500.0 / maxOf(h, w)
        val small = Mat()
        Imgproc.resize(src, small, Size(w * scale, h * scale))

        val gray = Mat()
        Imgproc.cvtColor(small, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        Imgproc.dilate(edges, edges, Mat.ones(Size(3.0, 3.0), CvType.CV_8U))

        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val minArea = 0.1 * small.rows() * small.cols()

        val quad = contours
            .sortedByDescending { Imgproc.contourArea(it) }
            .take(5)
            .firstNotNullOfOrNull { c ->
                val c2f = MatOfPoint2f(*c.toArray())
                val peri = Imgproc.arcLength(c2f, true)
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(c2f, approx, 0.02 * peri, true)
                val pts = approx.toArray()
                if (pts.size == 4 && Imgproc.contourArea(approx) > minArea) {
                    pts.map { Point(it.x / scale, it.y / scale) }
                } else null
            }

        return Geometry.orderCorners(
            quad ?: listOf(Point(0.0, 0.0), Point(w, 0.0), Point(w, h), Point(0.0, h))
        )
    }

    /** Perspective-correct the quad to a flat rectangle. */
    fun warp(src: Mat, corners: List<Point>): Mat {
        val ordered = Geometry.orderCorners(corners)
        val (outW, outH) = Geometry.outputSize(ordered)
        val srcQuad = MatOfPoint2f(*ordered.toTypedArray())
        val dstQuad = MatOfPoint2f(
            Point(0.0, 0.0),
            Point((outW - 1).toDouble(), 0.0),
            Point((outW - 1).toDouble(), (outH - 1).toDouble()),
            Point(0.0, (outH - 1).toDouble())
        )
        val m = Imgproc.getPerspectiveTransform(srcQuad, dstQuad)
        val out = Mat()
        Imgproc.warpPerspective(src, out, m, Size(outW.toDouble(), outH.toDouble()))
        return out
    }

    /** The 4 filters CamScanner ships — all standard ops, no secret sauce. */
    fun enhance(src: Mat, filter: Filter): Mat {
        val out = Mat()
        when (filter) {
            Filter.ORIGINAL -> src.copyTo(out)
            Filter.GRAY -> {
                Imgproc.cvtColor(src, out, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGBA)
            }
            Filter.BW -> {
                Imgproc.cvtColor(src, out, Imgproc.COLOR_RGBA2GRAY)
                Imgproc.adaptiveThreshold(
                    out, out, 255.0, Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                    Imgproc.THRESH_BINARY, 15, 10.0
                )
                Imgproc.cvtColor(out, out, Imgproc.COLOR_GRAY2RGBA)
            }
            Filter.MAGIC -> {
                val lab = Mat()
                Imgproc.cvtColor(src, lab, Imgproc.COLOR_RGBA2RGB)
                Imgproc.cvtColor(lab, lab, Imgproc.COLOR_RGB2Lab)
                val ch = ArrayList<Mat>()
                Core.split(lab, ch)
                val clahe: CLAHE = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
                clahe.apply(ch[0], ch[0])
                Core.merge(ch, lab)
                Imgproc.cvtColor(lab, out, Imgproc.COLOR_Lab2RGB)
                out.convertTo(out, -1, 1.15, 10.0)  // contrast + brightness
                Imgproc.cvtColor(out, out, Imgproc.COLOR_RGB2RGBA)
            }
        }
        return out
    }

    // --- Bitmap <-> Mat bridges (RGBA, matches Compose ImageBitmap) ---

    fun toMat(bitmap: Bitmap): Mat {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)  // RGBA
        return mat
    }

    fun toBitmap(mat: Mat): Bitmap {
        val bmp = Bitmap.createBitmap(mat.cols(), mat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(mat, bmp)
        return bmp
    }
}
