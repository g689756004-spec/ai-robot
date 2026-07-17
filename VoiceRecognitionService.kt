package com.robot.ai.services


import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.google.gson.JsonObject
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


    private var speechRecognizer: SpeechRecognizer? = null


    private var textToSpeech: TextToSpeech? = null


    private lateinit var webSocketClient: WebSocketClient


    private val gson = Gson()


    private val serviceScope =
        CoroutineScope(
            Dispatchers.Main
        )


    private var retryJob: Job? = null


    private var isListening = false


    private var ttsReady = false





    override fun onCreate() {

        super.onCreate()


        Timber.d(
            "Voice service created"
        )


        setupWebSocket()

        setupSpeechRecognizer()

        setupTextToSpeech()


    }







    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null

    }









    private fun setupWebSocket() {


        webSocketClient =
            WebSocketClient(


                backendUrl =
                "wss://YOUR_RENDER_APP.onrender.com/ws/agent",


                onMessageReceived = { message ->

                    handleServerMessage(
                        message
                    )

                },


                onConnected = {

                    Timber.d(
                        "Voice websocket connected"
                    )

                },


                onDisconnected = {

                    Timber.d(
                        "Voice websocket disconnected"
                    )

                },


                onError = { error ->

                    Timber.e(
                        "WebSocket error: $error"
                    )

                }

            )


        webSocketClient.connect()

    }









    private fun handleServerMessage(
        message:String
    ) {


        try {


            val json =
                gson.fromJson(
                    message,
                    JsonObject::class.java
                )


            val type =
                json.get("type")
                    ?.asString
                    ?: return




            when(type) {


                "chat_response" -> {


                    val reply =
                        json.get("message")
                            ?.asString



                    if(
                        !reply.isNullOrBlank()
                    ) {


                        speak(
                            reply
                        )

                    }


                }




                "speak" -> {


                    val text =
                        json.get("text")
                            ?.asString



                    if(
                        !text.isNullOrBlank()
                    ) {

                        speak(text)

                    }

                }




                else -> {


                    Timber.d(
                        "Unhandled message: $message"
                    )

                }


            }



        }
        catch(e:Exception) {


            Timber.e(
                "Message parse error: ${e.message}"
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



        createRecognizer()

    }









    private fun createRecognizer() {


        speechRecognizer?.destroy()



        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)



        speechRecognizer
            ?.setRecognitionListener(this)


    }









    private fun setupTextToSpeech() {


        textToSpeech =
            TextToSpeech(this) { status ->



                if(
                    status ==
                    TextToSpeech.SUCCESS
                ) {


                    val result =
                        textToSpeech
                            ?.setLanguage(
                                Locale.US
                            )



                    ttsReady =
                        result !=
                        TextToSpeech.LANG_MISSING_DATA &&
                        result !=
                        TextToSpeech.LANG_NOT_SUPPORTED



                    Timber.d(
                        "TTS ready: $ttsReady"
                    )

                }
                else {


                    Timber.e(
                        "TTS initialization failed"
                    )


                }


            }


    }









    fun startListening() {


        if(
            isListening
        ) return



        isListening = true



        startRecognizer()


    }









    private fun startRecognizer() {


        if(
            !isListening
        ) return



        val recognizer =
            speechRecognizer
                ?: return



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



        recognizer.startListening(
            intent
        )



        Timber.d(
            "Started listening"
        )


    }









    fun stopListening() {


        isListening = false


        retryJob?.cancel()



        speechRecognizer
            ?.stopListening()



        Timber.d(
            "Stopped listening"
        )

    }









    private fun sendVoiceCommand(
        text:String
    ) {


        if(
            !::webSocketClient.isInitialized
        ) {

            return

        }



        val payload =
            mapOf(

                "type" to "voice_command",

                "message" to text,

                "timestamp" to
                    System.currentTimeMillis()

            )



        webSocketClient.send(
            gson.toJson(payload)
        )



        Timber.d(
            "Voice sent: $text"
        )


    }









    fun speak(
        text:String
    ) {


        if(
            !ttsReady
        ) {


            Timber.w(
                "TTS not ready"
            )


            return

        }



        textToSpeech?.speak(

            text,

            TextToSpeech.QUEUE_FLUSH,

            null,

            "AI_RESPONSE"

        )



        Timber.d(
            "Speaking: $text"
        )


    }









    override fun onResults(
        results:Bundle?
    ) {


        val matches =
            results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )



        if(
            !matches.isNullOrEmpty()
        ) {


            sendVoiceCommand(
                matches[0]
            )


        }



        restartListening()

    }









    override fun onError(
        error:Int
    ) {


        Timber.e(
            "Speech error: $error"
        )



        restartListening()

    }









    private fun restartListening() {


        if(
            !isListening
        ) return



        retryJob?.cancel()



        retryJob =
            serviceScope.launch {


                delay(700)


                createRecognizer()

                startRecognizer()


            }


    }









    override fun onReadyForSpeech(
        params:Bundle?
    ) {


        Timber.d(
            "Ready for speech"
        )

    }



    override fun onBeginningOfSpeech() {


        Timber.d(
            "Speech started"
        )

    }



    override fun onEndOfSpeech() {


        Timber.d(
            "Speech ended"
        )

    }



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









    override fun onDestroy() {


        Timber.d(
            "Destroying voice service"
        )


        isListening = false



        retryJob?.cancel()



        speechRecognizer
            ?.destroy()



        textToSpeech
            ?.stop()



        textToSpeech
            ?.shutdown()



        if(
            ::webSocketClient.isInitialized
        ) {


            webSocketClient.destroy()


        }



        super.onDestroy()

    }


}
