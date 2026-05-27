package com.robot.ai.automation

import android.content.Context
import com.google.gson.Gson
import com.robot.ai.models.Action
import timber.log.Timber

/**
 * Main action coordinator that routes actions to appropriate handlers
 */
class ActionCoordinator(
    private val context: Context,
    private val actionExecutor: ActionExecutor,
    private val browserAutomator: BrowserAutomator? = null
) {

    private val gson = Gson()
    private val browserTaskExecutor = browserAutomator?.let { BrowserTaskExecutor(it) }

    /**
     * Execute an action from backend
     */
    fun executeAction(actionJson: String): ExecutionResult {
        return try {
            val action = gson.fromJson(actionJson, Action::class.java)
            executeAction(action)
        } catch (e: Exception) {
            Timber.e("Error parsing action: ${e.message}")
            ExecutionResult(false, "Parse error: ${e.message}")
        }
    }

    /**
     * Execute an action object
     */
    fun executeAction(action: Action): ExecutionResult {
        return try {
            val success = when {
                // Browser actions
                action.type.startsWith("browser_") -> {
                    if (browserTaskExecutor == null) {
                        ExecutionResult(false, "Browser not initialized")
                    } else {
                        val browserAction = action.type.substring(8) // Remove "browser_"
                        val browserTask = BrowserTask(
                            action = browserAction,
                            query = action.params?.get("query") as? String,
                            url = action.params?.get("url") as? String,
                            selector = action.params?.get("selector") as? String,
                            text = action.params?.get("text") as? String,
                            direction = action.params?.get("direction") as? String,
                            amount = (action.params?.get("amount") as? Number)?.toInt()
                        )
                        val result = browserTaskExecutor.executeTask(browserTask)
                        ExecutionResult(result, if (result) "Browser action executed" else "Browser action failed")
                    }
                }
                
                // Native UI actions
                else -> {
                    val interpreter = ActionInterpreter(context)
                    val result = interpreter.executeAction(action)
                    ExecutionResult(result, if (result) "Action executed" else "Action failed")
                }
            }
            
            success as ExecutionResult
        } catch (e: Exception) {
            Timber.e("Error executing action: ${e.message}")
            ExecutionResult(false, "Execution error: ${e.message}")
        }
    }

    /**
     * Execute a sequence of actions
     */
    fun executeSequence(actions: List<Action>): List<ExecutionResult> {
        val results = mutableListOf<ExecutionResult>()
        
        for ((index, action) in actions.withIndex()) {
            Timber.d("Executing action ${index + 1}/${actions.size}: ${action.type}")
            val result = executeAction(action)
            results.add(result)
            
            if (!result.success) {
                Timber.w("Action failed, continuing with next")
            }
            
            // Small delay between actions
            Thread.sleep(200)
        }
        
        return results
    }
}

/**
 * Result of executing an action
 */
data class ExecutionResult(
    val success: Boolean,
    val message: String
)
