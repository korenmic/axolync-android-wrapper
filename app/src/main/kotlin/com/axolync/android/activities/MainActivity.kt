package com.axolync.android.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.axolync.android.BuildConfig
import com.axolync.android.R
import com.axolync.android.bridge.NativeBridge
import com.axolync.android.server.ServerManager
import com.axolync.android.services.AudioCaptureService
import com.axolync.android.services.LifecycleCoordinator
import com.axolync.android.services.PermissionManager
import com.axolync.android.utils.NetworkMonitor
import com.axolync.android.utils.PluginManager

/**
 * MainActivity hosts the WebView and coordinates native services.
 * This is the primary activity that runs the Axolync web application.
 * 
 * Uses Android SplashScreen API to show splash while embedded server starts.
 * 
 * Requirements: 1.2, 1.3, 2.1, 2.4, 6.1, 6.2, 11.3, 11.4
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
        // Install splash screen and keep it visible until server ready
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val serverManager = ServerManager.getInstance(this)
            // Keep splash visible while STARTING
            // Dismiss when READY or FAILED
            serverManager.getServerState() == ServerManager.ServerState.STARTING
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        // Check server state (STATE-AWARE, no recreate loop)
        val serverManager = ServerManager.getInstance(this)
        when (serverManager.getServerState()) {
            ServerManager.ServerState.STARTING -> {
                // Still starting - splash should handle this
                // This case should not happen if splash condition works correctly
                // If it does happen, just log and wait (splash will eventually dismiss)
                Log.w(TAG, "Server still starting when MainActivity.onCreate() called - splash should be visible")
                // DO NOT call recreate() or show dialog - just return
                // Splash will dismiss when state changes
                return
            }
            ServerManager.ServerState.FAILED -> {
                // Server failed - show error
                Log.e(TAG, "Server failed to start")
                showServerFailedError()
                return
            }
            ServerManager.ServerState.READY -> {
                // Server ready - proceed normally
                Log.i(TAG, "Server ready, proceeding with initialization")
            }
        }
        
        // Initialize all services
        initializeServices()
        
        // Configure WebView
        configureWebView()
        
        // Restore state if available
        savedInstanceState?.let {
            lifecycleCoordinator.restoreState(it)
        }
        
        // Load web app from localhost server
        loadWebApp()
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
            
            // Security: Disable file access (we use HTTP server now)
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
            
            // Enable viewport and zoom for responsive web app
            useWideViewPort = true
            loadWithOverviewMode = true
            
            // Enable media playback
            mediaPlaybackRequiresUserGesture = false
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
                return if (isAllowedOrigin(request.url)) {
                    false  // Allow navigation
                } else {
                    // Block untrusted navigation
                    Log.w(TAG, "Blocked navigation to untrusted origin: $url")
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
                
                // Allow localhost server and trusted origins
                if (isAllowedOrigin(request.url)) {
                    return super.shouldInterceptRequest(view, request)
                }
                
                // Block untrusted subresource requests
                Log.w(TAG, "Blocked subresource request to untrusted origin: $url")
                return WebResourceResponse("text/plain", "UTF-8", null)
            }
        }
    }

    /**
     * Strict origin validation with exact scheme + host + port matching.
     * Includes localhost server origin from ServerManager.
     * Substring host matching is explicitly avoided to prevent bypass attacks.
     * Requirements: 11.6, 11.8
     */
    private fun isAllowedOrigin(uri: Uri): Boolean {
        val allowedOrigins = mutableListOf<Triple<String, String, Int>>()
        
        // Add localhost server origin from ServerManager (single source of truth)
        // CANONICAL: Use 'localhost' hostname (not 127.0.0.1)
        val serverManager = ServerManager.getInstance(this)
        serverManager.getBaseUrl()?.let { baseUrl ->
            val serverUri = Uri.parse(baseUrl)
            val port = serverUri.port.takeIf { it != -1 } ?: 80
            // Allow both localhost and 127.0.0.1 for compatibility
            allowedOrigins.add(Triple("http", "localhost", port))
            allowedOrigins.add(Triple("http", "127.0.0.1", port))
        }
        
        // Add external API origins as needed (explicitly documented)
        // Example: allowedOrigins.add(Triple("https", "api.axolync.com", 443))

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
     * Load the bundled web application from localhost HTTP server.
     * Uses canonical URL with 'localhost' hostname (not 127.0.0.1).
     * Requirements: 6.1, 6.3, 6.4
     */
    private fun loadWebApp() {
        val serverManager = ServerManager.getInstance(this)
        val baseUrl = serverManager.getBaseUrl()
        
        if (baseUrl == null) {
            Log.e(TAG, "Cannot load web app: server base URL is null")
            showServerFailedError()
            return
        }
        
        val url = "$baseUrl/index.html"
        Log.i(TAG, "Loading web app from $url (canonical localhost URL)")
        webView.loadUrl(url)
    }

    /**
     * Show error dialog when server fails to start.
     */
    private fun showServerFailedError() {
        val serverManager = ServerManager.getInstance(this)
        val reason = serverManager.getFailureReason() ?: "Unknown error"
        val metrics = serverManager.getMetrics()
        
        val message = buildString {
            append("Failed to start internal server:\n")
            append("$reason\n\n")
            append("Category: ${metrics.failureCategory}\n")
            if (metrics.startDurationMs != null) {
                append("Duration: ${metrics.startDurationMs}ms\n")
            }
            append("\nPlease restart the app.")
        }
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Server Error")
            .setMessage(message)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
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
        
        // DO NOT stop server here - lifetime equals process lifetime
        
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
