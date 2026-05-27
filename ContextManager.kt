package com.robot.ai.ai

import com.robot.ai.models.Action
import timber.log.Timber

/**
 * Analyzes screen state and maintains context for decision making
 */
class ContextManager {

    private var conversationHistory = mutableListOf<Pair<String, String>>()
    private var lastScreenState = ""
    private var currentAppPackage = ""
    private var taskStartTime = 0L

    /**
     * Update screen context
     */
    fun updateScreenContext(screenDescription: String) {
        lastScreenState = screenDescription
        Timber.d("Screen context updated")
    }

    /**
     * Update current app
     */
    fun setCurrentApp(packageName: String) {
        currentAppPackage = packageName
        Timber.d("Current app: $packageName")
    }

    /**
     * Add message to conversation history
     */
    fun addConversationMessage(userMessage: String, aiResponse: String) {
        conversationHistory.add(userMessage to aiResponse)
        
        // Keep last 10 messages
        if (conversationHistory.size > 10) {
            conversationHistory.removeAt(0)
        }
        
        Timber.d("Conversation recorded")
    }

    /**
     * Get recent context for AI
     */
    fun getContextString(): String {
        val builder = StringBuilder()
        
        builder.append("=== AI Context ===\n")
        builder.append("Current Screen: $lastScreenState\n")
        builder.append("Current App: $currentAppPackage\n")
        builder.append("Time Running: ${(System.currentTimeMillis() - taskStartTime) / 1000}s\n")
        
        if (conversationHistory.isNotEmpty()) {
            builder.append("\n=== Recent Conversation ===\n")
            conversationHistory.takeLast(3).forEach { (user, ai) ->
                builder.append("User: $user\n")
                builder.append("AI: $ai\n")
            }
        }
        
        return builder.toString()
    }

    /**
     * Clear context for new task
     */
    fun clearContext() {
        conversationHistory.clear()
        lastScreenState = ""
        currentAppPackage = ""
        taskStartTime = System.currentTimeMillis()
        Timber.d("Context cleared")
    }

    /**
     * Get conversation history
     */
    fun getConversationHistory(): List<Pair<String, String>> = conversationHistory.toList()
}
