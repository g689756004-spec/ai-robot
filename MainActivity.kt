package com.robot.ai

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.widget.Button
import android.widget.TextView
import android.view.View
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var listeningIndicator: View
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (permission, isGranted) ->
            if (isGranted) {
                Timber.d("Permission granted: $permission")
            } else {
                Timber.w("Permission denied: $permission")
            }
        }
        initializeApp()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Timber for logging
        if (!Timber.forest().isEmpty()) {
            Timber.uprootAll()
        }
        Timber.plant(Timber.DebugTree())

        // Bind views
        statusText = findViewById(R.id.status_text)
        listeningIndicator = findViewById(R.id.listening_indicator)
        settingsButton = findViewById(R.id.settings_button)
        stopButton = findViewById(R.id.stop_button)

        // Setup listeners
        settingsButton.setOnClickListener {
            Timber.d("Settings clicked")
            // TODO: Open settings dialog
        }

        stopButton.setOnClickListener {
            Timber.d("Stop clicked")
            stopAllServices()
        }

        // Request permissions
        requestRequiredPermissions()
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.INTERNET,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.MODIFY_AUDIO_SETTINGS,
            Manifest.permission.VIBRATE,
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        val permissionsNeeded = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsNeeded.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsNeeded.toTypedArray())
        } else {
            initializeApp()
        }
    }

    private fun initializeApp() {
        Timber.d("App initialized with permissions")
        updateUI()
    }

    private fun updateUI() {
        statusText.text = "AI Robot Ready ✓"
        listeningIndicator.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark)
        )
    }

    private fun stopAllServices() {
        statusText.text = "Stopping services..."
        Timber.d("Stopping all services")
    }
}
