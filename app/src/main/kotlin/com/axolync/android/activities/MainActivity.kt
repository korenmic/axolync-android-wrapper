package com.axolync.android.activities

import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import com.axolync.android.BuildConfig
import com.axolync.android.R

/**
 * MainActivity hosts the WebView and coordinates native services.
 * This is the primary activity that runs the Axolync web application.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        configureWebView()
        loadWebApp()
    }

    /**
     * Configure WebView with security settings and origin validation.
     * Requirements: 1.2, 1.3, 6.1, 6.3, 6.4, 11.2, 11.6, 11.8
     */
    private fun configureWebView() {
        // Disable remote debugging in production builds (Requirement 11.2)
        if (!BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(false)
        }

        // Configure WebView settings
        webView.settings.apply {
            // Enable JavaScript for web app functionality
            javaScriptEnabled = true
            
            // Enable DOM storage and database for web app state
            domStorageEnabled = true
            databaseEnabled = true
            
            // Security: Disable arbitrary file access (Requirement 11.6, 11.8)
            // Only bundled android_asset content is loaded by design
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            
            // Security: Block mixed content (Requirement 11.6)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // Cache configuration
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Set up WebViewClient with strict origin validation
        webView.webViewClient = object : WebViewClient() {
            /**
             * Restrict top-level navigation to app origins only.
             * Requirements: 11.6, 11.8
             */
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url.toString()
                return if (url.startsWith("file:///android_asset/") || isAllowedOrigin(request.url)) {
                    false  // Allow navigation
                } else {
                    // Block untrusted navigation
                    android.util.Log.w(TAG, "Blocked navigation to untrusted origin: $url")
                    true
                }
            }

            /**
             * Enforce origin policy for all subresource/network requests.
             * Top-level navigation checks alone are insufficient - must also restrict subresources.
             * Requirements: 11.6, 11.8
             */
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                val url = request.url.toString()
                
                // Allow bundled assets and trusted origins
                if (url.startsWith("file:///android_asset/") || isAllowedOrigin(request.url)) {
                    return super.shouldInterceptRequest(view, request)
                }
                
                // Block untrusted subresource requests
                android.util.Log.w(TAG, "Blocked subresource request to untrusted origin: $url")
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }
    }

    /**
     * Strict origin validation with exact scheme + host + port matching.
     * Substring host matching is explicitly avoided to prevent bypass attacks.
     * Requirements: 11.6, 11.8
     */
    private fun isAllowedOrigin(uri: Uri): Boolean {
        // Define allowed origins with explicit scheme, host, and port
        val allowedOrigins = listOf(
            // Example: Triple("https", "api.axolync.com", 443)
            // Add trusted origins as needed for provider endpoints
        )

        val scheme = uri.scheme ?: return false
        val host = uri.host ?: return false
        val port = if (uri.port == -1) {
            // Use default ports for schemes
            when (scheme) {
                "https" -> 443
                "http" -> 80
                else -> return false
            }
        } else {
            uri.port
        }

        // Check if origin matches any allowed origin exactly
        return allowedOrigins.any { (allowedScheme, allowedHost, allowedPort) ->
            scheme == allowedScheme && host == allowedHost && port == allowedPort
        }
    }

    /**
     * Load the bundled web application from assets.
     * Requirements: 6.1, 6.3, 6.4
     */
    private fun loadWebApp() {
        webView.loadUrl("file:///android_asset/axolync-browser/index.html")
    }

    override fun onResume() {
        super.onResume()
        // TODO: Restore audio capture if previously active
    }

    override fun onPause() {
        super.onPause()
        // TODO: Suspend audio capture
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }

    companion object {
        private const val TAG = "MainActivity"
    }
}
