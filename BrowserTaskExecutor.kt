package com.robot.ai.automation

import com.google.gson.Gson
import com.robot.ai.models.Action
import timber.log.Timber

/**
 * Manages browser automation tasks
 * Handles high-level actions like "search YouTube", "play video", etc.
 */
class BrowserTaskExecutor(private val browserAutomator: BrowserAutomator) {

    private val gson = Gson()

    /**
     * Execute a browser task
     */
    fun executeTask(taskJson: String): Boolean {
        return try {
            val task = gson.fromJson(taskJson, BrowserTask::class.java)
            executeTask(task)
        } catch (e: Exception) {
            Timber.e("Error parsing browser task: ${e.message}")
            false
        }
    }

    /**
     * Execute a browser task
     */
    fun executeTask(task: BrowserTask): Boolean {
        return when (task.action) {
            "search_youtube" -> searchYoutube(task.query ?: "")
            "play_video" -> playVideo(task.url ?: "")
            "search_google" -> searchGoogle(task.query ?: "")
            "navigate" -> browserAutomator.navigateTo(task.url ?: "")
            "click" -> browserAutomator.clickElement(task.selector ?: "")
            "type" -> browserAutomator.typeIntoInput(task.selector ?: "", task.text ?: "")
            "scroll" -> browserAutomator.scrollPage(task.direction ?: "down", task.amount ?: 500)
            "wait" -> {
                Thread.sleep(task.duration ?: 1000L)
                true
            }
            else -> {
                Timber.w("Unknown browser action: ${task.action}")
                false
            }
        }
    }

    /**
     * Search on YouTube
     */
    fun searchYoutube(query: String): Boolean {
        return try {
            browserAutomator.navigateTo("youtube.com")
            Thread.sleep(3000) // Wait for page load
            
            // Wait for search box and search
            browserAutomator.waitForElement("input#search", 5000)
            browserAutomator.clickElement("input#search")
            Thread.sleep(500)
            browserAutomator.typeIntoInput("input#search", query)
            Thread.sleep(500)
            browserAutomator.pressEnter("input#search")
            
            Timber.d("Searched YouTube for: $query")
            true
        } catch (e: Exception) {
            Timber.e("YouTube search failed: ${e.message}")
            false
        }
    }

    /**
     * Play first video in search results
     */
    fun playVideo(videoUrl: String = ""): Boolean {
        return try {
            if (videoUrl.isNotEmpty()) {
                browserAutomator.navigateTo(videoUrl)
            } else {
                // Click first video in results
                browserAutomator.clickElement("a#video-title")
            }
            
            Thread.sleep(2000) // Wait for video to load
            
            // Click play button if needed
            browserAutomator.clickElement(".ytp-play-button")
            
            Timber.d("Playing video")
            true
        } catch (e: Exception) {
            Timber.e("Play video failed: ${e.message}")
            false
        }
    }

    /**
     * Search on Google
     */
    fun searchGoogle(query: String): Boolean {
        return try {
            browserAutomator.navigateTo("google.com")
            Thread.sleep(2000) // Wait for page load
            
            browserAutomator.search(query, "input[name='q']")
            Timber.d("Searched Google for: $query")
            true
        } catch (e: Exception) {
            Timber.e("Google search failed: ${e.message}")
            false
        }
    }
}

/**
 * Represents a browser task
 */
data class BrowserTask(
    val action: String, // "search_youtube", "play_video", "navigate", etc.
    val query: String? = null,
    val url: String? = null,
    val selector: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val amount: Int? = null,
    val duration: Long? = null
)
