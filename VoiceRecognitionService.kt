package com.robot.ai.services

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Build
import android.speech.*
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat
import com.google.gson.Gson
import com.robot.ai.network.WebSocketClient
import timber.log.Timber
import java.util.Locale


class VoiceRecognitionService : Service(), RecognitionListener {


    private var speechRecognizer: SpeechRecognizer? = null

    private var textToSpeech: TextToSpeech? = null

    private lateinit var webSocketClient: WebSocketClient


    private val gson = Gson()


    private var listening = false



    override fun onBind(intent: Intent?): IBinder? {
        return null
    }



    override fun onCreate() {

        super.onCreate()

        createNotification()

        setupWebSocket()

        setupSpeech()

        Timber.d("Voice AI Service started")

    }



    private fun createNotification() {


        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {


            val notification =
                NotificationCompat.Builder(
                    this,
                    "AI_AGENT_CHANNEL"
                )
                    .setContentTitle("AI Robot Agent")
                    .setContentText("Listening for voice commands")
                    .setSmallIcon(
                        android.R.drawable.ic_btn_speak_now
                    )
                    .build()


            startForeground(
                1001,
                notification
            )

        }

    }




    private fun setupWebSocket() {


        webSocketClient =
            WebSocketClient(

                onMessageReceived = { message ->


                    try {


                        val json =
                            gson.fromJson(
                                message,
                                Map::class.java
                            )


                        when(json["type"]) {


                            "chat_response" -> {


                                val reply =
                                    json["message"]
                                        ?.toString()


                                reply?.let {

                                    speak(it)

                                }

                            }



                            "speak" -> {


                                val text =
                                    json["text"]
                                        ?.toString()


                                text?.let {

                                    speak(it)

                                }

                            }


                        }



                    } catch(e:Exception) {


                        Timber.e(
                            "Voice websocket error ${e.message}"
                        )

                    }


                }

            )


        webSocketClient.connect()


    }





    private fun setupSpeech() {


        if(
            SpeechRecognizer.isRecognitionAvailable(this)
        ) {


            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(
                    this
                )


            speechRecognizer?.setRecognitionListener(
                this
            )

        }



        textToSpeech =
            TextToSpeech(this) { status ->


                if(
                    status ==
                    TextToSpeech.SUCCESS
                ) {


                    textToSpeech?.language =
                        Locale.US


                    Timber.d(
                        "TTS ready"
                    )

                }


            }


    }





    fun startListening() {


        if(listening)
            return



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
            true
        )



        speechRecognizer?.startListening(
            intent
        )


        listening = true


        Timber.d(
            "Listening started"
        )


    }





    fun stopListening() {


        listening = false


        speechRecognizer?.stopListening()


        Timber.d(
            "Listening stopped"
        )


    }







    private fun sendToAI(text:String) {


        val message =
            mapOf(

                "type" to "voice_command",

                "message" to text,

                "timestamp" to
                    System.currentTimeMillis()

            )



        webSocketClient.send(
            gson.toJson(message)
        )


        Timber.d(
            "Voice sent: $text"
        )


    }







    fun speak(text:String) {


        Timber.d(
            "AI speaking: $text"
        )


        if(
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.LOLLIPOP
        ) {


            textToSpeech?.speak(

                text,

                TextToSpeech.QUEUE_FLUSH,

                null,

                "AI_RESPONSE"

            )


        }


    }







    override fun onResults(results: Bundle?) {


        val matches =
            results?.getStringArrayList(
                SpeechRecognizer.RESULTS_RECOGNITION
            )


        if(
            !matches.isNullOrEmpty()
        ) {


            sendToAI(
                matches[0]
            )


        }



        if(listening) {


            startListening()


        }


    }







    override fun onError(error:Int) {


        Timber.e(
            "Speech error: $error"
        )


        if(listening) {


            startListening()


        }


    }






    override fun onReadyForSpeech(params: Bundle?) {}

    override fun onBeginningOfSpeech() {}

    override fun onEndOfSpeech() {}

    override fun onRmsChanged(rmsdB: Float) {}

    override fun onBufferReceived(buffer: ByteArray?) {}

    override fun onPartialResults(partialResults: Bundle?) {}

    override fun onEvent(eventType: Int, params: Bundle?) {}







    override fun onDestroy() {


        listening = false


        speechRecognizer?.destroy()


        textToSpeech?.shutdown()


        webSocketClient.disconnect()


        super.onDestroy()


    }

}
