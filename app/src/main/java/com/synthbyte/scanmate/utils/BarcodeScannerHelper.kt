package com.synthbyte.scanmate.utils

import android.graphics.Bitmap
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object BarcodeScannerHelper {
    suspend fun scanBarcode(bitmap: Bitmap, rotationDegrees: Int = 0): String? = suspendCancellableCoroutine { continuation ->
        val scanner = try {
            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
            BarcodeScanning.getClient(options)
        } catch (_: Exception) {
            if (continuation.isActive) continuation.resume(null)
            return@suspendCancellableCoroutine
        }

        try {
            val image = InputImage.fromBitmap(bitmap, rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    runCatching { scanner.close() }
                    if (continuation.isActive) {
                        continuation.resume(barcodes.firstOrNull()?.rawValue?.trim()?.takeIf { it.isNotBlank() })
                    }
                }
                .addOnFailureListener {
                    runCatching { scanner.close() }
                    if (continuation.isActive) continuation.resume(null)
                }
        } catch (_: Exception) {
            runCatching { scanner.close() }
            if (continuation.isActive) continuation.resume(null)
        }
    }
}
