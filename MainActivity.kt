```kotlin
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

        Timber.plant(Timber.DebugTree())

        setContentView(R.layout.activity_main)

        initializeViews()

        checkMicrophonePermission()

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


        val permission =

            ContextCompat.checkSelfPermission(

                this,

                Manifest.permission.RECORD_AUDIO

            )



        if(
            permission != PackageManager.PERMISSION_GRANTED
        ) {


            ActivityCompat.requestPermissions(

                this,

                arrayOf(
                    Manifest.permission.RECORD_AUDIO
                ),

                microphonePermissionCode

            )


        }
        else {


            startVoiceService()


        }


    }








    private fun startVoiceService() {


        val serviceIntent =

            Intent(

                this,

                VoiceRecognitionService::class.java

            )



        ContextCompat.startForegroundService(

            this,

            serviceIntent

        )



        statusText.text =
            "Voice service ready"


    }








    private fun startListening() {


        val service =
            VoiceRecognitionService.instance



        if(service != null) {


            service.startListening()



            statusText.text =
                "Listening..."



            listeningPrompt.text =
                "🎤 Listening..."



        }
        else {


            statusText.text =
                "Voice service not ready"



        }


    }









    private fun stopListening() {


        val service =
            VoiceRecognitionService.instance



        if(service != null) {


            service.stopListening()



            statusText.text =
                "Stopped"



            listeningPrompt.text =
                "Say something..."



        }


    }









    override fun onRequestPermissionsResult(

        requestCode: Int,

        permissions: Array<out String>,

        grantResults: IntArray

    ) {


        super.onRequestPermissionsResult(

            requestCode,

            permissions,

            grantResults

        )



        if(
            requestCode == microphonePermissionCode
        ) {


            if(
                grantResults.isNotEmpty()
                &&
                grantResults[0] ==
                PackageManager.PERMISSION_GRANTED
            ) {


                statusText.text =
                    "Microphone permission granted"



                startVoiceService()



            }
            else {


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
```
