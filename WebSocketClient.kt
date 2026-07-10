package com.robot.ai.network

import kotlinx.coroutines.*
import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

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

    fun connect() {

        Timber.d("Connecting to $backendUrl")

        val request = Request.Builder()
            .url(backendUrl)
            .build()

        webSocket = client.newWebSocket(
            request,
            socketListener
        )
    }

    private val socketListener =
        object : WebSocketListener() {

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
                    "Message received: ${text.take(200)}"
                )

                try {

                    onMessageReceived(text)

                } catch(e:Exception){

                    Timber.e(
                        "Message handler error ${e.message}"
                    )
                }
            }


            override fun onClosing(
                webSocket: WebSocket,
                code:Int,
                reason:String
            ){

                Timber.d(
                    "Closing websocket $code $reason"
                )

                webSocket.close(
                    1000,
                    null
                )
            }


            override fun onClosed(
                webSocket:WebSocket,
                code:Int,
                reason:String
            ){

                Timber.d(
                    "Websocket closed"
                )

                connected.set(false)

                onDisconnected()

                if(shouldReconnect){
                    reconnect()
                }
            }


            override fun onFailure(
                webSocket:WebSocket,
                t:Throwable,
                response:Response?
            ){

                Timber.e(
                    "Websocket failure ${t.message}"
                )

                connected.set(false)

                onError(
                    t.message ?: "Unknown websocket error"
                )

                if(shouldReconnect){
                    reconnect()
                }
            }
        }


    fun send(message:String):Boolean {

        if(!connected.get()){

            Timber.w(
                "Cannot send. Websocket offline"
            )

            return false
        }


        return try {

            webSocket?.send(message) ?: false

        }catch(e:Exception){

            Timber.e(
                "Send failed ${e.message}"
            )

            false
        }
    }


    fun sendJson(json:String){

        send(json)

    }


    fun sendScreenshot(
        base64:String,
        taskId:String="current_task"
    ){

        val message =
            """
            {
              "type":"screenshot",
              "task_id":"$taskId",
              "screenshot":"$base64",
              "timestamp":${System.currentTimeMillis()}
            }
            """.trimIndent()


        send(message)
    }


    fun sendVoiceCommand(
        text:String
    ){

        val message =
            """
            {
              "type":"voice_command",
              "message":"$text",
              "timestamp":${System.currentTimeMillis()}
            }
            """.trimIndent()


        send(message)
    }


    fun isConnectedToBackend():Boolean{

        return connected.get()

    }


    private fun reconnect(){

        reconnectAttempts++

        val delayTime =
            minOf(
                30000L,
                reconnectAttempts * 3000L
            )


        Timber.d(
            "Reconnect in ${delayTime}ms"
        )


        scope.launch {

            delay(delayTime)


            if(
                shouldReconnect &&
                !connected.get()
            ){

                connect()

            }
        }
    }


    fun reconnectNow(){

        disconnect()

        shouldReconnect=true

        connect()

    }


    fun disconnect(){

        Timber.d(
            "Disconnect websocket"
        )

        shouldReconnect=false

        connected.set(false)


        webSocket?.close(
            1000,
            "Client disconnect"
        )

        webSocket=null
    }


    fun destroy(){

        disconnect()

        scope.cancel()

        client.dispatcher
            .executorService
            .shutdown()
    }
}
