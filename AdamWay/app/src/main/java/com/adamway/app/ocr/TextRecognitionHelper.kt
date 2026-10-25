package com.adamway.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Thin wrapper around ML Kit's bundled (on-device) Latin text recognizer.
 * The model ships inside the APK, so recognition happens entirely on the
 * phone — the photo never leaves the device.
 */
object TextRecognitionHelper {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** The raw result, with per-line position data — needed to tell apart fields laid out in columns, e.g. a delivery sheet. */
    suspend fun recognizeStructured(bitmap: Bitmap): Text = suspendCancellableCoroutine { cont ->
        val image = InputImage.fromBitmap(bitmap, 0)
        recognizer.process(image)
            .addOnSuccessListener { result -> cont.resume(result) }
            .addOnFailureListener { error -> cont.resumeWithException(error) }
    }

    suspend fun recognize(bitmap: Bitmap): String = recognizeStructured(bitmap).text
}
