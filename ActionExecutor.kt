package com.robot.ai.automation

import android.content.Context
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import timber.log.Timber

/**
 * Action executor that simulates user interactions on the device
 * Uses UiAutomator for reliable UI automation
 */
class ActionExecutor(private val context: Context) {

    private val uiDevice: UiDevice = UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())

    /**
     * Execute a click action
     * @param x X coordinate or "center"
     * @param y Y coordinate or "top_left", etc.
     */
    fun click(x: Any, y: Any): Boolean {
        return try {
            val point = resolveCoordinates(x, y)
            uiDevice.click(point.x, point.y)
            Timber.d("Clicked at ${point.x}, ${point.y}")
            true
        } catch (e: Exception) {
            Timber.e("Click failed: ${e.message}")
            false
        }
    }

    /**
     * Double-click at position
     */
    fun doubleClick(x: Any, y: Any): Boolean {
        return try {
            val point = resolveCoordinates(x, y)
            val delay = 100L
            click(point.x, point.y)
            SystemClock.sleep(delay)
            click(point.x, point.y)
            Timber.d("Double-clicked at ${point.x}, ${point.y}")
            true
        } catch (e: Exception) {
            Timber.e("Double-click failed: ${e.message}")
            false
        }
    }

    /**
     * Long-press at position
     */
    fun longPress(x: Any, y: Any, duration: Long = 500L): Boolean {
        return try {
            val point = resolveCoordinates(x, y)
            performLongPress(point.x, point.y, duration)
            Timber.d("Long-pressed at ${point.x}, ${point.y} for ${duration}ms")
            true
        } catch (e: Exception) {
            Timber.e("Long-press failed: ${e.message}")
            false
        }
    }

    /**
     * Type text (assumes input field is focused)
     */
    fun typeText(text: String): Boolean {
        return try {
            uiDevice.executeShellCommand("input text '$text'")
            Timber.d("Typed: $text")
            true
        } catch (e: Exception) {
            Timber.e("Type failed: ${e.message}")
            false
        }
    }

    /**
     * Clear focused text field
     */
    fun clearText(): Boolean {
        return try {
            uiDevice.executeShellCommand("input keyevent ${android.view.KeyEvent.KEYCODE_CTRL_A}")
            uiDevice.executeShellCommand("input keyevent ${android.view.KeyEvent.KEYCODE_DEL}")
            Timber.d("Cleared text")
            true
        } catch (e: Exception) {
            Timber.e("Clear failed: ${e.message}")
            false
        }
    }

    /**
     * Scroll in a direction
     * @param direction "up", "down", "left", "right"
     * @param steps number of scroll steps (1-10)
     */
    fun scroll(direction: String, steps: Int = 3): Boolean {
        return try {
            val displaySize = Point()
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
            wm.defaultDisplay.getSize(displaySize)

            val startX = displaySize.x / 2
            val startY = displaySize.y / 2
            var endX = startX
            var endY = startY

            when (direction.lowercase()) {
                "up" -> endY = startY - (displaySize.y / 4 * steps)
                "down" -> endY = startY + (displaySize.y / 4 * steps)
                "left" -> endX = startX - (displaySize.x / 4 * steps)
                "right" -> endX = startX + (displaySize.x / 4 * steps)
            }

            uiDevice.swipe(startX, startY, endX, endY, steps)
            Timber.d("Scrolled $direction")
            true
        } catch (e: Exception) {
            Timber.e("Scroll failed: ${e.message}")
            false
        }
    }

    /**
     * Swipe from point A to point B
     */
    fun swipe(startX: Any, startY: Any, endX: Any, endY: Any, duration: Int = 500): Boolean {
        return try {
            val start = resolveCoordinates(startX, startY)
            val end = resolveCoordinates(endX, endY)
            uiDevice.swipe(start.x, start.y, end.x, end.y, duration / 100)
            Timber.d("Swiped from ${start.x},${start.y} to ${end.x},${end.y}")
            true
        } catch (e: Exception) {
            Timber.e("Swipe failed: ${e.message}")
            false
        }
    }

    /**
     * Press hardware key
     */
    fun pressKey(keyCode: Int): Boolean {
        return try {
            uiDevice.pressKeyCode(keyCode)
            Timber.d("Pressed key: $keyCode")
            true
        } catch (e: Exception) {
            Timber.e("Key press failed: ${e.message}")
            false
        }
    }

    /**
     * Press back button
     */
    fun pressBack(): Boolean = pressKey(android.view.KeyEvent.KEYCODE_BACK)

    /**
     * Press home button
     */
    fun pressHome(): Boolean = pressKey(android.view.KeyEvent.KEYCODE_HOME)

    /**
     * Open app by package name
     */
    fun openApp(packageName: String): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launchIntent != null) {
                context.startActivity(launchIntent)
                SystemClock.sleep(1500) // Wait for app to open
                Timber.d("Opened app: $packageName")
                true
            } else {
                Timber.w("Could not find app: $packageName")
                false
            }
        } catch (e: Exception) {
            Timber.e("Open app failed: ${e.message}")
            false
        }
    }

    /**
     * Wait for element with text to appear
     */
    fun waitForElementWithText(text: String, timeoutMs: Long = 5000): Boolean {
        val selector = UiSelector().text(text)
        val element = UiObject(selector)
        
        return try {
            element.waitForExists(timeoutMs)
        } catch (e: Exception) {
            Timber.w("Wait for element failed: ${e.message}")
            false
        }
    }

    /**
     * Wait for element with resource ID to appear
     */
    fun waitForElementWithId(resourceId: String, timeoutMs: Long = 5000): Boolean {
        val selector = UiSelector().resourceId(resourceId)
        val element = UiObject(selector)
        
        return try {
            element.waitForExists(timeoutMs)
        } catch (e: Exception) {
            Timber.w("Wait for element failed: ${e.message}")
            false
        }
    }

    /**
     * Get current screen content description
     */
    fun getScreenDescription(): String {
        return try {
            uiDevice.executeShellCommand("dumpsys accessibility")
        } catch (e: Exception) {
            "Error getting screen: ${e.message}"
        }
    }

    /**
     * Take a screenshot (just for reference, actual capture is done in ScreenCaptureService)
     */
    fun takeScreenshot(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                // Screenshot would be saved to /sdcard/Pictures/
                Timber.d("Screenshot taken")
                true
            } else {
                false
            }
        } catch (e: Exception) {
            Timber.e("Screenshot failed: ${e.message}")
            false
        }
    }

    /**
     * Resolve coordinate parameter (could be absolute or relative like "center")
     */
    private fun resolveCoordinates(x: Any, y: Any): Point {
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as android.view.WindowManager
        val displaySize = Point()
        wm.defaultDisplay.getSize(displaySize)

        val xCoord = when (x) {
            is Int -> x
            is String -> when (x.lowercase()) {
                "center" -> displaySize.x / 2
                "left" -> displaySize.x / 4
                "right" -> displaySize.x * 3 / 4
                else -> displaySize.x / 2
            }
            else -> displaySize.x / 2
        }

        val yCoord = when (y) {
            is Int -> y
            is String -> when (y.lowercase()) {
                "center" -> displaySize.y / 2
                "top" -> displaySize.y / 4
                "bottom" -> displaySize.y * 3 / 4
                "top_left" -> displaySize.y / 4
                "top_right" -> displaySize.y / 4
                "bottom_left" -> displaySize.y * 3 / 4
                "bottom_right" -> displaySize.y * 3 / 4
                else -> displaySize.y / 2
            }
            else -> displaySize.y / 2
        }

        return Point(xCoord, yCoord)
    }

    /**
     * Perform long press using touch events
     */
    private fun performLongPress(x: Int, y: Int, duration: Long) {
        val downTime = SystemClock.uptimeMillis()
        val event = MotionEvent.obtain(
            downTime,
            downTime,
            MotionEvent.ACTION_DOWN,
            x.toFloat(),
            y.toFloat(),
            0
        )
        event.source = InputDevice.TOOL_TYPE_UNKNOWN
        
        SystemClock.sleep(duration)
        
        val upEvent = MotionEvent.obtain(
            downTime,
            SystemClock.uptimeMillis(),
            MotionEvent.ACTION_UP,
            x.toFloat(),
            y.toFloat(),
            0
        )
        upEvent.source = InputDevice.TOOL_TYPE_UNKNOWN
        
        event.recycle()
        upEvent.recycle()
    }
}
