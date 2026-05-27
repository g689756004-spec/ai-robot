# Android App - Phase 1 Setup

This folder contains the Android Kotlin application for the AI Robot.

## Project Structure

```
android/
├── app_build.gradle.kts      # Build configuration
├── settings.gradle.kts        # Gradle settings
├── AndroidManifest.xml        # App manifest with permissions
├── MainActivity.kt            # Main UI Activity
├── activity_main.xml          # Main layout
├── WebSocketClient.kt         # WebSocket communication
├── VoiceRecognitionService.kt # Voice I/O service
└── ...
```

## Files Overview

### app_build.gradle.kts

- Gradle build config for Android app
- Dependencies: OkHttp, Gson, Timber, Kotlin Coroutines
- Target API: 34 (Android 14)
- Min API: 24 (Android 7.0)

### MainActivity.kt

- Entry point of the app
- Handles permissions
- Initializes services
- Displays status UI

### WebSocketClient.kt

- Connects to backend via WebSocket
- Bidirectional communication
- Message sending/receiving
- Connection state management

### VoiceRecognitionService.kt

- Google Speech-to-Text API integration
- Text-to-Speech for responses
- Continuous listening loop
- Error recovery

### AndroidManifest.xml

- Required permissions:
  - INTERNET (backend communication)
  - RECORD_AUDIO (voice input)
  - MODIFY_AUDIO_SETTINGS (TTS)
  - CAMERA (future)
  - Accessibility (UI automation)

### activity_main.xml

- Material Design layout
- Status indicator (connected/listening/idle)
- Listening UI (microphone icon + prompt)
- Activity log view
- Control buttons (Settings, Stop)

## Setup Instructions

### 1. Copy to Android Project

If using Android Studio:

```bash
# Create new Android project
android studio
# File → New → New Android Project

# Copy these files into the project:
cp app_build.gradle.kts android_project/app/build.gradle.kts
cp MainActivity.kt android_project/app/src/main/java/com/robot/ai/
cp WebSocketClient.kt android_project/app/src/main/java/com/robot/ai/network/
cp VoiceRecognitionService.kt android_project/app/src/main/java/com/robot/ai/services/
cp activity_main.xml android_project/app/src/main/res/layout/
cp AndroidManifest.xml android_project/app/src/main/
```

### 2. Update Backend URL

In `WebSocketClient.kt`, update the backend URL:

```kotlin
private val backendUrl: String = "ws://YOUR_SERVER_IP:8000/ws/agent"
```

### 3. Build and Run

```bash
# Android Studio
Build → Make Project
Run → Run 'app'

# Or via command line
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

## Next Steps

- Phase 2: Implement screen capture service
- Phase 3: Action execution (clicks, typing)
- Phase 4: Integration testing

## Troubleshooting

**WebSocket connection fails:**

- Check backend is running
- Verify backend URL is correct
- Check firewall/network settings

**Microphone not working:**

- Grant RECORD_AUDIO permission
- Test with default Android speech recognizer app

**Permissions not granted:**

- Device must be running Android 6.0+
- Grant permissions in Settings > Apps
