package com.robot.ai.services


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robot.ai.R
import com.robot.ai.network.WebSocketClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.Locale



class VoiceRecognitionService :
    Service(),
    RecognitionListener {



    private val binder =
        LocalBinder()



    inner class LocalBinder : Binder() {

        fun getService():
                VoiceRecognitionService {

            return this@VoiceRecognitionService

        }

    }







    private var speechRecognizer:
            SpeechRecognizer? = null



    private var textToSpeech:
            TextToSpeech? = null



    private lateinit var webSocketClient:
            WebSocketClient



    private val gson =
        Gson()



    private val scope =
        CoroutineScope(
            Dispatchers.Main
        )



    private var restartJob:
            Job? = null



    private var listening =
        false



    private var ttsReady =
        false








    override fun onCreate() {

        super.onCreate()


        Timber.d(
            "Voice service started"
        )



        createNotificationChannel()


        startForeground(
            1001,
            createNotification()
        )



        setupWebSocket()

        setupSpeechRecognizer()

        setupTextToSpeech()


    }









    override fun onBind(
        intent: Intent?
    ): IBinder {


        return binder

    }









    private fun setupWebSocket() {


        webSocketClient =
            WebSocketClient(


                backendUrl =
                "wss://YOUR_RENDER_APP.onrender.com/ws/agent",



                onMessageReceived = {

                    handleMessage(it)

                },



                onConnected = {

                    Timber.d(
                        "WebSocket connected"
                    )

                },



                onDisconnected = {

                    Timber.d(
                        "WebSocket disconnected"
                    )

                },



                onError = {

                    Timber.e(
                        "WebSocket error $it"
                    )

                }


            )



        webSocketClient.connect()

    }









    private fun handleMessage(
        message:String
    ) {


        try {


            val json =
                gson.fromJson(
                    message,
                    JsonObject::class.java
                )



            when(
                json.get("type")
                    ?.asString
            ) {



                "chat_response" -> {


                    val text =
                        json.get("message")
                            ?.asString



                    if(
                        !text.isNullOrEmpty()
                    ) {

                        speak(text)

                    }


                }



                "speak" -> {


                    val text =
                        json.get("text")
                            ?.asString



                    if(
                        !text.isNullOrEmpty()
                    ) {

                        speak(text)

                    }

                }



            }



        }
        catch(e:Exception) {


            Timber.e(
                "Message error ${e.message}"
            )


        }


    }









    private fun setupSpeechRecognizer() {


        if(
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ) {


            Timber.e(
                "Speech recognition unavailable"
            )


            return

        }



        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(
                    this
                )



        speechRecognizer
            ?.setRecognitionListener(
                this
            )

    }









    private fun setupTextToSpeech() {


        textToSpeech =
            TextToSpeech(this) {


                if(
                    it ==
                    TextToSpeech.SUCCESS
                ) {


                    textToSpeech
                        ?.language =
                        Locale.US



                    ttsReady =
                        true



                    Timber.d(
                        "TTS ready"
                    )


                }


            }

    }









    fun startListening() {


        if(
            listening
        ) return



        listening =
            true



        listen()

    }









    fun stopListening() {


        listening =
            false



        speechRecognizer
            ?.stopListening()


    }









    private fun listen() {


        if(
            !listening
        ) return



        val intent =
            Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            )



        intent.putExtra(

            RecognizerIntent.EXTRA_LANGUAGE_MODEL,

            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM

        )



        intent.putExtra(

            RecognizerIntent.EXTRA_LANGUAGE,

            Locale.US

        )



        speechRecognizer
            ?.startListening(intent)



        Timber.d(
            "Listening"
        )


    }









    private fun sendVoice(
        text:String
    ) {


        val data =
            mapOf(

                "type" to "voice_command",

                "message" to text,

                "timestamp" to
                    System.currentTimeMillis()

            )



        webSocketClient.send(
            gson.toJson(data)
        )


    }









    fun speak(
        text:String
    ) {


        if(
            !ttsReady
        ) return



        textToSpeech
            ?.speak(

                text,

                TextToSpeech.QUEUE_FLUSH,

                null,

                "AI_REPLY"

            )


    }









    override fun onResults(
        results: Bundle?
    ) {


        val matches =
            results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )



        if(
            !matches.isNullOrEmpty()
        ) {


            sendVoice(
                matches[0]
            )


        }



        restartListening()

    }









    private fun restartListening() {


        if(
            !listening
        ) return



        restartJob =
            scope.launch {


                delay(800)


                listen()


            }


    }









    override fun onError(
        error:Int
    ) {


        Timber.e(
            "Speech error $error"
        )



        restartListening()

    }



    override fun onReadyForSpeech(
        params:Bundle?
    ) {}



    override fun onBeginningOfSpeech() {}



    override fun onEndOfSpeech() {}



    override fun onRmsChanged(
        rmsdB:Float
    ) {}



    override fun onBufferReceived(
        buffer:ByteArray?
    ) {}



    override fun onPartialResults(
        partialResults:Bundle?
    ) {}



    override fun onEvent(
        eventType:Int,
        params:Bundle?
    ) {}









    private fun createNotificationChannel() {


        val channel =
            NotificationChannel(

                "voice_agent",

                "AI Voice Agent",

                NotificationManager.IMPORTANCE_LOW

            )



        val manager =
            getSystemService(
                NotificationManager::class.java
            )


        manager.createNotificationChannel(
            channel
        )


    }









    private fun createNotification():
            Notification {


        return NotificationCompat.Builder(

            this,

            "voice_agent"

        )

            .setContentTitle(
                "AI Robot Listening"
            )

            .setContentText(
                "Voice service running"
            )

            .setSmallIcon(
                R.drawable.ic_launcher_foreground
            )

            .build()


    }









    override fun onDestroy() {


        listening =
            false



        restartJob?.cancel()



        speechRecognizer
            ?.destroy()



        textToSpeech
            ?.shutdown()



        webSocketClient.destroy()



        super.onDestroy()


    }



}
