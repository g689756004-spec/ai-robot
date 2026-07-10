package com.robot.ai.services

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robot.ai.network.WebSocketClient
import timber.log.Timber
import java.util.Locale

class VoiceRecognitionService : Service(), RecognitionListener {

    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private lateinit var webSocketClient: WebSocketClient

    private val gson = Gson()

    private var isListening = false

    private val handler = Handler(Looper.getMainLooper())


    override fun onCreate() {
        super.onCreate()

        setupWebSocket()
        setupSpeechRecognizer()
        setupTextToSpeech()

        Timber.d("VoiceRecognitionService started")
    }


    override fun onBind(intent: Intent?): IBinder? {
        return null
    }


    private fun setupWebSocket() {

        webSocketClient = WebSocketClient(

            onMessageReceived = { message ->

                try {

                    val json =
                        gson.fromJson(
                            message,
                            JsonObject::class.java
                        )


                    val type =
                        json.get("type")?.asString


                    when(type) {


                        "chat_response" -> {

                            val response =
                                json.get("message")?.asString


                            if(!response.isNullOrEmpty()) {

                                speak(response)

                            }

                        }


                        else -> {

                            Timber.d(
                                "Ignoring websocket message: $message"
                            )

                        }

                    }


                } catch(e: Exception) {

                    Timber.e(
                        "WebSocket message error: ${e.message}"
                    )

                }

            }

        )


        webSocketClient.connect()

        Timber.d("WebSocket connected")

    }



    private fun setupSpeechRecognizer() {


        if(!SpeechRecognizer.isRecognitionAvailable(this)) {

            Timber.e(
                "Speech recognition unavailable"
            )

            return
        }


        speechRecognizer =
            SpeechRecognizer.createSpeechRecognizer(this)


        speechRecognizer?.setRecognitionListener(this)


        Timber.d(
            "Speech recognizer ready"
        )

    }




    private fun setupTextToSpeech() {


        textToSpeech =
            TextToSpeech(this) { status ->


                if(status == TextToSpeech.SUCCESS) {


                    textToSpeech?.language =
                        Locale.US


                    Timber.d(
                        "TextToSpeech ready"
                    )

                }
                else {

                    Timber.e(
                        "TextToSpeech initialization failed"
                    )

                }

            }

    }




    fun startListening() {


        if(isListening) {
            return
        }


        isListening = true

        startSpeechRecognition()

    }




    private fun startSpeechRecognition() {


        if(!isListening) {
            return
        }


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


        intent.putExtra(
            RecognizerIntent.EXTRA_PARTIAL_RESULTS,
            false
        )


        speechRecognizer?.startListening(intent)


        Timber.d(
            "Listening..."
        )

    }





    fun stopListening() {


        isListening = false


        speechRecognizer?.stopListening()


        Timber.d(
            "Stopped listening"
        )

    }





    private fun sendVoiceCommand(text: String) {


        val message = mapOf(

            "type" to "voice_command",

            "message" to text,

            "timestamp" to System.currentTimeMillis()

        )


        val json =
            gson.toJson(message)


        webSocketClient.send(json)


        Timber.d(
            "Voice sent: $text"
        )

    }





    fun speak(text: String) {


        Timber.d(
            "AI speaking: $text"
        )


        textToSpeech?.speak(

            text,

            TextToSpeech.QUEUE_FLUSH,

            null,

            "AI_RESPONSE"

        )

    }






    override fun onResults(results: Bundle?) {


        val matches =
            results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )


        if(!matches.isNullOrEmpty()) {


            val spokenText =
                matches[0]


            Timber.d(
                "User said: $spokenText"
            )


            sendVoiceCommand(
                spokenText
            )

        }


        if(isListening) {

            startSpeechRecognition()

        }

    }





    override fun onError(error: Int) {


        Timber.e(
            "Speech error: $error"
        )


        if(isListening) {


            handler.postDelayed(

                {

                    startSpeechRecognition()

                },

                500

            )

        }

    }





    override fun onReadyForSpeech(params: Bundle?) {

        Timber.d(
            "Ready for speech"
        )

    }


    override fun onBeginningOfSpeech() {

        Timber.d(
            "Speech detected"
        )

    }


    override fun onEndOfSpeech() {

        Timber.d(
            "Speech ended"
        )

    }


    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(
        eventType: Int,
        params: Bundle?
    ) {}





    override fun onDestroy() {


        isListening = false


        speechRecognizer?.destroy()


        textToSpeech?.shutdown()


        if(::webSocketClient.isInitialized) {

            webSocketClient.disconnect()

        }


        Timber.d(
            "Voice service destroyed"
        )


        super.onDestroy()

    }

}
