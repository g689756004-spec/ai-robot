package com.robot.ai

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.robot.ai.ai.AIAgent
import com.robot.ai.ai.ContextManager
import com.robot.ai.ai.TaskPlanner
import com.robot.ai.automation.*
import com.robot.ai.models.Action
import com.robot.ai.models.ScreenAnalysis
import com.robot.ai.network.WebSocketClient
import com.robot.ai.network.WebSocketMessageHandler
import com.robot.ai.services.VoiceRecognitionService
import kotlinx.coroutines.*
import timber.log.Timber

/**
 * Main AI Robot controller that orchestrates all components
 */
class RobotController(
    private val context: Context,
    private val webView: WebView
) : ViewModel() {

    // Core components
    private val actionExecutor = ActionExecutor(context)
    private val browserAutomator = BrowserAutomator(webView)
    private val actionCoordinator = ActionCoordinator(context, actionExecutor, browserAutomator)
    private val messageHandler = WebSocketMessageHandler(actionCoordinator)
    
    // AI components
    private val aiAgent = AIAgent()
    private val taskPlanner = TaskPlanner()
    private val contextManager = ContextManager()
    
    // Communication
    private var webSocketClient: WebSocketClient? = null
    private var voiceService: VoiceRecognitionService? = null
    
    // State
    private var isRunning = false
    private var analysisCoroutine: Job? = null
    
    private val gson = Gson()

    /**
     * Initialize the robot
     */
    fun initialize(
        backendUrl: String,
        onConnected: () -> Unit = {},
        onDisconnected: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        Timber.d("Initializing AI Robot Controller")
        
        // Setup WebSocket
        webSocketClient = WebSocketClient(
            backendUrl = backendUrl,
            onConnected = {
                Timber.d("Connected to backend")
                onConnected()
            },
            onDisconnected = {
                Timber.d("Disconnected from backend")
                stopAnalysis()
                onDisconnected()
            },
            onError = { error ->
                Timber.e("Backend error: $error")
                onError(error)
            },
            onMessageReceived = { message ->
                handleBackendMessage(message)
            }
        )
        
        webSocketClient?.connect()
        Timber.d("Robot initialized")
    }

    /**
     * Process voice command
     */
    fun processVoiceCommand(command: String) {
        Timber.d("Voice command: $command")
        
        viewModelScope.launch {
            try {
                // Create task
                val taskId = "voice_${System.currentTimeMillis()}"
                taskPlanner.createTask(taskId, command, command)
                contextManager.clearContext()
                
                // Add to conversation
                contextManager.addConversationMessage(command, "Processing...")
                
                // Start analysis
                startAnalysis()
                
            } catch (e: Exception) {
                Timber.e("Error processing voice command: ${e.message}")
            }
        }
    }

    /**
     * Start real-time screen analysis
     */
    private fun startAnalysis() {
        if (isRunning) return
        
        isRunning = true
        Timber.d("Starting real-time analysis")
        
        analysisCoroutine = viewModelScope.launch {
            while (isActive && isRunning) {
                try {
                    // Get current screenshot (placeholder)
                    val screenshot: Bitmap? = null // Would come from ScreenCaptureService
                    
                    if (screenshot != null) {
                        // Analyze with backend
                        val analysis = aiAgent.analyzeScreenshot(
                            screenshot,
                            contextManager.getContextString()
                        )
                        
                        if (analysis != null) {
                            // Update context
                            contextManager.updateScreenContext(analysis.analysis)
                            
                            // Execute action if confident
                            if (analysis.confidence > 0.6) {
                                val result = actionCoordinator.executeAction(analysis.next_action)
                                
                                if (result.success) {
                                    taskPlanner.completeStep(
                                        taskPlanner.getCurrentTask()?.steps?.size ?: 1,
                                        "Executed: ${analysis.reasoning}"
                                    )
                                } else {
                                    taskPlanner.failStep(
                                        taskPlanner.getCurrentTask()?.steps?.size ?: 1,
                                        result.message
                                    )
                                }
                            } else {
                                Timber.w("Low confidence (${analysis.confidence}), awaiting user input")
                            }
                        }
                    }
                    
                    delay(2000) // Wait 2 seconds between analyses
                    
                } catch (e: Exception) {
                    Timber.e("Analysis error: ${e.message}")
                    delay(2000)
                }
            }
        }
    }

    /**
     * Stop analysis loop
     */
    fun stopAnalysis() {
        isRunning = false
        analysisCoroutine?.cancel()
        Timber.d("Analysis stopped")
    }

    /**
     * Handle message from backend
     */
    private fun handleBackendMessage(message: String) {
        try {
            Timber.d("Backend message received")
            val response = messageHandler.handleMessage(message)
            
            // Send response back to backend
            webSocketClient?.send(response)
            
        } catch (e: Exception) {
            Timber.e("Error handling backend message: ${e.message}")
        }
    }

    /**
     * Execute manual action
     */
    fun executeAction(action: Action) {
        viewModelScope.launch {
            val result = actionCoordinator.executeAction(action)
            Timber.d("Action executed: ${result.message}")
        }
    }

    /**
     * Get task status
     */
    fun getTaskStatus(): String {
        return taskPlanner.getProgress()
    }

    /**
     * Get context info
     */
    fun getContextInfo(): String {
        return contextManager.getContextString()
    }

    /**
     * Shutdown robot
     */
    fun shutdown() {
        Timber.d("Shutting down AI Robot")
        stopAnalysis()
        webSocketClient?.disconnect()
        aiAgent.stopAnalysisLoop()
    }

    override fun onCleared() {
        super.onCleared()
        shutdown()
    }
}
