package com.robot.ai.automation

import android.content.Context
import com.google.gson.Gson
import com.robot.ai.models.Action
import timber.log.Timber

/**
 * Interprets and executes actions received from the backend AI
 */
class ActionInterpreter(private val context: Context) {

    private val executor = ActionExecutor(context)
    private val gson = Gson()

    /**
     * Execute an action JSON from the backend
     */
    fun executeAction(actionJson: String): Boolean {
        return try {
            val action = gson.fromJson(actionJson, Action::class.java)
            executeAction(action)
        } catch (e: Exception) {
            Timber.e("Error parsing action: ${e.message}")
            false
        }
    }

    /**
     * Execute an action object
     */
    fun executeAction(action: Action): Boolean {
        return try {
            val result = when (action.type.lowercase()) {
                "click" -> {
                    val x = action.params?.get("x") ?: "center"
                    val y = action.params?.get("y") ?: "center"
                    executor.click(x, y)
                }
                "double_click" -> {
                    val x = action.params?.get("x") ?: "center"
                    val y = action.params?.get("y") ?: "center"
                    executor.doubleClick(x, y)
                }
                "long_press" -> {
                    val x = action.params?.get("x") ?: "center"
                    val y = action.params?.get("y") ?: "center"
                    val duration = (action.params?.get("duration") as? Number)?.toLong() ?: 500L
                    executor.longPress(x, y, duration)
                }
                "type" -> {
                    val text = action.params?.get("text") as? String ?: ""
                    executor.typeText(text)
                }
                "clear" -> executor.clearText()
                "scroll" -> {
                    val direction = action.params?.get("direction") as? String ?: "down"
                    val steps = (action.params?.get("steps") as? Number)?.toInt() ?: 3
                    executor.scroll(direction, steps)
                }
                "swipe" -> {
                    val startX = action.params?.get("start_x") ?: "center"
                    val startY = action.params?.get("start_y") ?: "center"
                    val endX = action.params?.get("end_x") ?: "center"
                    val endY = action.params?.get("end_y") ?: "center"
                    val duration = (action.params?.get("duration") as? Number)?.toInt() ?: 500
                    executor.swipe(startX, startY, endX, endY, duration)
                }
                "back" -> executor.pressBack()
                "home" -> executor.pressHome()
                "open_app" -> {
                    val packageName = action.params?.get("package") as? String ?: ""
                    executor.openApp(packageName)
                }
                "wait" -> {
                    val duration = (action.params?.get("duration") as? Number)?.toLong() ?: 1000L
                    Thread.sleep(duration)
                    true
                }
                "wait_for_element" -> {
                    val text = action.params?.get("text") as? String
                    val resourceId = action.params?.get("resource_id") as? String
                    val timeout = (action.params?.get("timeout") as? Number)?.toLong() ?: 5000L
                    
                    when {
                        text != null -> executor.waitForElementWithText(text, timeout)
                        resourceId != null -> executor.waitForElementWithId(resourceId, timeout)
                        else -> false
                    }
                }
                else -> {
                    Timber.w("Unknown action type: ${action.type}")
                    false
                }
            }
            
            if (result) {
                Timber.d("Action executed successfully: ${action.type}")
            } else {
                Timber.w("Action failed: ${action.type}")
            }
            
            result
        } catch (e: Exception) {
            Timber.e("Error executing action: ${e.message}")
            false
        }
    }

    /**
     * Execute a sequence of actions
     */
    fun executeSequence(actions: List<Action>): Boolean {
        var allSucceeded = true
        for ((index, action) in actions.withIndex()) {
            Timber.d("Executing action ${index + 1}/${actions.size}: ${action.type}")
            if (!executeAction(action)) {
                allSucceeded = false
                Timber.w("Action failed, continuing with next action")
            }
        }
        return allSucceeded
    }
}
