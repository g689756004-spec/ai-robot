package com.robot.ai.ai

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import com.google.gson.Gson
import com.robot.ai.models.ScreenAnalysis
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Handles communication with backend AI for screen analysis
 * Sends screenshots and receives action recommendations
 */
class AIAgent(
    private val backendUrl: String = "http://10.0.2.2:8000",
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default)
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Analyze a screenshot and get next action
     */
    suspend fun analyzeScreenshot(
        bitmap: Bitmap,
        taskContext: String = ""
    ): ScreenAnalysis? {
        return withContext(Dispatchers.IO) {
            try {
                // Compress screenshot
                val screenshotBytes = compressScreenshot(bitmap)
                Timber.d("Screenshot compressed: ${screenshotBytes.size / 1024} KB")

                // Create multipart request
                val requestBody = createMultipartRequest(
                    screenshotBytes,
                    taskContext
                )

                val request = Request.Builder()
                    .url("$backendUrl/api/analyze-screen")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val analysis = gson.fromJson(body, ScreenAnalysis::class.java)
                    Timber.d("Screen analysis received: ${analysis.analysis.take(50)}...")
                    analysis
                } else {
                    Timber.e("Analysis failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Timber.e("Error analyzing screenshot: ${e.message}")
                null
            }
        }
    }

    /**
     * Extract text from screenshot
     */
    suspend fun extractText(bitmap: Bitmap): String {
        return withContext(Dispatchers.IO) {
            try {
                val screenshotBytes = compressScreenshot(bitmap)
                val requestBody = createMultipartRequest(screenshotBytes, "")

                val request = Request.Builder()
                    .url("$backendUrl/api/extract-text")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    val json = gson.fromJson(body, Map::class.java)
                    json["text"]?.toString() ?: ""
                } else {
                    Timber.e("Text extraction failed: ${response.code}")
                    ""
                }
            } catch (e: Exception) {
                Timber.e("Error extracting text: ${e.message}")
                ""
            }
        }
    }

    /**
     * Understand page layout
     */
    suspend fun analyzeLayout(bitmap: Bitmap): Map<String, Any>? {
        return withContext(Dispatchers.IO) {
            try {
                val screenshotBytes = compressScreenshot(bitmap)
                val requestBody = createMultipartRequest(screenshotBytes, "")

                val request = Request.Builder()
                    .url("$backendUrl/api/understand-layout")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                if (response.isSuccessful) {
                    val body = response.body?.string() ?: "{}"
                    @Suppress("UNCHECKED_CAST")
                    gson.fromJson(body, Map::class.java) as? Map<String, Any>
                } else {
                    Timber.e("Layout analysis failed: ${response.code}")
                    null
                }
            } catch (e: Exception) {
                Timber.e("Error analyzing layout: ${e.message}")
                null
            }
        }
    }

    /**
     * Compress screenshot for transmission
     */
    private fun compressScreenshot(bitmap: Bitmap): ByteArray {
        val outputStream = ByteArrayOutputStream()
        
        // Scale down for faster transmission
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * 0.7).toInt(),
            (bitmap.height * 0.7).toInt(),
            true
        )
        
        scaled.compress(Bitmap.CompressFormat.JPEG, 70, outputStream)
        val bytes = outputStream.toByteArray()
        outputStream.close()
        
        bitmap.recycle()
        scaled.recycle()
        
        return bytes
    }

    /**
     * Create multipart request for file upload
     */
    private fun createMultipartRequest(imageBytes: ByteArray, context: String): okhttp3.RequestBody {
        val boundary = "----BoundaryAIRobot${System.currentTimeMillis()}"
        val body = StringBuilder()

        // Image part
        body.append("--$boundary\r\n")
        body.append("Content-Disposition: form-data; name=\"file\"; filename=\"screenshot.jpg\"\r\n")
        body.append("Content-Type: image/jpeg\r\n\r\n")

        val requestBody = body.toString().toRequestBody()
        
        // Would need multipart body builder library for complete implementation
        // For now, simplified version
        return requestBody
    }

    /**
     * Continuous screenshot analysis loop
     */
    fun startAnalysisLoop(
        screenshotProvider: suspend () -> Bitmap,
        onAnalysis: suspend (ScreenAnalysis) -> Unit,
        interval: Long = 2000
    ) {
        scope.launch {
            while (isActive) {
                try {
                    val screenshot = screenshotProvider()
                    val analysis = analyzeScreenshot(screenshot)
                    
                    if (analysis != null) {
                        onAnalysis(analysis)
                    }
                    
                    delay(interval)
                } catch (e: Exception) {
                    Timber.e("Analysis loop error: ${e.message}")
                }
            }
        }
    }

    /**
     * Stop the analysis loop
     */
    fun stopAnalysisLoop() {
        scope.cancel()
    }
}
