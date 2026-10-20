package com.openscan.capture

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import org.opencv.core.Point
import java.util.concurrent.Executors

/**
 * feature/capture — live CameraX preview with a real-time detected-document
 * outline, and a shutter that hands the full-res frame back for the CV pipeline.
 */
@Composable
fun CameraScreen(
    onCaptured: (Bitmap) -> Unit,
    onPickGallery: () -> Unit,
) {
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    var granted by remember { mutableStateOf(hasCameraPermission(ctx)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permLauncher.launch(Manifest.permission.CAMERA) }

    // live overlay state: corners in frame-pixel coords + the frame size
    var corners by remember { mutableStateOf<List<Point>>(emptyList()) }
    var frameW by remember { mutableIntStateOf(1) }
    var frameH by remember { mutableIntStateOf(1) }

    val capture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }

    if (!granted) {
        Column(Modifier.fillMaxSize(), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Camera permission needed to scan.")
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("Grant") }
            Button(onClick = onPickGallery) { Text("Or pick from gallery") }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                val previewView = PreviewView(c)
                bindCamera(c, lifecycle, previewView, capture) { cs, w, h ->
                    corners = cs; frameW = w; frameH = h
                }
                previewView
            }
        )

        // draw the detected quad, scaled from frame pixels to canvas size
        Canvas(Modifier.fillMaxSize()) {
            if (corners.size == 4) {
                val sx = size.width / frameW
                val sy = size.height / frameH
                val path = Path().apply {
                    val p0 = corners[0]; moveTo((p0.x * sx).toFloat(), (p0.y * sy).toFloat())
                    corners.drop(1).forEach { lineTo((it.x * sx).toFloat(), (it.y * sy).toFloat()) }
                    close()
                }
                drawPath(path, Color(0xFF00E676), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6f))
                corners.forEach {
                    drawCircle(Color(0xFF00E676), radius = 10f, center = Offset((it.x * sx).toFloat(), (it.y * sy).toFloat()))
                }
            }
        }

        Row(
            Modifier.align(Alignment.BottomCenter).padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Button(onClick = onPickGallery) { Text("Gallery") }
            Button(onClick = {
                capture.takePicture(
                    Executors.newSingleThreadExecutor(),
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            val bmp = image.toBitmapRgba()
                            image.close()
                            onCaptured(bmp)
                        }
                        override fun onError(e: ImageCaptureException) { e.printStackTrace() }
                    }
                )
            }) { Text("Capture") }
        }
    }
}

private fun hasCameraPermission(ctx: Context) =
    ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

private fun bindCamera(
    ctx: Context,
    lifecycle: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    capture: ImageCapture,
    onCorners: (List<Point>, Int, Int) -> Unit,
) {
    val future = ProcessCameraProvider.getInstance(ctx)
    future.addListener({
        val provider = future.get()
        val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { it.setAnalyzer(Executors.newSingleThreadExecutor(), EdgeAnalyzer(onCorners)) }
        provider.unbindAll()
        provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture, analysis)
    }, ContextCompat.getMainExecutor(ctx))
}

/** JPEG ImageProxy -> ARGB Bitmap. */
private fun ImageProxy.toBitmapRgba(): Bitmap {
    val buf = planes[0].buffer
    val bytes = ByteArray(buf.remaining()).also { buf.get(it) }
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}
