package com.robot.ai.ai

import com.google.gson.Gson
import com.robot.ai.models.Action, Task
import timber.log.Timber

/**
 * Manages multi-step task planning and execution
 */
class TaskPlanner {

    private val gson = Gson()
    private var currentTask: Task? = null
    private val taskHistory = mutableListOf<Task>()

    /**
     * Create a new task from user command
     */
    fun createTask(
        taskId: String,
        userCommand: String,
        goal: String
    ): Task {
        val task = Task(
            task_id = taskId,
            goal = goal,
            user_command = userCommand,
            status = "pending",
            steps = mutableListOf()
        )
        
        currentTask = task
        taskHistory.add(task)
        Timber.d("Task created: $taskId - $goal")
        
        return task
    }

    /**
     * Add step to current task
     */
    fun addStep(
        description: String,
        action: Action
    ) {
        currentTask?.let {
            @Suppress("UNCHECKED_CAST")
            val steps = it.steps?.toMutableList() ?: mutableListOf()
            
            val step = com.robot.ai.models.TaskStep(
                step_number = steps.size + 1,
                description = description,
                action = action,
                status = "pending",
                result = null,
                timestamp = null
            )
            
            steps.add(step)
            Timber.d("Step added: $description")
        }
    }

    /**
     * Mark step as completed
     */
    fun completeStep(stepNumber: Int, result: String = "") {
        currentTask?.let {
            @Suppress("UNCHECKED_CAST")
            val steps = it.steps?.toMutableList() ?: mutableListOf()
            
            if (stepNumber > 0 && stepNumber <= steps.size) {
                val step = steps[stepNumber - 1]
                steps[stepNumber - 1] = com.robot.ai.models.TaskStep(
                    step_number = step.step_number,
                    description = step.description,
                    action = step.action,
                    status = "completed",
                    result = result,
                    timestamp = System.currentTimeMillis().toString()
                )
                Timber.d("Step $stepNumber completed")
            }
        }
    }

    /**
     * Mark step as failed
     */
    fun failStep(stepNumber: Int, error: String) {
        currentTask?.let {
            @Suppress("UNCHECKED_CAST")
            val steps = it.steps?.toMutableList() ?: mutableListOf()
            
            if (stepNumber > 0 && stepNumber <= steps.size) {
                val step = steps[stepNumber - 1]
                steps[stepNumber - 1] = com.robot.ai.models.TaskStep(
                    step_number = step.step_number,
                    description = step.description,
                    action = step.action,
                    status = "failed",
                    result = error,
                    timestamp = System.currentTimeMillis().toString()
                )
                Timber.d("Step $stepNumber failed: $error")
            }
        }
    }

    /**
     * Get current task
     */
    fun getCurrentTask(): Task? = currentTask

    /**
     * Get task progress
     */
    fun getProgress(): String {
        currentTask?.let { task ->
            val steps = task.steps ?: emptyList()
            val completed = steps.count { it.status == "completed" }
            return "$completed/${steps.size} steps completed"
        }
        return "No task"
    }

    /**
     * Complete current task
     */
    fun completeTask(result: String = "Task completed") {
        currentTask?.let {
            Timber.d("Task completed: ${it.task_id}")
            // Update status
            currentTask = null
        }
    }

    /**
     * Get all tasks
     */
    fun getTaskHistory(): List<Task> = taskHistory.toList()
}
