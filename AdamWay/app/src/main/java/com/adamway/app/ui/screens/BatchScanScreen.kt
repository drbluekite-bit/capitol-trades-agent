package com.adamway.app.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.adamway.app.ocr.DeliverySheetParser
import com.adamway.app.ocr.TextRecognitionHelper
import com.adamway.app.viewmodel.DeliveryRunViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Photographs delivery sheets one at a time, going through as many as
 * needed, until you tap "Done". Each photo is decoded, OCR'd, parsed, and
 * discarded immediately — same as the single-address camera scan
 * elsewhere in the app, nothing is ever written to disk. Parsed rows
 * accumulate in [DeliveryRunViewModel] across every photo in the session,
 * ready for review before anything touches the real queue.
 */
@Composable
fun BatchScanScreen(
    deliveryRunViewModel: DeliveryRunViewModel,
    onDoneScanning: () -> Unit,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    val rows by deliveryRunViewModel.rows.collectAsState()
    val photosScanned by deliveryRunViewModel.photosScanned.collectAsState()

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose { cameraExecutor.shutdown() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan delivery sheets", color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Cancel", tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                actions = {
                    if (rows.isNotEmpty()) {
                        TextButton(onClick = onDoneScanning) {
                            Text("Done (${rows.size})", color = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder().build()
                        imageCapture = capture
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                capture,
                            )
                        } catch (e: Exception) {
                            Toast.makeText(ctx, "Could not start camera: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
            )

            Text(
                "$photosScanned photo${if (photosScanned == 1) "" else "s"} scanned — ${rows.size} deliver${if (rows.size == 1) "y" else "ies"} found",
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.75f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )

            if (isProcessing) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            FloatingActionButton(
                onClick = {
                    val capture = imageCapture ?: return@FloatingActionButton
                    isProcessing = true
                    capture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = imageProxyToBitmap(image)
                                image.close()
                                scope.launch {
                                    try {
                                        val result = TextRecognitionHelper.recognizeStructured(bitmap)
                                        val parsed = DeliverySheetParser.parse(result)
                                        deliveryRunViewModel.addParsedFromPhoto(parsed)
                                        isProcessing = false
                                        val message = if (parsed.isEmpty()) {
                                            "Couldn't find a delivery on that photo — try a flatter, brighter shot"
                                        } else {
                                            "Found ${parsed.size} deliver${if (parsed.size == 1) "y" else "ies"} on that photo"
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    } catch (e: Exception) {
                                        isProcessing = false
                                        Toast.makeText(context, "Couldn't read that photo: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                isProcessing = false
                                Toast.makeText(context, "Capture failed: ${exception.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                    )
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(32.dp)
                    .size(72.dp),
            ) {
                Icon(Icons.Filled.Camera, contentDescription = "Capture")
            }
        }
    }
}

private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
    val buffer = image.planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0) return bitmap
    val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}
