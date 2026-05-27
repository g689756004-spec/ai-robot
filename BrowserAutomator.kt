package com.robot.ai.automation

import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import timber.log.Timber

/**
 * Browser automation for web tasks (YouTube, shopping, etc.)
 * Controls WebView and executes actions on web pages
 */
class BrowserAutomator(private val webView: WebView) {

    init {
        setupWebView()
    }

    private fun setupWebView() {
        webView.apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
            
            webViewClient = BrowserWebViewClient()
            webChromeClient = WebChromeClient()
        }
        Timber.d("WebView configured for automation")
    }

    /**
     * Navigate to a URL
     */
    fun navigateTo(url: String): Boolean {
        return try {
            val finalUrl = if (url.startsWith("http")) url else "https://$url"
            webView.loadUrl(finalUrl)
            Timber.d("Navigating to: $finalUrl")
            true
        } catch (e: Exception) {
            Timber.e("Navigation failed: ${e.message}")
            false
        }
    }

    /**
     * Click an element on the page by CSS selector
     */
    fun clickElement(cssSelector: String): Boolean {
        return try {
            val javascript = """
                (function() {
                    const element = document.querySelector('$cssSelector');
                    if (element) {
                        element.click();
                        return 'clicked';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(javascript) { result ->
                Timber.d("Click result: $result")
            }
            true
        } catch (e: Exception) {
            Timber.e("Click failed: ${e.message}")
            false
        }
    }

    /**
     * Find and click element by text
     */
    fun clickElementByText(text: String): Boolean {
        return try {
            val javascript = """
                (function() {
                    const xpath = "//*[contains(text(), '$text')]";
                    const result = document.evaluate(xpath, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null);
                    if (result.singleNodeValue) {
                        result.singleNodeValue.click();
                        return 'clicked';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(javascript) { result ->
                Timber.d("Click by text result: $result")
            }
            true
        } catch (e: Exception) {
            Timber.e("Click by text failed: ${e.message}")
            false
        }
    }

    /**
     * Type into input field
     */
    fun typeIntoInput(cssSelector: String, text: String): Boolean {
        return try {
            val javascript = """
                (function() {
                    const input = document.querySelector('$cssSelector');
                    if (input) {
                        input.value = '$text';
                        input.dispatchEvent(new Event('input', { bubbles: true }));
                        input.dispatchEvent(new Event('change', { bubbles: true }));
                        return 'typed';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(javascript) { result ->
                Timber.d("Type result: $result")
            }
            true
        } catch (e: Exception) {
            Timber.e("Type failed: ${e.message}")
            false
        }
    }

    /**
     * Search on page (YouTube, Google, etc.)
     */
    fun search(query: String, searchBoxSelector: String = "input[type='search']"): Boolean {
        return try {
            // Click search box
            clickElement(searchBoxSelector)
            Thread.sleep(300)
            
            // Type query
            typeIntoInput(searchBoxSelector, query)
            Thread.sleep(300)
            
            // Press Enter
            pressEnter(searchBoxSelector)
            
            Timber.d("Searched for: $query")
            true
        } catch (e: Exception) {
            Timber.e("Search failed: ${e.message}")
            false
        }
    }

    /**
     * Press Enter in focused element
     */
    fun pressEnter(cssSelector: String = "input"): Boolean {
        return try {
            val javascript = """
                (function() {
                    const element = document.querySelector('$cssSelector');
                    if (element) {
                        const event = new KeyboardEvent('keydown', {
                            key: 'Enter',
                            code: 'Enter',
                            keyCode: 13,
                            which: 13,
                            bubbles: true
                        });
                        element.dispatchEvent(event);
                        return 'pressed';
                    }
                    return 'not_found';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(javascript) { result ->
                Timber.d("Enter press result: $result")
            }
            true
        } catch (e: Exception) {
            Timber.e("Press Enter failed: ${e.message}")
            false
        }
    }

    /**
     * Scroll page
     */
    fun scrollPage(direction: String, amount: Int = 500): Boolean {
        return try {
            val javascript = when (direction.lowercase()) {
                "down" -> "window.scrollBy(0, $amount);"
                "up" -> "window.scrollBy(0, -$amount);"
                "top" -> "window.scrollTo(0, 0);"
                "bottom" -> "window.scrollTo(0, document.body.scrollHeight);"
                else -> "window.scrollBy(0, $amount);"
            }
            
            webView.evaluateJavascript(javascript) { }
            Timber.d("Scrolled $direction by $amount")
            true
        } catch (e: Exception) {
            Timber.e("Scroll failed: ${e.message}")
            false
        }
    }

    /**
     * Get page title
     */
    fun getPageTitle(): String {
        return webView.title ?: "Unknown"
    }

    /**
     * Get current URL
     */
    fun getCurrentUrl(): String {
        return webView.url ?: "about:blank"
    }

    /**
     * Go back
     */
    fun goBack(): Boolean {
        return if (webView.canGoBack()) {
            webView.goBack()
            Timber.d("Went back")
            true
        } else {
            Timber.w("Cannot go back")
            false
        }
    }

    /**
     * Go forward
     */
    fun goForward(): Boolean {
        return if (webView.canGoForward()) {
            webView.goForward()
            Timber.d("Went forward")
            true
        } else {
            Timber.w("Cannot go forward")
            false
        }
    }

    /**
     * Extract text from page element
     */
    fun getElementText(cssSelector: String): String {
        var result = ""
        val javascript = """
            (function() {
                const element = document.querySelector('$cssSelector');
                return element ? element.innerText : '';
            })();
        """.trimIndent()
        
        webView.evaluateJavascript(javascript) { value ->
            result = value?.trim('"') ?: ""
        }
        
        return result
    }

    /**
     * Wait for element to appear
     */
    fun waitForElement(cssSelector: String, timeoutMs: Long = 5000): Boolean {
        val startTime = System.currentTimeMillis()
        
        while (System.currentTimeMillis() - startTime < timeoutMs) {
            var found = false
            val javascript = """
                (function() {
                    return document.querySelector('$cssSelector') ? 'found' : 'not_found';
                })();
            """.trimIndent()
            
            webView.evaluateJavascript(javascript) { result ->
                found = result == "\"found\""
            }
            
            if (found) {
                Timber.d("Element found: $cssSelector")
                return true
            }
            
            Thread.sleep(500)
        }
        
        Timber.w("Element not found within timeout: $cssSelector")
        return false
    }

    private class BrowserWebViewClient : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Timber.d("Page loaded: $url")
        }

        override fun onReceivedError(
            view: WebView?,
            request: android.webkit.WebResourceRequest?,
            error: android.webkit.WebResourceError?
        ) {
            super.onReceivedError(view, request, error)
            Timber.e("WebView error: ${error?.description}")
        }
    }
}
