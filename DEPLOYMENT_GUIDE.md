# AI Robot - Complete Setup & Deployment Guide

## ⚡ Quick Start (5 minutes)

### Prerequisites

- Python 3.9+
- Android Studio (latest)
- Node.js + npm (for testing)
- Claude API key from [console.anthropic.com](https://console.anthropic.com)

---

## 🔧 Backend Setup

### 1. Install Python Dependencies

```bash
cd E:\React\robot
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```

### 2. Configure Environment

```bash
# Create .env file
copy .env.example .env

# Edit .env with your Claude API key
# ANTHROPIC_API_KEY=your_key_here
# BACKEND_HOST=0.0.0.0
# BACKEND_PORT=8000
```

### 3. Run Backend Locally

```bash
python main.py

# Expected output:
# INFO:     Uvicorn running on http://0.0.0.0:8000
# INFO:     WebSocket endpoint: ws://0.0.0.0:8000/ws/agent
```

### 4. Test Backend

```bash
# Test health check
curl http://localhost:8000/health

# Response: {"status":"ready"}

# Test with screenshot (optional)
curl -X POST -F "file=@screenshot.jpg" \
  http://localhost:8000/api/analyze-screen
```

---

## 📱 Android Setup

### 1. Create Android Project

```bash
# Open Android Studio
# File → New → Project
# Choose "Kotlin + Jetpack" template
# Project name: AIRobot
# Package name: com.robot.ai
# Location: E:\React\robot\android\
```

### 2. Copy Kotlin Files

```bash
# Copy to src/main/java/com/robot/ai/
- MainActivity.kt
- RobotController.kt
- Models.kt
- AndroidManifest.xml

# Copy to src/main/java/com/robot/ai/network/
- WebSocketClient.kt
- WebSocketMessageHandler.kt
- PreferencesManager.kt

# Copy to src/main/java/com/robot/ai/services/
- VoiceRecognitionService.kt
- ScreenCaptureService.kt

# Copy to src/main/java/com/robot/ai/automation/
- ActionExecutor.kt
- AccessibilityAutomationService.kt
- ActionInterpreter.kt
- BrowserAutomator.kt
- BrowserTaskExecutor.kt
- ActionCoordinator.kt

# Copy to src/main/java/com/robot/ai/ai/
- AIAgent.kt
- TaskPlanner.kt
- ContextManager.kt

# Copy to src/main/res/xml/
- accessibility_service_config.xml

# Copy to src/main/res/layout/
- activity_main.xml

# Copy to src/main/
- AndroidManifest.xml
```

### 3. Update Gradle

Edit `build.gradle.kts`:

```kotlin
android {
    compileSdk = 34
    defaultConfig {
        applicationId = "com.robot.ai"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
}

dependencies {
    // Network
    implementation("com.squareup.okhttp3:okhttp:4.11.0")

    // JSON
    implementation("com.google.code.gson:gson:2.10.1")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // Android
    implementation("androidx.core:core:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")

    // DataStore
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // Test
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.2.0")
}
```

### 4. Update AndroidManifest.xml

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.CAMERA" />
    <uses-permission android:name="android.permission.WRITE_SECURE_SETTINGS" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />

    <application>
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".services.ScreenCaptureService"
            android:enabled="true" />

        <service
            android:name=".automation.AccessibilityAutomationService"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

### 5. Build & Run

```bash
# Build
./gradlew build

# Run on emulator/device
./gradlew installDebug

# Check logcat
adb logcat | grep Robot
```

---

## 🌐 Deploy Backend to Cloud

### Option A: Railway.app (Easiest)

1. Sign up: https://railway.app
2. Create new project
3. Deploy from GitHub (or connect directly)
4. Set env vars: `ANTHROPIC_API_KEY`
5. Get domain: `https://airobotapi.up.railway.app`

### Option B: Google Cloud Run

```bash
# Authenticate
gcloud auth login

# Create project
gcloud projects create ai-robot-project

# Deploy
gcloud run deploy ai-robot-api \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated \
  --set-env-vars ANTHROPIC_API_KEY=your_key

# Get URL from output
# https://ai-robot-api-xxxxx.run.app
```

### Option C: Docker (Any Host)

```dockerfile
FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install -r requirements.txt

COPY . .

CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
```

```bash
docker build -t airobotapi .
docker run -e ANTHROPIC_API_KEY=your_key -p 8000:8000 airobotapi
```

---

## 📋 Post-Deployment Checklist

### On Android Device:

- [ ] Grant Microphone permission (Settings → Apps → AIRobot)
- [ ] Grant Camera permission
- [ ] Enable Accessibility Service (Settings → Accessibility → AIRobot)
- [ ] Enable Screen Capture (will prompt on first use)

### Testing:

- [ ] Verify backend is running: `curl https://api.yourhost.com/health`
- [ ] Update backend URL in MainActivity
- [ ] Launch app and see "Connected" status
- [ ] Speak: "Play chess"
- [ ] Watch robot open YouTube and search

### Monitoring:

```bash
# Backend logs
tail -f backend.log

# Android logs
adb logcat *:S RobotController:V

# API calls
curl -H "Authorization: Bearer token" \
  https://api.yourhost.com/analytics
```

---

## 🐛 Troubleshooting

### "Connection refused"

- Backend not running on correct IP/port
- Firewall blocking port 8000
- Check: `netstat -an | findstr :8000`

### "Permission denied" on microphone

- Grant at Settings → Apps → AIRobot → Permissions
- Or request permission dynamically in code

### "Accessibility service not binding"

- Enable at Settings → Accessibility → AIRobot
- Service requires manual user action (cannot programmatically grant)

### Screenshot stuck/slow

- Reduce FPS: Change `2000` to `5000` (ms) in RobotController
- Increase compression: Change `0.7` to `0.5` in AIAgent.kt

### Backend times out

- Increase timeout: Edit OkHttpClient in AIAgent.kt
- Current: 30s connect, 60s read/write

### "Low confidence" messages

- Backend uncertain about UI state
- Try clearer voice commands
- Check internet connection to backend

---

## 📊 API Reference

### Backend Endpoints

```bash
# Health check
GET /health
→ {"status":"ready"}

# Analyze screenshot
POST /api/analyze-screen
  Body: multipart/form-data {file: image}
← {
    "analysis": "I see home screen",
    "confidence": 0.95,
    "next_action": {...}
  }

# Extract text
POST /api/extract-text
  Body: multipart/form-data {file: image}
← {"text": "extracted text"}

# Understand layout
POST /api/understand-layout
  Body: multipart/form-data {file: image}
← {"elements": [...]}

# WebSocket
WS /ws/agent
→ {type: "screenshot", data: "base64..."}
← {type: "action", action: {...}}
```

### Action Types

```json
{
  "type": "click",
  "x": 100,
  "y": 200,
  "duration": 100
}

{
  "type": "type",
  "text": "hello world"
}

{
  "type": "scroll",
  "direction": "down",
  "amount": 500
}

{
  "type": "open_app",
  "package": "com.google.chrome"
}

{
  "type": "web_action",
  "action": "search_youtube",
  "query": "chess tutorials"
}
```

---

## 🎯 Example Workflows

### Workflow 1: Play YouTube Video

```
User: "Play chess video"
  ↓
Backend: Plan [open chrome, navigate youtube, search "chess", click video, play]
  ↓
Android executes each step with screenshots between
  ↓
AI verifies video is playing
  ↓
Task complete, await next command
```

### Workflow 2: Web Search

```
User: "What's the weather?"
  ↓
Backend: Plan [open chrome, navigate google, type "weather", click search]
  ↓
AI reads results, selects weather widget
  ↓
Speaks weather aloud: "It's 72 degrees..."
```

### Workflow 3: Complex Task

```
User: "Buy coffee online"
  ↓
Backend: Multi-step plan [open amazon, search "coffee", filter by price, add to cart]
  ↓
AI stops at checkout, asks: "Ready to purchase?"
  ↓
User: "Yes"
  ↓
AI completes purchase
```

---

## 📈 Performance Tuning

| Setting           | Default | Lower Latency | Lower Battery |
| ----------------- | ------- | ------------- | ------------- |
| Analysis interval | 2000ms  | 1000ms        | 5000ms        |
| Screenshot scale  | 70%     | 80%           | 50%           |
| JPEG quality      | 70%     | 85%           | 60%           |
| Timeout           | 60s     | 30s           | 120s          |

---

## 🔐 Security Notes

1. **Never commit `.env` with API keys**
2. **Use HTTPS/WSS in production**
3. **Restrict backend to authenticated requests**
4. **Encrypt stored screenshots**
5. **Monitor API usage for abuse**

---

## 📞 Support

- **Backend issues**: Check `main.py` logs
- **Android issues**: Check logcat with `adb logcat`
- **Claude API**: See [api.anthropic.com/docs](https://api.anthropic.com/docs)
- **Android Accessibility**: See [Android Developer docs](https://developer.android.com/guide/topics/ui/accessibility)

---

**Last Updated**: December 2024
**Status**: ✅ **READY FOR DEPLOYMENT**
