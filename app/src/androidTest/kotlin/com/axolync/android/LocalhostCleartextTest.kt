package com.axolync.android

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.axolync.android.activities.MainActivity
import com.axolync.android.server.ServerManager
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Instrumentation test proving WebView can load canonical localhost URL.
 * Validates that cleartext HTTP is allowed for localhost on target API levels.
 */
@RunWith(AndroidJUnit4::class)
class LocalhostCleartextTest {
    
    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)
    
    @Test
    fun webViewCanLoadLocalhostCleartext() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serverManager = ServerManager.getInstance(context)
        
        // Wait for server to become READY (or timeout)
        var attempts = 0
        while (serverManager.getServerState() == ServerManager.ServerState.STARTING && attempts < 50) {
            Thread.sleep(100)
            attempts++
        }
        
        assertEquals(
            "Server should be READY",
            ServerManager.ServerState.READY,
            serverManager.getServerState()
        )
        
        val baseUrl = serverManager.getBaseUrl()
        assertNotNull("Base URL should not be null", baseUrl)
        assertTrue("Base URL should start with http://localhost", baseUrl!!.startsWith("http://localhost"))
        
        // Test that WebView can load the localhost URL
        val latch = CountDownLatch(1)
        var loadSuccess = false
        var loadError: String? = null
        
        activityRule.scenario.onActivity { activity ->
            val webView = WebView(activity)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loadSuccess = true
                    latch.countDown()
                }
                
                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    loadError = "Error $errorCode: $description"
                    latch.countDown()
                }
            }
            
            webView.loadUrl("$baseUrl/index.html")
        }
        
        // Wait for load to complete (or timeout)
        assertTrue("WebView load should complete within timeout", latch.await(10, TimeUnit.SECONDS))
        
        if (!loadSuccess) {
            fail("WebView failed to load localhost URL: $loadError")
        }
        
        assertTrue("WebView should successfully load localhost cleartext URL", loadSuccess)
    }
}
