package com.robot.ai

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.robot.ai.network.WebSocketClient
import com.robot.ai.services.VoiceRecognitionService
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView
    private lateinit var listeningPrompt: TextView
    private lateinit var settingsButton: Button
    private lateinit var stopButton: Button

    private var webSocketClient: WebSocketClient? = null
    private var voiceService: VoiceRecognitionService? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initializeViews()
        setupWebSocket()
        setupButtons()

        updateStatus("Initializing...")
    }

    private fun initializeViews() {
        statusText = findViewById(R.id.status_text)
        listeningPrompt = findViewById(R.id.listening_prompt)
        settingsButton = findViewById(R.id.settings_button)
        stopButton = findViewById(R.id.stop_button)
    }

    private fun setupWebSocket() {
        webSocketClient = WebSocketClient(
            backendUrl = "ws://192.168.1.100:8000/ws/agent",
            onMessageReceived = { message ->
                runOnUiThread {
                    Timber.d("Backend message: $message")
                    updateStatus("Message received")
                }
            },
            onConnected = {
                runOnUiThread {
                    updateStatus("Connected to AI backend")
                }
            },
            onDisconnected = {
                runOnUiThread {
                    updateStatus("Disconnected")
                }
            },
            onError = { error ->
                runOnUiThread {
                    updateStatus("Error: $error")
                }
            }
        )

        webSocketClient?.connect()
    }

    private fun setupButtons() {
        settingsButton.setOnClickListener {
            updateStatus("Settings opened")
        }

        stopButton.setOnClickListener {
            stopAgent()
        }
    }

    private fun startVoice() {
        try {
            val intent = Intent(this, VoiceRecognitionService::class.java)
            startService(intent)

            updateStatus("Listening...")
            listeningPrompt.text = "Listening..."
        } catch (e: Exception) {
            Timber.e("Voice start error: ${e.message}")
        }
    }

    private fun stopAgent() {
        try {
            val intent = Intent(this, VoiceRecognitionService::class.java)
            stopService(intent)

            webSocketClient?.disconnect()

            updateStatus("Stopped")
            listeningPrompt.text = "Say something..."

        } catch (e: Exception) {
            Timber.e("Stop error: ${e.message}")
        }
    }

    private fun updateStatus(message: String) {
        statusText.text = message
        Timber.d(message)
    }

    override fun onStart() {
        super.onStart()
        startVoice()
    }

    override fun onDestroy() {
        webSocketClient?.disconnect()
        super.onDestroy()
    }
}
