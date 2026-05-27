package com.robot.ai.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.annotation.RequiresApi
import com.google.gson.Gson
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream

/**
 * Service that captures real-time screenshots from the tablet screen
 * and sends them to the backend for AI analysis
 */
@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var isCapturing = false
    private val scope = CoroutineScope(Dispatchers.Default + Job())

    private val displayMetrics: DisplayMetrics
        get() {
            val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(metrics)
            return metrics
        }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("ScreenCaptureService started")
        
        // Get MediaProjectionManager
        val projectionManager = getSystemService(
            Context.MEDIA_PROJECTION_SERVICE
        ) as? MediaProjectionManager ?: run {
            Timber.e("Could not get MediaProjectionManager")
            stopSelf()
            return START_NOT_STICKY
        }

        // In a real app, you'd start this from MainActivity with user permission
        // via startActivityForResult with REQUEST_CODE
        return START_STICKY
    }

    /**
     * Start screen capture with the given MediaProjection
     * Call this after getting user permission in MainActivity
     */
    fun startCapture(mediaProjection: MediaProjection) {
        if (isCapturing) {
            Timber.w("Screen capture already running")
            return
        }

        this.mediaProjection = mediaProjection
        setupImageReader()
        startCapturingLoop()
        Timber.d("Screen capture started")
    }

    private fun setupImageReader() {
        val metrics = displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels

        Timber.d("Setting up ImageReader: ${width}x${height}")

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2
        )

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenCapture",
            width,
            height,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            try {
                val image = reader.acquireLatestImage()
                if (image != null) {
                    val bitmap = convertImageToBitmap(image)
                    if (bitmap != null) {
                        // Send in coroutine to avoid blocking
                        scope.launch {
                            sendScreenshotToBackend(bitmap)
                        }
                    }
                    image.close()
                }
            } catch (e: Exception) {
                Timber.e("Error processing image: ${e.message}")
            }
        }, null)
    }

    private fun convertImageToBitmap(image: android.media.Image): Bitmap? {
        return try {
            val planes = image.planes
            val buffer = planes[0].buffer
            buffer.rewind()

            val pixelStride = planes[0].pixelStride
            val rowPadding = planes[0].rowPadding
            val w = image.width
            val h = image.height

            val bitmap = Bitmap.createBitmap(
                w + rowPadding / pixelStride,
                h,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            
            // Crop to actual size
            val croppedBitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h)
            bitmap.recycle()
            croppedBitmap
        } catch (e: Exception) {
            Timber.e("Error converting image to bitmap: ${e.message}")
            null
        }
    }

    private suspend fun sendScreenshotToBackend(bitmap: Bitmap) {
        try {
            // Compress bitmap to PNG
            val outputStream = ByteArrayOutputStream()
            
            // Quality reduction for faster transmission
            val compressed = Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * 0.7).toInt(),
                (bitmap.height * 0.7).toInt(),
                true
            )
            
            compressed.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
            val imageBytes = outputStream.toByteArray()
            outputStream.close()

            // Encode to Base64
            val imageBase64 = Base64.encodeToString(imageBytes, Base64.NO_WRAP)
            
            Timber.d("Screenshot compressed: ${imageBytes.size / 1024} KB")

            // Prepare WebSocket message
            val message = mapOf(
                "type" to "screenshot",
                "task_id" to "current_task",
                "screenshot" to imageBase64,
                "timestamp" to System.currentTimeMillis(),
                "width" to bitmap.width,
                "height" to bitmap.height
            )

            val json = Gson().toJson(message)
            
            // Send via WebSocket (this would be injected in real implementation)
            // For now, just log
            Timber.d("Ready to send screenshot: ${json.length} bytes")

            compressed.recycle()
            bitmap.recycle()

        } catch (e: Exception) {
            Timber.e("Error sending screenshot: ${e.message}")
        }
    }

    private fun startCapturingLoop() {
        isCapturing = true
        Timber.d("Capture loop started")
    }

    fun stopCapture() {
        isCapturing = false
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        Timber.d("Screen capture stopped")
    }

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }
}
