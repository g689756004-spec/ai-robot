package com.robot.ai.network


import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit



class WebSocketClient(


    private val backendUrl: String,


    private val onMessageReceived: (String) -> Unit,


    private val onConnected: () -> Unit,


    private val onDisconnected: () -> Unit,


    private val onError: (String) -> Unit


) {



    private var webSocket: WebSocket? = null



    private val client: OkHttpClient =
        OkHttpClient.Builder()

            .connectTimeout(
                20,
                TimeUnit.SECONDS
            )

            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )

            .writeTimeout(
                20,
                TimeUnit.SECONDS
            )

            .pingInterval(
                30,
                TimeUnit.SECONDS
            )

            .build()






    private var connected = false






    fun connect() {


        Timber.d(
            "Connecting websocket: $backendUrl"
        )



        val request =
            Request.Builder()

                .url(
                    backendUrl
                )

                .build()






        webSocket =
            client.newWebSocket(

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


                connected = true



                Timber.d(
                    "WebSocket connected"
                )



                onConnected()

            }








            override fun onMessage(

                webSocket: WebSocket,

                text: String

            ) {


                Timber.d(
                    "WebSocket message: ${text.take(200)}"
                )



                onMessageReceived(
                    text
                )


            }








            override fun onClosing(

                webSocket: WebSocket,

                code: Int,

                reason: String

            ) {


                Timber.d(
                    "WebSocket closing: $reason"
                )



                connected = false



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


                connected = false



                Timber.d(
                    "WebSocket closed: $reason"
                )



                onDisconnected()


            }








            override fun onFailure(

                webSocket: WebSocket,

                t: Throwable,

                response: Response?

            ) {


                connected = false



                Timber.e(
                    "WebSocket failure: ${t.message}"
                )



                onError(
                    t.message
                        ?: "Unknown websocket error"
                )


            }


        }









    fun send(
        message:String
    ) {



        if(
            !connected
        ) {


            Timber.w(
                "Cannot send. WebSocket not connected"
            )


            return

        }





        val success =
            webSocket
                ?.send(
                    message
                )
                ?: false





        if(!success) {


            Timber.e(
                "Failed sending websocket message"
            )


        }


    }









    fun isConnected():Boolean {


        return connected

    }









    fun disconnect() {


        Timber.d(
            "Disconnecting websocket"
        )



        connected = false



        webSocket
            ?.close(

                1000,

                "Client disconnect"

            )



        webSocket = null


    }









    fun destroy() {


        disconnect()



        client
            .dispatcher
            .executorService
            .shutdown()



        client
            .connectionPool
            .evictAll()



        Timber.d(
            "WebSocket destroyed"
        )


    }



}
