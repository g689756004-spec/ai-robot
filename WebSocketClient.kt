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
import timber.log.Timber
import java.util.Locale


class VoiceRecognitionService : Service(), RecognitionListener {


    private var speechRecognizer: SpeechRecognizer? = null

    private var textToSpeech: TextToSpeech? = null


    private lateinit var webSocketClient: WebSocketClient


    private val gson = Gson()


    private var isListening = false



    override fun onCreate() {

        super.onCreate()


        setupWebSocket()

        setupSpeechRecognizer()

        setupTextToSpeech()


        Timber.d(
            "Voice service started"
        )

    }





    override fun onBind(
        intent: Intent?
    ): IBinder? {

        return null

    }







    private fun setupWebSocket(){



        webSocketClient =
            WebSocketClient(

                backendUrl =
                "wss://YOUR_RENDER_APP.onrender.com/ws/agent",


                onMessageReceived = { message ->

                    handleServerMessage(message)

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


                onError = {

                    Timber.e(
                        "Websocket error: $it"
                    )

                }

            )



        webSocketClient.connect()


    }








    private fun handleServerMessage(
        message:String
    ){


        try {


            val json =
                gson.fromJson(
                    message,
                    JsonObject::class.java
                )



            val type =
                json.get("type")
                    ?.asString




            when(type){



                "chat_response" -> {


                    val reply =
                        json.get("message")
                            ?.asString



                    if(
                        !reply.isNullOrEmpty()
                    ){

                        speak(reply)

                    }


                }



                else -> {


                    Timber.d(
                        "Unknown server message: $message"
                    )

                }


            }



        }catch(e:Exception){


            Timber.e(
                "Message parsing error ${e.message}"
            )


        }


    }









    private fun setupSpeechRecognizer(){



        if(
            !SpeechRecognizer
                .isRecognitionAvailable(this)
        ){

            Timber.e(
                "Speech recognition unavailable"
            )

            return

        }



        speechRecognizer =
            SpeechRecognizer
                .createSpeechRecognizer(this)



        speechRecognizer
            ?.setRecognitionListener(this)


    }









    private fun setupTextToSpeech(){



        textToSpeech =
            TextToSpeech(this){ status ->



                if(
                    status ==
                    TextToSpeech.SUCCESS
                ){


                    textToSpeech
                        ?.language =
                        Locale.US



                    Timber.d(
                        "TTS ready"
                    )

                }


            }


    }









    fun startListening(){


        if(isListening)
            return



        isListening = true


        listen()


    }









    private fun listen(){



        if(
            speechRecognizer == null
        ){

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



        speechRecognizer
            ?.startListening(intent)



        Timber.d(
            "Listening..."
        )


    }









    fun stopListening(){


        isListening = false


        speechRecognizer
            ?.stopListening()



    }









    private fun sendVoiceCommand(
        text:String
    ){



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









    fun speak(
        text:String
    ){



        Timber.d(
            "AI speaking: $text"
        )



        textToSpeech
            ?.speak(

                text,

                TextToSpeech.QUEUE_FLUSH,

                null,

                "AI_REPLY"

            )


    }









    override fun onResults(
        results:Bundle?
    ){



        val matches =
            results
                ?.getStringArrayList(
                    SpeechRecognizer.RESULTS_RECOGNITION
                )



        if(
            !matches.isNullOrEmpty()
        ){


            sendVoiceCommand(
                matches[0]
            )


        }



        if(isListening){

            listen()

        }


    }









    override fun onError(
        error:Int
    ){


        Timber.e(
            "Speech error: $error"
        )



        if(isListening){


            Thread.sleep(500)


            listen()


        }


    }







    override fun onReadyForSpeech(
        params:Bundle?
    ){

        Timber.d(
            "Ready"
        )

    }



    override fun onBeginningOfSpeech(){

        Timber.d(
            "Speech started"
        )

    }



    override fun onEndOfSpeech(){

        Timber.d(
            "Speech ended"
        )

    }



    override fun onRmsChanged(
        rmsdB:Float
    ){}



    override fun onBufferReceived(
        buffer:ByteArray?
    ){}



    override fun onPartialResults(
        partialResults:Bundle?
    ){}



    override fun onEvent(
        eventType:Int,
        params:Bundle?
    ){}









    override fun onDestroy(){


        isListening = false



        speechRecognizer
            ?.destroy()



        textToSpeech
            ?.shutdown()



        webSocketClient.destroy()



        Timber.d(
            "Voice service stopped"
        )



        super.onDestroy()


    }


}
