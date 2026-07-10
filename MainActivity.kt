package com.robot.ai

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.robot.ai.services.VoiceRecognitionService
import timber.log.Timber


class MainActivity : AppCompatActivity() {


    private lateinit var statusText: TextView
    private lateinit var listeningPrompt: TextView

    private lateinit var startButton: Button
    private lateinit var stopButton: Button


    private val microphonePermissionCode = 1001




    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)


        setContentView(R.layout.activity_main)


        initializeViews()


        checkMicrophonePermission()


        startVoiceService()


        Timber.plant(
            Timber.DebugTree()
        )

    }







    private fun initializeViews() {


        statusText =
            findViewById(
                R.id.status_text
            )


        listeningPrompt =
            findViewById(
                R.id.listening_prompt
            )


        startButton =
            findViewById(
                R.id.settings_button
            )


        stopButton =
            findViewById(
                R.id.stop_button
            )



        startButton.text =
            "Start Listening"



        stopButton.text =
            "Stop Listening"




        startButton.setOnClickListener {


            startListening()


        }





        stopButton.setOnClickListener {


            stopListening()


        }


    }









    private fun checkMicrophonePermission() {


        if(
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            )
            != PackageManager.PERMISSION_GRANTED
        ) {


            ActivityCompat.requestPermissions(

                this,

                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),

                microphonePermissionCode

            )

        }


    }









    private fun startVoiceService() {


        val intent =
            Intent(
                this,
                VoiceRecognitionService::class.java
            )


        startService(intent)



        statusText.text =
            "Voice service running"


    }









    private fun startListening() {


        statusText.text =
            "Listening..."


        listeningPrompt.text =
            "🎤 Listening..."



        // Service will keep listening

        Timber.d(
            "Start listening pressed"
        )

    }









    private fun stopListening() {


        statusText.text =
            "Stopped"


        listeningPrompt.text =
            "Say something..."



        Timber.d(
            "Stop listening pressed"
        )

    }









    override fun onRequestPermissionsResult(

        requestCode:Int,

        permissions:Array<out String>,

        grantResults:IntArray

    ) {


        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        )



        if(
            requestCode ==
            microphonePermissionCode
        ) {


            if(
                grantResults.isNotEmpty()
                &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {


                statusText.text =
                    "Microphone ready"


            } else {


                statusText.text =
                    "Microphone permission denied"


            }


        }


    }







    override fun onDestroy() {


        super.onDestroy()


        Timber.d(
            "MainActivity destroyed"
        )

    }

}
