package com.robot.ai.services

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.os.Build
import android.speech.*
import android.speech.tts.TextToSpeech
import com.google.gson.Gson
import com.robot.ai.network.WebSocketClient
import timber.log.Timber
import java.util.Locale


class VoiceRecognitionService : Service(),
    RecognitionListener {


    private var speechRecognizer: SpeechRecognizer? = null
    private var textToSpeech: TextToSpeech? = null

    private var listening = false

    private lateinit var webSocketClient: WebSocketClient

    private val gson = Gson()



    override fun onBind(intent: Intent?): IBinder? {
        return null
    }



    override fun onCreate() {
        super.onCreate()

        setupWebSocket()

        setupSpeech()

        Timber.d("Voice service started")
    }





    private fun setupWebSocket() {


        webSocketClient = WebSocketClient(

            onMessageReceived = { message ->


                try {

                    val json =
                        gson.fromJson(
                            message,
                            Map::class.java
                        )


                    if(json["type"] == "chat_response") {


                        val reply =
                            json["message"] as? String


                        reply?.let {

                            speak(it)

                        }

                    }


                } catch(e:Exception){

                    Timber.e(
                        "Voice response error ${e.message}"
                    )

                }


            }

        )


        webSocketClient.connect()

    }





    private fun setupSpeech(){


        if(
            SpeechRecognizer.isRecognitionAvailable(this)
        ){


            speechRecognizer =
                SpeechRecognizer.createSpeechRecognizer(this)


            speechRecognizer?.setRecognitionListener(this)


        }



        textToSpeech =
            TextToSpeech(this){ status ->


                if(
                    status ==
                    TextToSpeech.SUCCESS
                ){

                    textToSpeech?.language =
                        Locale.US

                    Timber.d(
                        "TTS initialized"
                    )

                }


            }


    }







    fun startListening(){


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



        speechRecognizer?.startListening(intent)


        listening = true


        Timber.d(
            "Listening..."
        )


    }






    fun stopListening(){


        speechRecognizer?.stopListening()

        listening = false


    }








    private fun sendToAI(text:String){


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
            "Sent voice: $text"
        )


    }








    fun speak(text:String){


        Timber.d(
            "Speaking: $text"
        )


        if(
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.LOLLIPOP
        ){


            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "AI_REPLY"
            )


        }else{


            @Suppress("DEPRECATION")

            textToSpeech?.speak(
                text,
                TextToSpeech.QUEUE_FLUSH,
                null
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
        ){

            sendToAI(
                matches[0]
            )

        }



        if(listening){

            startListening()

        }


    }





    override fun onError(error:Int){

        Timber.e(
            "Speech error $error"
        )


        if(listening){

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







    override fun onDestroy(){


        speechRecognizer?.destroy()

        textToSpeech?.shutdown()

        webSocketClient.disconnect()


        super.onDestroy()

    }


}
