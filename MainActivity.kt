package com.robot.ai

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.robot.ai.databinding.ActivityMainBinding
import com.robot.ai.services.ScreenCaptureService
import com.robot.ai.services.VoiceRecognitionService
import timber.log.Timber


class MainActivity : AppCompatActivity() {


    private lateinit var binding: ActivityMainBinding


    private val mediaProjectionLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->


            if (result.resultCode == RESULT_OK && result.data != null) {


                val serviceIntent =
                    Intent(
                        this,
                        ScreenCaptureService::class.java
                    )


                serviceIntent.putExtra(
                    "resultCode",
                    result.resultCode
                )


                serviceIntent.putExtra(
                    "data",
                    result.data
                )


                startForegroundService(
                    serviceIntent
                )


                updateStatus(
                    "Screen capture active"
                )


            } else {


                Toast.makeText(
                    this,
                    "Screen capture permission denied",
                    Toast.LENGTH_SHORT
                ).show()

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


        requestAccessibilityService()


        requestScreenCapture()


    }




    private fun setupUI() {


        binding.settingsButton.setOnClickListener {


            startActivity(
                Intent(
                    Settings.ACTION_ACCESSIBILITY_SETTINGS
                )
            )


        }



        binding.stopButton.setOnClickListener {


            stopAgent()


        }


    }




    private fun startVoiceService() {


        try {


            val intent =
                Intent(
                    this,
                    VoiceRecognitionService::class.java
                )


            startForegroundService(
                intent
            )


            updateStatus(
                "Voice AI running"
            )


            Timber.d(
                "Voice service started"
            )


        } catch(e:Exception) {


            Timber.e(
                "Voice service error ${e.message}"
            )

        }


    }




    private fun requestScreenCapture() {


        try {


            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager



            val intent =
                manager.createScreenCaptureIntent()



            mediaProjectionLauncher.launch(
                intent
            )


        } catch(e:Exception) {


            Timber.e(
                "Screen capture error ${e.message}"
            )


        }


    }





    private fun requestAccessibilityService() {


        val enabled =
            Settings.Secure.getString(
                contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )


        if(
            enabled == null ||
            !enabled.contains(
                packageName
            )
        ) {


            Toast.makeText(
                this,
                "Enable AI Robot Accessibility Service",
                Toast.LENGTH_LONG
            ).show()


        }


    }




    private fun stopAgent() {


        try {


            stopService(
                Intent(
                    this,
                    VoiceRecognitionService::class.java
                )
            )


            stopService(
                Intent(
                    this,
                    ScreenCaptureService::class.java
                )
            )


            updateStatus(
                "Agent stopped"
            )


        } catch(e:Exception) {


            Timber.e(
                "Stop error ${e.message}"
            )


        }


    }





    private fun updateStatus(
        text:String
    ) {


        runOnUiThread {


            binding.statusText.text =
                text


            binding.listeningPrompt.text =
                text


        }


    }




    override fun onDestroy() {


        stopAgent()


        super.onDestroy()

    }

}
