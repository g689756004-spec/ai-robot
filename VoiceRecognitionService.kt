package com.robot.ai.services


import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.app.NotificationCompat

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robot.ai.network.WebSocketClient

import timber.log.Timber

import java.util.Locale





class VoiceRecognitionService : Service(),
    RecognitionListener {



    companion object {

        var instance: VoiceRecognitionService? = null

    }




    private var speechRecognizer: SpeechRecognizer? = null


    private var textToSpeech: TextToSpeech? = null



    private lateinit var webSocketClient: WebSocketClient



    private val gson = Gson()



    private val handler =
        Handler(
            Looper.getMainLooper()
        )



    private var isListening = false





    override fun onCreate() {

        super.onCreate()


        instance = this


        startForegroundService()



        setupWebSocket()


        setupSpeechRecognizer()


        setupTextToSpeech()



        Timber.d(
            "Voice Recognition Service started"
        )


    }








    private fun startForegroundService(){


        val channelId =
            "voice_agent_channel"



        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O){


            val channel =
                NotificationChannel(

                    channelId,

                    "AI Robot Voice",

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




        val notification =

            NotificationCompat.Builder(

                this,

                channelId

            )

                .setContentTitle(
                    "AI Robot Voice Agent"
                )

                .setContentText(
                    "Listening service active"
                )

                .setSmallIcon(
                    android.R.drawable.ic_btn_speak_now
                )

                .build()



        startForeground(

            1,

            notification

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


                onMessageReceived = {

                    message ->

                    handleServerMessage(
                        message
                    )

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


                    error ->


                    Timber.e(
                        "WebSocket error: $error"
                    )


                }

            )



        webSocketClient.connect()


    }









    private fun handleServerMessage(
        message:String
    ){


        try{


            val json =

                gson.fromJson(

                    message,

                    JsonObject::class.java

                )



            when(

                json.get("type")
                    ?.asString

            ){


                "chat_response" -> {


                    val reply =

                        json.get("message")
                            ?.asString



                    if(!reply.isNullOrEmpty()){


                        speak(
                            reply
                        )


                    }


                }



                else -> {


                    Timber.d(
                        "Server message: $message"
                    )


                }


            }



        }catch(e:Exception){


            Timber.e(
                "Message error ${e.message}"
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
                .createSpeechRecognizer(
                    this
                )



        speechRecognizer
            ?.setRecognitionListener(
                this
            )


    }









    private fun setupTextToSpeech(){



        textToSpeech =

            TextToSpeech(

                this

            ){ status ->



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



        if(!isListening)
            return



        val intent =

            Intent(

                RecognizerIntent
                    .ACTION_RECOGNIZE_SPEECH

            )



        intent.putExtra(

            RecognizerIntent
                .EXTRA_LANGUAGE_MODEL,

            RecognizerIntent
                .LANGUAGE_MODEL_FREE_FORM

        )



        intent.putExtra(

            RecognizerIntent
                .EXTRA_LANGUAGE,

            Locale.US

        )



        intent.putExtra(

            RecognizerIntent
                .EXTRA_PARTIAL_RESULTS,

            false

        )



        speechRecognizer
            ?.startListening(
                intent
            )



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



        val message = mapOf(

            "type" to "voice_command",

            "message" to text,

            "timestamp" to
            System.currentTimeMillis()

        )



        webSocketClient.send(

            gson.toJson(
                message
            )

        )



        Timber.d(
            "Voice sent: $text"
        )


    }









    fun speak(
        text:String
    ){



        Timber.d(
            "AI speaking: $text"
        )



        textToSpeech?.speak(

            text,

            TextToSpeech
                .QUEUE_FLUSH,

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

                    SpeechRecognizer
                        .RESULTS_RECOGNITION

                )



        if(!matches.isNullOrEmpty()){


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


            handler.postDelayed(

                {

                    listen()

                },

                700

            )


        }


    }








    override fun onReadyForSpeech(
        params:Bundle?
    ) {

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



        instance = null



        isListening = false



        speechRecognizer
            ?.destroy()



        textToSpeech
            ?.shutdown()



        if(::webSocketClient.isInitialized){


            webSocketClient.destroy()


        }



        Timber.d(
            "Voice service destroyed"
        )



        super.onDestroy()


    }


}
