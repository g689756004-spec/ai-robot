package com.robot.ai.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Rect
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import timber.log.Timber

/**
 * Service for interacting with UI elements using Accessibility API
 * Allows the app to:
 * - Find UI elements by text/resource ID
 * - Click buttons, links, etc.
 * - Type text into input fields
 * - Scroll content
 */
class AccessibilityAutomationService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Log accessibility events for debugging
        if (event != null) {
            Timber.d("Accessibility event: ${event.eventType}")
        }
    }

    override fun onInterrupt() {
        Timber.d("Accessibility service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val info = AccessibilityServiceInfo()
        info.flags = AccessibilityServiceInfo.DEFAULT
        info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        setServiceInfo(info)
        Timber.d("Accessibility service connected")
    }

    /**
     * Find a UI element by text content
     */
    fun findElementByText(text: String): AccessibilityNodeInfo? {
        return findNodeByText(rootInActiveWindow, text)
    }

    /**
     * Find a UI element by resource ID
     */
    fun findElementByResourceId(resourceId: String): AccessibilityNodeInfo? {
        return findNodeByResourceId(rootInActiveWindow, resourceId)
    }

    /**
     * Click on an element
     */
    fun clickElement(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) {
            Timber.w("Cannot click null node")
            return false
        }
        
        return if (node.isClickable) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            Timber.d("Clicked element")
            true
        } else {
            Timber.w("Element is not clickable")
            false
        }
    }

    /**
     * Type text into a focused input field
     */
    fun typeText(text: String): Boolean {
        val args = android.os.Bundle()
        args.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        
        val result = rootInActiveWindow?.performAction(
            AccessibilityNodeInfo.ACTION_SET_TEXT,
            args
        ) ?: false
        
        if (result) {
            Timber.d("Typed text: $text")
        } else {
            Timber.w("Failed to type text")
        }
        
        return result
    }

    /**
     * Scroll content in a direction
     */
    fun scroll(direction: String): Boolean {
        val action = when (direction.lowercase()) {
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            else -> return false
        }
        
        return rootInActiveWindow?.performAction(action) ?: false
    }

    /**
     * Get bounds of an element on screen
     */
    fun getElementBounds(node: AccessibilityNodeInfo?): Rect? {
        if (node == null) return null
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds
    }

    /**
     * Recursively find node by text
     */
    private fun findNodeByText(
        node: AccessibilityNodeInfo?,
        text: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) {
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByText(child, text)
            if (result != null) return result
            child?.recycle()
        }

        return null
    }

    /**
     * Recursively find node by resource ID
     */
    private fun findNodeByResourceId(
        node: AccessibilityNodeInfo?,
        resourceId: String
    ): AccessibilityNodeInfo? {
        if (node == null) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (node.viewIdResourceName == resourceId) {
                return node
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val result = findNodeByResourceId(child, resourceId)
            if (result != null) return result
            child?.recycle()
        }

        return null
    }

    /**
     * Get text content of element
     */
    fun getElementText(node: AccessibilityNodeInfo?): String {
        return node?.text?.toString() ?: ""
    }

    /**
     * Check if element is visible
     */
    fun isElementVisible(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.width() > 0 && bounds.height() > 0
    }
}
