package com.robot.ai.models

import android.graphics.Bitmap

/**
 * Represents a single action to be executed on the Android device
 */
data class Action(
    val type: String, // "click", "scroll", "type", "wait", etc.
    val target: String?, // Description or coordinates
    val params: Map<String, Any>? = null
)

/**
 * Represents an AI analysis response from the backend
 */
data class ScreenAnalysis(
    val analysis: String, // What the AI sees
    val ui_elements: List<UIElement>?,
    val next_action: Action,
    val confidence: Double,
    val reasoning: String?
)

/**
 * Represents a UI element identified on the screen
 */
data class UIElement(
    val type: String, // "button", "text", "input", etc.
    val label: String?,
    val position: String?, // "top_left", "center", etc.
    val description: String?
)

/**
 * Represents a task being executed
 */
data class Task(
    val task_id: String,
    val goal: String,
    val user_command: String,
    val status: String, // "pending", "running", "completed", "failed"
    val steps: List<TaskStep>?
)

/**
 * Represents a single step in a multi-step task
 */
data class TaskStep(
    val step_number: Int,
    val description: String,
    val action: Action,
    val status: String,
    val result: String?,
    val timestamp: String?
)
