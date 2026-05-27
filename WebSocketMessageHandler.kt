package com.robot.ai.network

import com.google.gson.Gson
import com.robot.ai.automation.ActionCoordinator
import timber.log.Timber

/**
 * Handles WebSocket messages from backend
 * Parses action commands and coordinates execution
 */
class WebSocketMessageHandler(private val actionCoordinator: ActionCoordinator) {

    private val gson = Gson()

    /**
     * Process incoming WebSocket message
     */
    fun handleMessage(message: String): String {
        return try {
            val json = gson.fromJson(message, Map::class.java)
            val messageType = json["type"] as? String ?: "unknown"
            
            Timber.d("Handling WebSocket message: $messageType")
            
            when (messageType) {
                "action" -> {
                    val actionJson = gson.toJson(json["action"])
                    val result = actionCoordinator.executeAction(actionJson)
                    
                    // Send back result
                    gson.toJson(mapOf(
                        "type" to "action_result",
                        "success" to result.success,
                        "message" to result.message
                    ))
                }
                
                "actions" -> {
                    // Multiple actions in sequence
                    @Suppress("UNCHECKED_CAST")
                    val actionsList = json["actions"] as? List<Map<String, Any>>
                    
                    if (actionsList != null) {
                        val results = actionsList.map { actionMap ->
                            val actionJson = gson.toJson(actionMap)
                            actionCoordinator.executeAction(actionJson)
                        }
                        
                        val allSuccess = results.all { it.success }
                        gson.toJson(mapOf(
                            "type" to "actions_result",
                            "success" to allSuccess,
                            "count" to results.size,
                            "message" to "${results.filter { it.success }.size}/${results.size} actions succeeded"
                        ))
                    } else {
                        gson.toJson(mapOf(
                            "type" to "error",
                            "message" to "Invalid actions format"
                        ))
                    }
                }
                
                "ping" -> {
                    gson.toJson(mapOf("type" to "pong"))
                }
                
                "status" -> {
                    gson.toJson(mapOf(
                        "type" to "status",
                        "agent_status" to "ready",
                        "timestamp" to System.currentTimeMillis()
                    ))
                }
                
                else -> {
                    Timber.w("Unknown message type: $messageType")
                    gson.toJson(mapOf(
                        "type" to "error",
                        "message" to "Unknown message type: $messageType"
                    ))
                }
            }
        } catch (e: Exception) {
            Timber.e("Error handling message: ${e.message}")
            gson.toJson(mapOf(
                "type" to "error",
                "message" to "Error: ${e.message}"
            ))
        }
    }
}
