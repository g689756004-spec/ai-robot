package com.robot.ai

import android.content.Context
import android.content.Intent
import com.google.gson.Gson
import com.robot.ai.network.WebSocketClient
import com.robot.ai.network.WebSocketMessageHandler
import com.robot.ai.automation.ActionCoordinator
import com.robot.ai.services.ScreenCaptureService
import com.robot.ai.services.VoiceRecognitionService
import com.robot.ai.ai.TaskPlanner
import timber.log.Timber


/**
 * Main AI Agent Controller
 *
 * Controls:
 *
 * Voice
 * Screen Vision
 * Tasks
 * Actions
 * Backend connection
 *
 */
class AIAgent(
    private val context: Context
) {


    private val gson = Gson()


    private lateinit var webSocketClient: WebSocketClient


    private lateinit var taskPlanner: TaskPlanner


    private lateinit var actionCoordinator: ActionCoordinator


    private lateinit var messageHandler:
            WebSocketMessageHandler



    private var initialized = false





    fun initialize(){


        if(initialized)
            return



        Timber.d(
            "Initializing AI Agent..."
        )



        taskPlanner =
            TaskPlanner()



        actionCoordinator =
            ActionCoordinator(
                context
            )



        messageHandler =
            WebSocketMessageHandler(
                actionCoordinator
            )



        setupWebSocket()



        initialized = true


        Timber.d(
            "AI Agent ready"
        )

    }








    private fun setupWebSocket(){


        webSocketClient =
            WebSocketClient(

                onMessageReceived = { message ->


                    Timber.d(
                        "Backend message: $message"
                    )



                    val response =
                        messageHandler
                            .handleMessage(message)



                    if(response.isNotEmpty()){


                        webSocketClient.send(
                            response
                        )


                    }


                },



                onConnected = {


                    Timber.d(
                        "Connected to AI backend"
                    )


                    sendStatus()


                },



                onDisconnected = {


                    Timber.w(
                        "Backend disconnected"
                    )


                },



                onError = {


                    Timber.e(
                        "Backend error: $it"
                    )


                }

            )


        webSocketClient.connect()


    }









    fun startVoice(){


        Timber.d(
            "Starting voice"
        )


        val intent =
            Intent(
                context,
                VoiceRecognitionService::class.java
            )


        context.startService(intent)


    }








    fun startScreenCapture(){


        Timber.d(
            "Starting screen capture"
        )



        val intent =
            Intent(
                context,
                ScreenCaptureService::class.java
            )


        context.startService(intent)


    }









    fun createTask(
        taskId:String,
        goal:String,
        command:String
    ){


        val task =
            taskPlanner.createTask(
                taskId,
                command,
                goal
            )



        Timber.d(
            "Task created ${task.task_id}"
        )



        val message =
            mapOf(

                "type" to "task_created",

                "task" to task

            )


        webSocketClient.send(
            gson.toJson(message)
        )


    }










    private fun sendStatus(){


        val status =
            mapOf(

                "type" to "status",

                "agent" to "android_robot",

                "ready" to true,

                "timestamp" to
                    System.currentTimeMillis()

            )


        webSocketClient.send(
            gson.toJson(status)
        )


    }









    fun sendCommand(command:String){



        val message =
            mapOf(

                "type" to "command",

                "message" to command

            )



        webSocketClient.send(
            gson.toJson(message)
        )


    }








    fun stop(){


        Timber.d(
            "Stopping AI Agent"
        )


        try {


            context.stopService(
                Intent(
                    context,
                    VoiceRecognitionService::class.java
                )
            )


            context.stopService(
                Intent(
                    context,
                    ScreenCaptureService::class.java
                )
            )



        }catch(e:Exception){

            Timber.e(
                "Stop error ${e.message}"
            )

        }



        if(::webSocketClient.isInitialized){

            webSocketClient.disconnect()

        }


        initialized = false


    }








    fun isReady():Boolean{

        return initialized &&
                webSocketClient.isConnectedToBackend()

    }



}
