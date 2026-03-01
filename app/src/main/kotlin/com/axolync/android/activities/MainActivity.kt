package com.axolync.android.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.axolync.android.BuildConfig
import com.axolync.android.R
import com.axolync.android.bridge.NativeBridge
import com.axolync.android.services.AudioCaptureService
import com.axolync.android.services.LifecycleCoordinator
import com.axolync.android.services.PermissionManager
import com.axolync.android.utils.NetworkMonitor
import com.axolync.android.utils.PluginManager

/**
 * MainActivity hosts the WebView and coordinates native services.
 * This is the primary activity that runs the Axolync web application.
 * 
 * Requirements: 1.2, 1.3, 2.1, 2.4
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var permissionManager: PermissionManager
    private lateinit var audioCaptureService: AudioCaptureService
    private lateinit var lifecycleCoordinator: LifecycleCoordinator
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var pluginManager: PluginManager
    private lateinit var nativeBridge: NativeBridge

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        // Initialize all services
        initializeServices()
        
        // Configure WebView
        configureWebView()
        
        // Restore state if available
        savedInstanceState?.let {
            lifecycleCoordinator.restoreState(it)
        }
        
        // Load web app
        loadWebApp()
        
        // Signal SplashActivity that we're ready
        SplashActivity.signalReady()
    }

    /**
     * Initialize all native services and wire them together.
     * Requirements: 1.2, 1.3, 2.1, 2.4
     */
    private fun initializeServices() {
        // Initialize PermissionManager
        permissionManager = PermissionManager(this)
        
        // Initialize AudioCaptureService
        audioCaptureService = AudioCaptureService()
        
        // Initialize NetworkMonitor
        networkMonitor = NetworkMonitor(this)
        
        // Initialize PluginManager
        pluginManager = PluginManager(this)
        
        // Initialize NativeBridge with all dependencies
        nativeBridge = NativeBridge(
            webView = webView,
            audioCaptureService = audioCaptureService,
            permissionManager = permissionManager,
            getNetworkStatusCallback = {
                val isOnline = networkMonitor.isOnline()
                val connectionType = when (networkMonitor.getConnectionType()) {
                    NetworkMonitor.ConnectionType.WIFI -> "wifi"
                    NetworkMonitor.ConnectionType.CELLULAR -> "cellular"
                    NetworkMonitor.ConnectionType.NONE -> "none"
                }
                Pair(isOnline, connectionType)
            }
        )
        
        // Initialize LifecycleCoordinator
        lifecycleCoordinator = LifecycleCoordinator(
            context = this,
            webView = webView,
            audioCaptureService = audioCaptureService,
            nativeBridge = nativeBridge
        )
        
        // Register network callback
        networkMonitor.registerCallback { isOnline ->
            nativeBridge.notifyLifecycleEvent(if (isOnline) "online" else "offline")
        }
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
            @Suppress("DEPRECATION")
            allowFileAccessFromFileURLs = false
            @Suppress("DEPRECATION")
            allowUniversalAccessFromFileURLs = false
            
            // Security: Block mixed content (Requirement 11.6)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            
            // Cache configuration
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Register NativeBridge as JavaScript interface
        webView.addJavascriptInterface(nativeBridge, "AndroidBridge")

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
        val allowedOrigins = listOf<Triple<String, String, Int>>(
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
        lifecycleCoordinator.onAppResume()
    }

    override fun onPause() {
        super.onPause()
        lifecycleCoordinator.onAppPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val state = lifecycleCoordinator.saveState()
        outState.putAll(state)
    }

    override fun onDestroy() {
        super.onDestroy()
        networkMonitor.unregisterCallback()
        audioCaptureService.stopCapture()
        webView.destroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        lifecycleCoordinator.onLowMemory()
    }

    /**
     * Handle permission request results.
     * Requirements: 3.1, 3.2
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            val granted = grantResults.isNotEmpty() && 
                         grantResults[0] == PackageManager.PERMISSION_GRANTED
            
            val status = if (granted) {
                "granted"
            } else {
                if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                    "denied"
                } else {
                    "denied_permanently"
                }
            }
            
            nativeBridge.notifyPermissionResult(status)
        }
    }
}
