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
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.robot.ai.network.WebSocketClient
import kotlinx.coroutines.*
import timber.log.Timber
import java.io.ByteArrayOutputStream


class ScreenCaptureService : Service() {


    private var mediaProjection: MediaProjection? = null

    private var virtualDisplay: VirtualDisplay? = null

    private var imageReader: ImageReader? = null


    private lateinit var webSocketClient: WebSocketClient


    private val gson = Gson()


    private val scope =
        CoroutineScope(
            Dispatchers.IO + SupervisorJob()
        )


    private var capturing = false



    private val displayMetrics: DisplayMetrics
        get() {

            val wm =
                getSystemService(
                    Context.WINDOW_SERVICE
                ) as WindowManager


            return DisplayMetrics().also {

                wm.defaultDisplay.getRealMetrics(it)

            }

        }






    override fun onBind(intent: Intent?): IBinder? {

        return null

    }






    override fun onCreate() {

        super.onCreate()


        createNotification()


        webSocketClient =
            WebSocketClient()


        webSocketClient.connect()


    }






    private fun createNotification() {


        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


            val notification =
                NotificationCompat.Builder(
                    this,
                    "AI_AGENT_CHANNEL"
                )
                    .setContentTitle(
                        "AI Vision Active"
                    )
                    .setContentText(
                        "Analyzing screen"
                    )
                    .setSmallIcon(
                        android.R.drawable.ic_menu_view
                    )
                    .build()


            startForeground(
                2001,
                notification
            )

        }

    }






    fun startCapture(
        projection: MediaProjection
    ) {


        if(capturing)
            return



        mediaProjection =
            projection



        setupImageReader()



        capturing = true



        Timber.d(
            "Screen capture started"
        )

    }







    private fun setupImageReader() {


        val metrics =
            displayMetrics


        val width =
            metrics.widthPixels


        val height =
            metrics.heightPixels




        imageReader =
            ImageReader.newInstance(

                width,

                height,

                PixelFormat.RGBA_8888,

                2

            )




        virtualDisplay =
            mediaProjection?.createVirtualDisplay(

                "AI_SCREEN_CAPTURE",

                width,

                height,

                metrics.densityDpi,

                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,

                imageReader!!.surface,

                null,

                null

            )




        imageReader?.setOnImageAvailableListener(
            { reader ->


                try {


                    val image =
                        reader.acquireLatestImage()
                            ?: return@setOnImageAvailableListener



                    val bitmap =
                        imageToBitmap(
                            image
                        )


                    image.close()



                    bitmap?.let {


                        scope.launch {


                            sendScreenshot(
                                it
                            )


                        }


                    }



                } catch(e:Exception) {


                    Timber.e(
                        "Capture error ${e.message}"
                    )

                }


            },

            null

        )


    }








    private fun imageToBitmap(
        image: android.media.Image
    ): Bitmap? {


        return try {


            val plane =
                image.planes[0]


            val buffer =
                plane.buffer


            val pixelStride =
                plane.pixelStride


            val rowStride =
                plane.rowStride


            val rowPadding =
                rowStride -
                pixelStride *
                image.width



            val bitmap =
                Bitmap.createBitmap(

                    image.width +
                    rowPadding /
                    pixelStride,

                    image.height,

                    Bitmap.Config.ARGB_8888

                )



            buffer.rewind()


            bitmap.copyPixelsFromBuffer(
                buffer
            )



            Bitmap.createBitmap(
                bitmap,
                0,
                0,
                image.width,
                image.height
            ).also {

                bitmap.recycle()

            }


        } catch(e:Exception) {


            Timber.e(
                "Bitmap error ${e.message}"
            )


            null

        }


    }








    private suspend fun sendScreenshot(
        bitmap: Bitmap
    ) {


        try {


            val scaled =
                Bitmap.createScaledBitmap(

                    bitmap,

                    (bitmap.width * 0.6).toInt(),

                    (bitmap.height * 0.6).toInt(),

                    true

                )



            val stream =
                ByteArrayOutputStream()



            scaled.compress(

                Bitmap.CompressFormat.JPEG,

                65,

                stream

            )



            val bytes =
                stream.toByteArray()



            val encoded =
                Base64.encodeToString(

                    bytes,

                    Base64.NO_WRAP

                )





            val message =
                mapOf(

                    "type" to "screenshot",

                    "task_id" to "current_task",

                    "screenshot" to encoded,

                    "timestamp" to
                        System.currentTimeMillis()

                )



            webSocketClient.send(

                gson.toJson(message)

            )



            Timber.d(
                "Screenshot sent ${bytes.size / 1024} KB"
            )



            stream.close()


            scaled.recycle()

            bitmap.recycle()



        } catch(e:Exception) {


            Timber.e(
                "Send screenshot error ${e.message}"
            )


        }


    }








    fun stopCapture() {


        capturing = false


        virtualDisplay?.release()

        imageReader?.close()

        mediaProjection?.stop()



        virtualDisplay = null

        imageReader = null

        mediaProjection = null



        Timber.d(
            "Screen capture stopped"
        )


    }







    override fun onDestroy() {


        stopCapture()


        webSocketClient.disconnect()


        scope.cancel()



        super.onDestroy()


    }


}
