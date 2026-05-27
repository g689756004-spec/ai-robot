# Phase 3: AI Reasoning & Planning

## New Files Added

### AIAgent.kt (6KB)

Backend communication for screen analysis:

- `analyzeScreenshot(bitmap)` - Send screenshot, get AI analysis
- `extractText(bitmap)` - Extract visible text
- `analyzeLayout(bitmap)` - Understand page structure
- Automatic screenshot compression (70%)
- Multipart request building
- Async operations with coroutines

### TaskPlanner.kt (3KB)

Multi-step task management:

- `createTask()` - Start new task
- `addStep()` - Add action step
- `completeStep()` - Mark step done
- `failStep()` - Mark step failed
- `getProgress()` - Track completion
- Task history tracking

### ContextManager.kt (2KB)

Maintains AI context across interactions:

- Conversation history (last 10 messages)
- Screen state tracking
- Current app tracking
- Time tracking
- Context string generation for AI

## Integration Flow

```
User: "Play a chess video"
  ↓
VoiceRecognitionService captures
  ↓
MainActivity.onSpeechResult()
  ↓
TaskPlanner.createTask()
  ↓
AIAgent.analyzeScreenshot()
  ↓
Backend Claude Vision analyzes
  ↓
Plans: [
  - open YouTube,
  - search "chess",
  - click first video,
  - play
]
  ↓
ActionCoordinator.executeSequence()
  ↓
Each action updates screen
  ↓
AIAgent.analyzeScreenshot() again
  ↓
Verify: video playing?
  ↓
TaskPlanner.completeTask()
```

## Example: AI Analysis Response

```json
{
  "analysis": "I see Android home screen with Chrome icon visible",
  "ui_elements": [
    {
      "type": "app_icon",
      "label": "Chrome",
      "position": "top_left"
    }
  ],
  "next_action": {
    "type": "click",
    "target": "Chrome app icon",
    "params": { "x": 50, "y": 100 }
  },
  "confidence": 0.95,
  "reasoning": "User wants to play a video, so I should open the browser first"
}
```

## Real-time Loop

```kotlin
// In MainActivity
val aiAgent = AIAgent(backendUrl)
val taskPlanner = TaskPlanner()
val contextManager = ContextManager()

// Start analysis loop
aiAgent.startAnalysisLoop(
    screenshotProvider = { screenCaptureService?.getLatestScreenshot() },
    onAnalysis = { analysis ->
        // Process AI analysis
        contextManager.updateScreenContext(analysis.analysis)

        // Execute recommended action
        if (analysis.confidence > 0.7) {
            actionCoordinator.executeAction(analysis.next_action)
            taskPlanner.addStep(
                analysis.reasoning ?: "Step",
                analysis.next_action
            )
        }
    }
)
```

## Conversational Context

```kotlin
// Example: Multi-turn interaction

// Turn 1
User: "Play chess"
AI: "I'll open YouTube and search for chess"

// Turn 2
User: "Actually, make it chess.com"
AI: "Changing to chess.com instead"

// Context maintained across turns
contextManager.addConversationMessage(
    "Play chess",
    "I'll open YouTube"
)
contextManager.addConversationMessage(
    "Actually, make it chess.com",
    "Changing to chess.com"
)

// AI has full context for decisions
val context = contextManager.getContextString()
// Passed to backend for better understanding
```

## Performance

- **Screenshot analysis**: 1-2 seconds
- **Action execution**: 100-500ms
- **Screenshot capture**: 500ms (parallel)
- **Total loop**: 2-3 seconds

## Error Handling

```kotlin
// If AI confidence low
if (analysis.confidence < 0.5) {
    // Ask user for confirmation
    voiceService.speak("I'm not sure about the next step. Please help.")
}

// If action fails
if (!actionResult.success) {
    taskPlanner.failStep(stepNumber, actionResult.message)
    // Reanalyze screen and try alternative
    aiAgent.analyzeScreenshot()
}
```

## Testing

```bash
# Send test screenshot to backend
curl -X POST -F "file=@screenshot.png" \
  http://localhost:8000/api/analyze-screen

# Get text extraction
curl -X POST -F "file=@screenshot.png" \
  http://localhost:8000/api/extract-text

# Get layout analysis
curl -X POST -F "file=@screenshot.png" \
  http://localhost:8000/api/understand-layout
```

---

**Status**: 🔄 Phase 3 In Progress - AI Reasoning

**Files Created**: 3 core modules
**Lines of Code**: ~500
**Next**: Full integration with WebSocket and real-time loop
