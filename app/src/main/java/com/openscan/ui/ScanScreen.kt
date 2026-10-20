package com.openscan.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openscan.capture.CameraScreen
import com.openscan.cv.DocumentCv.Filter

@Composable
fun ScanScreen(vm: ScanViewModel = viewModel()) {
    val ctx = LocalContext.current
    val screen by vm.screen.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? -> uri?.let { vm.onGalleryPick(ctx, it) } }
    val pickGallery = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    when (val s = screen) {
        is Screen.Camera -> Box(Modifier.fillMaxSize()) {
            CameraScreen(onCaptured = vm::onFrame, onPickGallery = { pickGallery() })
            if (vm.pageCount() > 0) {
                ExtendedFloatingActionButton(
                    onClick = vm::goPages,
                    modifier = Modifier.align(Alignment.TopEnd).padding(16.dp),
                    text = { Text("${vm.pageCount()} page(s)") },
                    icon = {}
                )
            }
        }
        is Screen.Working -> Center { CircularProgressIndicator(); Text("Processing…") }
        is Screen.Error -> Center {
            Text("Error: ${s.message}")
            Button(onClick = vm::goCamera) { Text("Back") }
        }
        is Screen.Crop -> CropEditor(s, onConfirm = vm::confirmCrop, onRetake = vm::goCamera)
        is Screen.Review -> ReviewView(s, vm)
        is Screen.Pages -> PagesView(s, vm, ctx)
    }
}

@Composable
private fun ReviewView(s: Screen.Review, vm: ScanViewModel) = Column(
    Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text("Review page", style = MaterialTheme.typography.titleLarge)
    Image(s.output.asImageBitmap(), "scanned page", Modifier.fillMaxWidth().heightIn(max = 420.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach { f ->
            FilterChip(f == s.filter, onClick = { vm.previewFilter(f) }, label = { Text(f.name) })
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::goCamera) { Text("Retake") }
        Button(onClick = vm::addReviewedPage) { Text("Add page") }
    }
}

@Composable
private fun PagesView(s: Screen.Pages, vm: ScanViewModel, ctx: android.content.Context) = Column(
    Modifier.fillMaxSize().padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp)
) {
    Text("Document — ${s.pages.size} page(s)", style = MaterialTheme.typography.titleLarge)
    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(s.pages) { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Image(p.output.asImageBitmap(), "page ${i + 1}", Modifier.height(120.dp))
                Text("Page ${i + 1} · ${p.filter}")
                Spacer(Modifier.weight(1f))
                if (i > 0) TextButton(onClick = { vm.movePage(i, i - 1) }) { Text("↑") }
                if (i < s.pages.size - 1) TextButton(onClick = { vm.movePage(i, i + 1) }) { Text("↓") }
                TextButton(onClick = { vm.removePage(p.id) }) { Text("✕") }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = vm::goCamera) { Text("+ Add page") }
        Button(onClick = {
            vm.exportPdf(ctx, onReady = { uri -> sharePdf(ctx, uri) }, onError = {})
        }) { Text("Export PDF") }
    }
    Text("No watermark. Ever.", style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun Center(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
    horizontalAlignment = Alignment.CenterHorizontally, content = content
)

private fun sharePdf(ctx: android.content.Context, uri: Uri) {
    val share = Intent(Intent.ACTION_SEND).apply {
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(share, "Share PDF"))
}
