package com.robot.ai.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.robot.ai.automation.ActionCoordinator
import timber.log.Timber


/**
 * Handles all messages received from AI backend.
 *
 * Backend -> Android communication layer.
 *
 * Supported:
 * - action
 * - actions
 * - voice
 * - task_update
 * - screenshot_request
 * - ping
 */
class WebSocketMessageHandler(
    private val actionCoordinator: ActionCoordinator
) {


    private val gson = Gson()



    /**
     * Process incoming websocket message
     */
    fun handleMessage(
        message: String
    ): String {


        return try {


            Timber.d(
                "Incoming WS message: ${message.take(300)}"
            )


            val json =
                gson.fromJson(
                    message,
                    JsonObject::class.java
                )


            val type =
                json.get("type")
                    ?.asString
                    ?: "unknown"



            Timber.d(
                "Message type: $type"
            )



            when(type) {



                // ----------------------------
                // Single AI action
                // ----------------------------

                "action" -> {


                    val actionJson =
                        gson.toJson(
                            json.get("action")
                        )


                    val result =
                        actionCoordinator.executeAction(
                            actionJson
                        )



                    gson.toJson(
                        mapOf(

                            "type" to "action_result",

                            "success" to result.success,

                            "message" to result.message

                        )
                    )
                }





                // ----------------------------
                // Multiple AI actions
                // ----------------------------

                "actions" -> {


                    val actions =
                        json.getAsJsonArray(
                            "actions"
                        )



                    if(actions == null) {


                        return error(
                            "Missing actions array"
                        )

                    }



                    val results =
                        actions.map {


                            val actionJson =
                                gson.toJson(it)



                            actionCoordinator
                                .executeAction(
                                    actionJson
                                )

                        }



                    val success =
                        results.all {
                            it.success
                        }



                    gson.toJson(

                        mapOf(

                            "type" to "actions_result",

                            "success" to success,

                            "completed" to
                                    results.count {
                                        it.success
                                    },

                            "total" to results.size

                        )

                    )

                }





                // ----------------------------
                // AI wants device voice output
                // ----------------------------

                "speak" -> {


                    val text =
                        json.get("text")
                            ?.asString
                            ?: ""



                    gson.toJson(

                        mapOf(

                            "type" to "speech_received",

                            "text" to text

                        )

                    )

                }





                // ----------------------------
                // Backend asks for screenshot
                // ----------------------------

                "screenshot_request" -> {


                    gson.toJson(

                        mapOf(

                            "type" to "screenshot_ready"

                        )

                    )

                }





                // ----------------------------
                // Task update
                // ----------------------------

                "task_update" -> {


                    Timber.d(
                        "Task update received"
                    )


                    gson.toJson(

                        mapOf(

                            "type" to "task_ack",

                            "received" to true

                        )

                    )

                }





                // ----------------------------
                // Future memory system
                // ----------------------------

                "memory_update" -> {


                    Timber.d(
                        "Memory update received"
                    )


                    gson.toJson(

                        mapOf(

                            "type" to "memory_ack",

                            "saved" to true

                        )

                    )

                }





                // ----------------------------
                // Heartbeat
                // ----------------------------

                "ping" -> {


                    gson.toJson(

                        mapOf(

                            "type" to "pong",

                            "timestamp" to
                                    System.currentTimeMillis()

                        )

                    )

                }





                // ----------------------------
                // Status request
                // ----------------------------

                "status" -> {


                    gson.toJson(

                        mapOf(

                            "type" to "status",

                            "agent_status" to "ready",

                            "timestamp" to
                                    System.currentTimeMillis()

                        )

                    )

                }





                else -> {


                    Timber.w(
                        "Unknown message type: $type"
                    )


                    error(
                        "Unknown message type: $type"
                    )

                }

            }



        } catch(e: Exception) {


            Timber.e(
                "Message handling error: ${e.message}"
            )


            error(
                e.message ?: "Unknown error"
            )

        }

    }





    private fun error(
        message:String
    ):String {


        return gson.toJson(

            mapOf(

                "type" to "error",

                "message" to message

            )

        )

    }

}
