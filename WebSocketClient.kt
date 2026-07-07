package com.robot.ai.network

import kotlinx.coroutines.*
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * WebSocket client for communication between Android AI Agent and backend.
 *
 * Handles:
 * - Connecting to AI backend
 * - Sending screenshots
 * - Receiving AI actions
 * - Automatic reconnect
 * - Connection state tracking
 */
class WebSocketClient(
    private val backendUrl: String = "ws://192.168.1.100:8000/ws/agent",
    private val onMessageReceived: (String) -> Unit = {},
    private val onConnected: () -> Unit = {},
    private val onDisconnected: () -> Unit = {},
    private val onError: (String) -> Unit = {}
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()


    private var webSocket: WebSocket? = null

    private val scope = CoroutineScope(
        Dispatchers.IO + SupervisorJob()
    )


    private val connected = AtomicBoolean(false)

    private var shouldReconnect = true


    private var reconnectAttempts = 0


    /**
     * Connect to backend websocket
     */
    fun connect() {

        Timber.d("Connecting to backend: $backendUrl")


        val request = Request.Builder()
            .url(backendUrl)
            .build()


        webSocket = client.newWebSocket(
            request,
            socketListener
        )
    }



    private val socketListener = object : WebSocketListener() {


        override fun onOpen(
            webSocket: WebSocket,
            response: Response
        ) {

            Timber.d("WebSocket connected")

            connected.set(true)

            reconnectAttempts = 0

            onConnected()
        }



        override fun onMessage(
            webSocket: WebSocket,
            text: String
        ) {

            Timber.d(
                "Backend message: ${text.take(200)}"
            )

            try {

                onMessageReceived(text)

            } catch (e: Exception) {

                Timber.e(
                    "Message handler error: ${e.message}"
                )
            }
        }



        override fun onClosing(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {

            Timber.d(
                "Closing websocket: $code $reason"
            )

            webSocket.close(
                1000,
                null
            )
        }



        override fun onClosed(
            webSocket: WebSocket,
            code: Int,
            reason: String
        ) {

            Timber.d(
                "WebSocket closed: $reason"
            )

            connected.set(false)

            onDisconnected()


            if (shouldReconnect) {
                reconnect()
            }
        }



        override fun onFailure(
            webSocket: WebSocket,
            t: Throwable,
            response: Response?
        ) {

            Timber.e(
                "WebSocket failure: ${t.message}"
            )


            connected.set(false)


            onError(
                t.message ?: "Unknown websocket error"
            )


            if (shouldReconnect) {
                reconnect()
            }
        }
    }




    /**
     * Send text message
     */
    fun send(message: String): Boolean {


        if (!connected.get()) {

            Timber.w(
                "Cannot send. Websocket disconnected"
            )

            return false
        }


        return try {

            webSocket?.send(message) ?: false

        } catch (e: Exception) {

            Timber.e(
                "Send error: ${e.message}"
            )

            false
        }
    }




    /**
     * Send JSON object
     */
    fun sendJson(json: String) {

        send(json)

    }




    /**
     * Automatic reconnect
     */
    private fun reconnect() {


        reconnectAttempts++


        val delayTime =
            minOf(
                30000L,
                reconnectAttempts * 3000L
            )


        Timber.d(
            "Reconnecting in ${delayTime}ms"
        )


        scope.launch {


            delay(delayTime)


            if (shouldReconnect &&
                !connected.get()
            ) {

                connect()
            }
        }
    }





    /**
     * Check connection
     */
    fun isConnectedToBackend(): Boolean {

        return connected.get()

    }





    /**
     * Disconnect permanently
     */
    fun disconnect() {


        Timber.d(
            "Disconnecting websocket"
        )


        shouldReconnect = false


        connected.set(false)


        webSocket?.close(
            1000,
            "Client disconnect"
        )


        webSocket = null
    }





    /**
     * Restart connection
     */
    fun reconnectNow() {


        disconnect()

        shouldReconnect = true

        connect()

    }





    fun destroy() {


        disconnect()

        scope.cancel()

        client.dispatcher.executorService.shutdown()

    }

}
