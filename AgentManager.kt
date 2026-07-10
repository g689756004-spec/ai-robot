package com.robot.ai.ai

import android.content.Context
import com.google.gson.Gson
import com.robot.ai.network.WebSocketClient
import com.robot.ai.network.WebSocketMessageHandler
import com.robot.ai.automation.ActionCoordinator
import timber.log.Timber
import java.util.UUID

class AgentManager(
    private val context: Context
) {

    private val gson = Gson()

    private val taskPlanner = TaskPlanner()

    private lateinit var webSocketClient: WebSocketClient

    private lateinit var messageHandler: WebSocketMessageHandler

    private var running = false


    fun initialize(){

        Timber.d("Initializing AI Agent")


        val actionCoordinator =
            ActionCoordinator(context)


        messageHandler =
            WebSocketMessageHandler(
                actionCoordinator
            )


        webSocketClient =
            WebSocketClient(
                onMessageReceived = { message ->

                    handleBackendMessage(
                        message
                    )

                },

                onConnected = {

                    Timber.d(
                        "Agent connected to AI backend"
                    )

                    sendStatus()

                },

                onDisconnected = {

                    Timber.w(
                        "Agent disconnected"
                    )

                },

                onError = { error ->

                    Timber.e(
                        "Websocket error $error"
                    )

                }
            )


        webSocketClient.connect()

        running=true

    }




    fun sendVoiceCommand(
        text:String
    ){

        if(!running)
            return


        val taskId =
            UUID.randomUUID()
                .toString()


        taskPlanner.createTask(
            taskId,
            text,
            text
        )


        val message =
            mapOf(

                "type" to "voice_command",

                "task_id" to taskId,

                "message" to text,

                "timestamp" to
                    System.currentTimeMillis()

            )


        webSocketClient.send(
            gson.toJson(message)
        )


        Timber.d(
            "Voice command sent $text"
        )

    }





    fun sendScreenshot(
        image:String
    ){

        if(!running)
            return


        val message =
            mapOf(

                "type" to "screenshot",

                "task_id" to
                    (
                    taskPlanner
                        .getCurrentTask()
                        ?.task_id
                        ?: "current_task"
                    ),

                "screenshot" to image,

                "timestamp" to
                    System.currentTimeMillis()

            )


        webSocketClient.send(
            gson.toJson(message)
        )


        Timber.d(
            "Screenshot sent"
        )

    }





    private fun handleBackendMessage(
        message:String
    ){

        try {


            val response =
                messageHandler
                    .handleMessage(
                        message
                    )


            if(response.isNotEmpty()){

                webSocketClient.send(
                    response
                )

            }


        }catch(e:Exception){

            Timber.e(
                "Backend message error ${e.message}"
            )

        }

    }





    private fun sendStatus(){

        val status =
            mapOf(

                "type" to "status",

                "agent_status" to "ready",

                "timestamp" to
                    System.currentTimeMillis()

            )


        webSocketClient.send(
            gson.toJson(status)
        )

    }





    fun getTaskPlanner():TaskPlanner{

        return taskPlanner

    }





    fun isRunning():Boolean{

        return running

    }





    fun stop(){

        Timber.d(
            "Stopping Agent Manager"
        )


        running=false


        if(::webSocketClient.isInitialized){

            webSocketClient.disconnect()

        }

    }


}
