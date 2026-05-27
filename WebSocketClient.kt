package com.robot.ai.network

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import timber.log.Timber
import java.util.concurrent.TimeUnit

class WebSocketClient(
    private val backendUrl: String = "ws://192.168.1.100:8000/ws/agent",
    private val onMessageReceived: (String) -> Unit = {},
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private val client = OkHttpClient.Builder()
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private var isConnected = false

    fun connect() {
        val request = Request.Builder()
            .url(backendUrl)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: okhttp3.Response) {
                Timber.d("WebSocket connected")
                isConnected = true
                onConnected()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Timber.d("Received: $text")
                onMessageReceived(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: okhttp3.Response?) {
                Timber.e("WebSocket error: ${t.message}")
                isConnected = false
                onError(t.message ?: "Unknown error")
                onDisconnected()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Timber.d("WebSocket closed: $code - $reason")
                isConnected = false
                onDisconnected()
            }
        }

        webSocket = client.newWebSocket(request, listener)
    }

    fun send(message: String): Boolean {
        return if (isConnected && webSocket != null) {
            webSocket!!.send(message)
            true
        } else {
            Timber.w("WebSocket not connected")
            false
        }
    }

    fun disconnect() {
        webSocket?.close(1000, "Disconnecting")
        isConnected = false
    }

    fun isConnectedToBackend(): Boolean = isConnected
}
