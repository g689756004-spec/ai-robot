package com.robot.ai.network


import okhttp3.*
import timber.log.Timber
import java.util.concurrent.TimeUnit



class WebSocketClient(

    private val backendUrl: String =
        "wss://YOUR_RENDER_APP.onrender.com/ws/agent",


    private val onMessageReceived: (String) -> Unit = {},


    private val onConnected: () -> Unit = {},


    private val onDisconnected: () -> Unit = {},


    private val onError: (String) -> Unit = {}

) {



    private val client: OkHttpClient =

        OkHttpClient.Builder()

            .connectTimeout(
                30,
                TimeUnit.SECONDS
            )

            .readTimeout(
                0,
                TimeUnit.MILLISECONDS
            )

            .writeTimeout(
                30,
                TimeUnit.SECONDS
            )

            .build()



    private var webSocket: WebSocket? = null


    private var connected = false





    fun connect() {


        if(connected) {

            Timber.d(
                "WebSocket already connected"
            )

            return

        }



        val request =

            Request.Builder()

                .url(backendUrl)

                .build()





        webSocket =

            client.newWebSocket(

                request,

                object : WebSocketListener(){



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
                            "WS message: ${text.take(200)}"
                        )


                        onMessageReceived(
                            text
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
                            "WebSocket error: ${t.message}"
                        )


                        onError(
                            t.message
                                ?: "Unknown websocket error"
                        )

                    }


                }

            )

    }








    fun send(

        message: String

    ) {


        if(!connected) {


            Timber.w(
                "Cannot send. WebSocket not connected"
            )


            return

        }




        val success =

            webSocket?.send(
                message
            )
                ?: false




        Timber.d(

            "WebSocket send result: $success"

        )


    }









    fun disconnect(){


        connected = false



        webSocket?.close(

            1000,

            "Client disconnect"

        )


        webSocket = null



        Timber.d(
            "WebSocket disconnected"
        )

    }









    fun destroy(){


        disconnect()


        client.dispatcher
            .executorService
            .shutdown()


        client.connectionPool
            .evictAll()



    }








    fun isConnected(): Boolean {


        return connected

    }


}
