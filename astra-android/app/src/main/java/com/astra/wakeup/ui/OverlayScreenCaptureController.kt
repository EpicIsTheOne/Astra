package com.astra.wakeup.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.Base64
import kotlin.math.roundToInt

class OverlayScreenCaptureController(
    private val context: Context,
    private val onFrame: (jpegBase64: String) -> Unit,
) {
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var width = 0
    private var height = 0
    @Volatile private var running = false

    private val captureRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            captureFrame()
            workerHandler?.postDelayed(this, 2000L)
        }
    }

    fun start(): Boolean {
        if (running) return true
        if (!AstraScreenShareStore.isOverlayCallScreenShareEnabled(context)) return false
        projection = AstraScreenShareStore.createProjection(context) ?: return false
        val metrics = context.resources.displayMetrics
        val scale = (960f / maxOf(metrics.widthPixels, metrics.heightPixels).coerceAtLeast(1)).coerceAtMost(1f)
        width = (metrics.widthPixels * scale).roundToInt().coerceAtLeast(360)
        height = (metrics.heightPixels * scale).roundToInt().coerceAtLeast(640)
        workerThread = HandlerThread("astra-overlay-screen-capture").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "astra-overlay-screen",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            workerHandler,
        )
        running = true
        workerHandler?.postDelayed(captureRunnable, 1200L)
        return true
    }

    fun stop() {
        running = false
        workerHandler?.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
        workerThread?.quitSafely()
        workerThread = null
        workerHandler = null
    }

    private fun captureFrame() {
        val reader = imageReader ?: return
        val image = reader.acquireLatestImage() ?: return
        try {
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val rowPadding = rowStride - pixelStride * width
            val bitmap = Bitmap.createBitmap(width + (rowPadding / pixelStride), height, Bitmap.Config.ARGB_8888)
            bitmap.copyPixelsFromBuffer(buffer)
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            val out = java.io.ByteArrayOutputStream()
            cropped.compress(Bitmap.CompressFormat.JPEG, 55, out)
            val jpegBase64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
            onFrame(jpegBase64)
            out.close()
            if (cropped != bitmap) bitmap.recycle()
            cropped.recycle()
        } catch (_: Throwable) {
        } finally {
            image.close()
        }
    }
}
