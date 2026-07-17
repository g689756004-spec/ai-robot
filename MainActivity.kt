package com.robot.ai


import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.content.ServiceConnection
import android.os.IBinder
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.robot.ai.databinding.ActivityMainBinding
import com.robot.ai.services.VoiceRecognitionService
import timber.log.Timber



class MainActivity : AppCompatActivity() {



    private lateinit var binding: ActivityMainBinding



    private var voiceService:
            VoiceRecognitionService? = null



    private var serviceBound = false






    private val serviceConnection =
        object : ServiceConnection {



            override fun onServiceConnected(

                name: ComponentName?,

                service: IBinder?

            ) {


                /*
                 *
                 * VoiceRecognitionService
                 * currently returns null binder.
                 *
                 * This is kept here because
                 * we will upgrade it later
                 * to a LocalBinder.
                 *
                 */



                serviceBound = true



                Timber.d(
                    "Voice service connected"
                )


            }








            override fun onServiceDisconnected(

                name: ComponentName?

            ) {


                serviceBound = false


                voiceService = null



                Timber.d(
                    "Voice service disconnected"
                )


            }


        }









    override fun onCreate(
        savedInstanceState: Bundle?
    ) {


        super.onCreate(
            savedInstanceState
        )



        binding =
            ActivityMainBinding.inflate(
                layoutInflater
            )



        setContentView(
            binding.root
        )



        setupUI()



        startVoiceService()


    }









    private fun setupUI() {


        binding.statusText.text =
            "Ready"




        binding.listeningPrompt.text =
            "Press Start Listening"






        binding.settingsButton.setOnClickListener {


            startListening()


        }






        binding.stopButton.setOnClickListener {


            stopListening()


        }



    }









    private fun startVoiceService() {


        val intent =
            Intent(
                this,
                VoiceRecognitionService::class.java
            )



        ContextCompat.startForegroundService(

            this,

            intent

        )



        bindService(

            intent,

            serviceConnection,

            BIND_AUTO_CREATE

        )


    }









    private fun startListening() {


        binding.statusText.text =
            "Listening..."



        binding.listeningPrompt.text =
            "Say something..."



        Timber.d(
            "Start listening pressed"
        )



        /*
         *
         * The service currently
         * does not expose a binder.
         *
         * Once we add LocalBinder,
         * this will directly call:
         *
         * voiceService?.startListening()
         *
         */


    }









    private fun stopListening() {


        binding.statusText.text =
            "Stopped"



        binding.listeningPrompt.text =
            "Listening paused"



        Timber.d(
            "Stop listening pressed"
        )



        /*
         *
         * Will call:
         *
         * voiceService?.stopListening()
         *
         * after binder update.
         *
         */


    }









    fun updateUserText(
        text:String
    ) {


        runOnUiThread {


            binding.userText.text =
                text


        }


    }









    fun updateAIResponse(
        text:String
    ) {


        runOnUiThread {


            binding.aiResponse.text =
                text


        }


    }









    override fun onDestroy() {


        if(serviceBound){


            unbindService(
                serviceConnection
            )


            serviceBound = false


        }



        super.onDestroy()


    }



}
