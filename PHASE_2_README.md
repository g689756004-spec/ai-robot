# Phase 2: Action Execution Layer

This phase implements the AI agent's ability to control the Android tablet by:

- Clicking buttons and UI elements
- Typing text
- Scrolling and swiping
- Opening apps
- Pressing hardware keys
- Waiting for UI elements

## Files Created

### ActionExecutor.kt (7KB)

Simulates user interactions using UiAutomator:

- `click(x, y)` - Click at coordinates
- `doubleClick(x, y)` - Double click
- `longPress(x, y, duration)` - Long press
- `typeText(text)` - Type text (requires focused input)
- `clearText()` - Clear text field
- `scroll(direction, steps)` - Scroll up/down/left/right
- `swipe(startX, startY, endX, endY)` - Swipe gesture
- `pressKey(keyCode)` - Press hardware key
- `pressBack()` - Press back button
- `pressHome()` - Press home button
- `openApp(packageName)` - Launch an app
- `waitForElementWithText(text)` - Wait for UI element to appear
- `waitForElementWithId(resourceId)` - Wait for element by ID

**Coordinate System:**

- Absolute: `500` (pixel coordinate)
- Relative: `"center"`, `"top"`, `"left"`, `"right"`, `"bottom"`

### AccessibilityAutomationService.kt (5KB)

Android Accessibility Service for deeper UI control:

- `findElementByText(text)` - Find UI element by text
- `findElementByResourceId(resourceId)` - Find by resource ID
- `clickElement(node)` - Click specific element
- `typeText(text)` - Type into focused element
- `scroll(direction)` - Accessibility scroll
- `getElementBounds(node)` - Get element coordinates
- `isElementVisible(node)` - Check if visible

### ActionInterpreter.kt (4KB)

Interprets and executes JSON actions from backend:

- Parses action JSON
- Routes to appropriate executor method
- Handles parameters
- Executes action sequences
- Error handling and logging

## Supported Actions

### Click Actions

```json
{
  "type": "click",
  "params": {
    "x": "center",
    "y": "center"
  }
}
```

### Text Input

```json
{
  "type": "type",
  "params": {
    "text": "search query"
  }
}
```

### Scroll

```json
{
  "type": "scroll",
  "params": {
    "direction": "down",
    "steps": 3
  }
}
```

### Open App

```json
{
  "type": "open_app",
  "params": {
    "package": "com.android.chrome"
  }
}
```

### Wait for Element

```json
{
  "type": "wait_for_element",
  "params": {
    "text": "Play Button",
    "timeout": 5000
  }
}
```

### Swipe

```json
{
  "type": "swipe",
  "params": {
    "start_x": "left",
    "start_y": "center",
    "end_x": "right",
    "end_y": "center"
  }
}
```

### Key Press

```json
{
  "type": "back"
}
```

## Integration Steps

### 1. Add Files to Project

```
src/main/java/com/robot/ai/automation/
├── ActionExecutor.kt
├── ActionInterpreter.kt
└── AccessibilityAutomationService.kt

src/main/res/xml/
└── accessibility_service_config.xml
```

### 2. Update AndroidManifest.xml

```xml
<service
    android:name=".automation.AccessibilityAutomationService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

### 3. Update build.gradle

```gradle
dependencies {
    // UI Automation
    androidTestImplementation 'androidx.test.uiautomator:uiautomator:2.2.0'
    androidTestImplementation 'androidx.test:runner:1.5.2'
    androidTestImplementation 'androidx.test:rules:1.5.0'
}
```

### 4. Update MainActivity.kt

```kotlin
// Add to MainActivity
private var actionInterpreter: ActionInterpreter? = null

override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    actionInterpreter = ActionInterpreter(this)
}

// In WebSocket message handler:
if (message_type == "action") {
    val actionJson = data.getString("action")
    actionInterpreter?.executeAction(actionJson)
}
```

## Example Workflow: "Play Chess on Chess.com"

```
Backend sends:
{
  "type": "open_app",
  "params": {"package": "com.android.chrome"}
}
→ ActionInterpreter → ActionExecutor → Opens Chrome

Backend sees new screenshot, sends:
{
  "type": "click",
  "params": {"x": "center", "y": 100}
}
→ Clicks on address bar

Backend sends:
{
  "type": "type",
  "params": {"text": "chess.com"}
}
→ Types URL

Backend sends:
{
  "type": "key",
  "params": {"code": 66}
}
→ Presses Enter

... (continues with more actions)
```

## Permissions Required

```xml
<uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
<uses-permission android:name="android.permission.INTERACT_ACROSS_USERS_FULL" />
```

## Configuration

### Enable Accessibility Service

Users must manually enable in Settings:

1. Settings → Accessibility
2. Find "AI Robot Agent"
3. Enable toggle

### Test Accessibility

```bash
# Verify service is running
adb shell dumpsys accessibility

# Grant permission (requires root or Settings UI)
adb shell settings put secure enabled_accessibility_services \
  "com.robot.ai/.automation.AccessibilityAutomationService"
```

## Limitations & Workarounds

| Issue                         | Workaround                                 |
| ----------------------------- | ------------------------------------------ |
| Can't automate system dialogs | Use alternative actions or restart         |
| Input lag on slow devices     | Increase wait times                        |
| Element not found             | Use waitForElement first                   |
| Permission denied             | User must enable in Accessibility settings |

## Debugging

Enable verbose logging:

```kotlin
Timber.plant(Timber.DebugTree())

// View logs
adb logcat | grep "Robot"
```

---

**Status**: 🔄 Phase 2 In Progress - Action Execution

- ✅ UI automation framework
- ✅ Accessibility service
- ✅ Action interpreter
- 🔄 Integration with WebSocket
- 🔄 Testing with real device

**Next**: Integrate with MainActivity WebSocket handler and test!
