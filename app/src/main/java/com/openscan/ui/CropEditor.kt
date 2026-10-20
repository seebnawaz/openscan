package com.openscan.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import org.opencv.core.Point
import kotlin.math.hypot

/**
 * feature/edit — manual corner adjustment. Auto-detect gives the starting quad;
 * the user drags any of the 4 handles to fix a bad detection, then confirms.
 * This is the answer to "no cropping": even when detection falls back to the
 * whole frame, the user can pull the corners onto the real document edges.
 */
@Composable
fun CropEditor(
    state: Screen.Crop,
    onConfirm: (List<Point>) -> Unit,
    onRetake: () -> Unit,
) {
    val bmp = state.source
    // handles in IMAGE pixel coords; seed from auto-detected corners
    val handles = remember(state) { mutableStateListOf<Point>().apply { addAll(state.corners) } }
    var boxW by remember { mutableIntStateOf(1) }
    var boxH by remember { mutableIntStateOf(1) }

    // the image is drawn fit-width; compute the on-screen scale from image->box
    val scale = boxW.toFloat() / bmp.width
    val drawnH = bmp.height * scale

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Adjust corners", style = MaterialTheme.typography.titleLarge)
        Text("Drag the dots onto the document's corners.", style = MaterialTheme.typography.bodySmall)

        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { boxW = it.width; boxH = it.height },
            contentAlignment = Alignment.TopCenter
        ) {
            Image(bmp.asImageBitmap(), "captured page", Modifier.fillMaxWidth())

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(with(androidx.compose.ui.platform.LocalDensity.current) { drawnH.toDp() })
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { }, onDrag = { change, drag ->
                                // move the nearest handle (in screen space) by the drag delta
                                val touch = change.position
                                val idx = handles.indices.minByOrNull { i ->
                                    hypot(handles[i].x * scale - touch.x.toDouble(), handles[i].y * scale - touch.y.toDouble())
                                } ?: return@detectDragGestures
                                val nx = (handles[idx].x + drag.x / scale).coerceIn(0.0, bmp.width.toDouble())
                                val ny = (handles[idx].y + drag.y / scale).coerceIn(0.0, bmp.height.toDouble())
                                handles[idx] = Point(nx, ny)
                                change.consume()
                            }
                        )
                    }
            ) {
                androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
                    if (handles.size == 4) {
                        val pts = handles.map { Offset((it.x * scale).toFloat(), (it.y * scale).toFloat()) }
                        val path = Path().apply {
                            moveTo(pts[0].x, pts[0].y); pts.drop(1).forEach { lineTo(it.x, it.y) }; close()
                        }
                        drawPath(path, Color(0xFF00E676), style = Stroke(width = 5f))
                        pts.forEach { drawCircle(Color(0xFF00E676), radius = 22f, center = it) }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onRetake) { Text("Retake") }
            OutlinedButton(onClick = {
                // reset to full frame if detection was way off
                handles.clear()
                handles.addAll(listOf(
                    Point(0.0, 0.0), Point(bmp.width.toDouble(), 0.0),
                    Point(bmp.width.toDouble(), bmp.height.toDouble()), Point(0.0, bmp.height.toDouble())
                ))
            }) { Text("Full page") }
            Button(onClick = { onConfirm(handles.toList()) }) { Text("Use these corners") }
        }
    }
}
