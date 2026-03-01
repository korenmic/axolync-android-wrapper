package com.axolync.android.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
    
    private var bootstrapped = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var startupTimeoutRunnable: Runnable? = null

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val STARTUP_TIMEOUT_MS = 5000L
        private const val STARTUP_RETRY_INTERVAL_MS = 100L
        private const val MINIMUM_SPLASH_DURATION_MS = 2000L
    }
    
    private var splashStartTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        // Record splash start time for minimum duration enforcement
        splashStartTime = System.currentTimeMillis()
        
        // Install splash screen and keep it visible until server ready AND minimum duration elapsed
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            val serverManager = ServerManager.getInstance(this)
            val elapsedTime = System.currentTimeMillis() - splashStartTime
            val serverNotReady = serverManager.getServerState() == ServerManager.ServerState.STARTING
            val minimumTimeNotElapsed = elapsedTime < MINIMUM_SPLASH_DURATION_MS
            
            // Keep splash visible while STARTING OR minimum time not elapsed
            // Dismiss only when READY/FAILED AND minimum time elapsed
            serverNotReady || minimumTimeNotElapsed
        }
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        // Check server state and handle continuation
        val serverManager = ServerManager.getInstance(this)
        when (serverManager.getServerState()) {
            ServerManager.ServerState.STARTING -> {
                // Server still starting - schedule retry loop with timeout
                Log.i(TAG, "Server still starting, scheduling retry loop with timeout")
                scheduleStartupRetry()
            }
            ServerManager.ServerState.FAILED -> {
                // Server failed - show error (after minimum splash duration)
                Log.e(TAG, "Server failed to start")
                enforceMinimumSplashDuration {
                    showServerFailedError()
                }
            }
            ServerManager.ServerState.READY -> {
                // Server ready - proceed with bootstrap (after minimum splash duration)
                Log.i(TAG, "Server ready, proceeding with initialization")
                enforceMinimumSplashDuration {
                    bootstrapApplication(savedInstanceState)
                }
            }
        }
    }
    
    /**
     * Enforce minimum splash duration before executing action.
     * Ensures splash screen shows for at least MINIMUM_SPLASH_DURATION_MS.
     */
    private fun enforceMinimumSplashDuration(action: () -> Unit) {
        val elapsedTime = System.currentTimeMillis() - splashStartTime
        val remainingTime = MINIMUM_SPLASH_DURATION_MS - elapsedTime
        
        if (remainingTime > 0) {
            Log.i(TAG, "Enforcing minimum splash duration, waiting ${remainingTime}ms")
            mainHandler.postDelayed(action, remainingTime)
        } else {
            action()
        }
    }
    
    /**
     * Schedule retry loop to check server state with hard timeout.
     * Ensures STARTING state doesn't dead-end.
     */
    private fun scheduleStartupRetry() {
        val startTime = System.currentTimeMillis()
        
        val retryRunnable = object : Runnable {
            override fun run() {
                val serverManager = ServerManager.getInstance(this@MainActivity)
                val elapsed = System.currentTimeMillis() - startTime
                
                when (serverManager.getServerState()) {
                    ServerManager.ServerState.READY -> {
                        Log.i(TAG, "Server became ready after ${elapsed}ms")
                        cancelStartupTimeout()
                        enforceMinimumSplashDuration {
                            bootstrapApplication(null)
                        }
                    }
                    ServerManager.ServerState.FAILED -> {
                        Log.e(TAG, "Server failed after ${elapsed}ms")
                        cancelStartupTimeout()
                        enforceMinimumSplashDuration {
                            showServerFailedError()
                        }
                    }
                    ServerManager.ServerState.STARTING -> {
                        if (elapsed >= STARTUP_TIMEOUT_MS) {
                            Log.e(TAG, "Server startup timeout after ${elapsed}ms")
                            cancelStartupTimeout()
                            enforceMinimumSplashDuration {
                                showStartupTimeoutError()
                            }
                        } else {
                            // Still starting, retry
                            mainHandler.postDelayed(this, STARTUP_RETRY_INTERVAL_MS)
                        }
                    }
                }
            }
        }
        
        startupTimeoutRunnable = retryRunnable
        mainHandler.postDelayed(retryRunnable, STARTUP_RETRY_INTERVAL_MS)
    }
    
    /**
     * Cancel startup timeout/retry loop.
     */
    private fun cancelStartupTimeout() {
        startupTimeoutRunnable?.let {
            mainHandler.removeCallbacks(it)
            startupTimeoutRunnable = null
        }
    }
    
    /**
     * Bootstrap application once server is ready.
     * Idempotent - only runs once per activity instance.
     */
    private fun bootstrapApplication(savedInstanceState: Bundle?) {
        if (bootstrapped) {
            Log.w(TAG, "Application already bootstrapped, skipping")
            return
        }
        
        bootstrapped = true
        
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
     * Show error dialog when startup times out.
     */
    private fun showStartupTimeoutError() {
        val message = "Server startup timed out after ${STARTUP_TIMEOUT_MS}ms.\n\nPlease restart the app."
        
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Startup Timeout")
            .setMessage(message)
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel any pending startup retry
        cancelStartupTimeout()
        
        // DO NOT stop server here - lifetime equals process lifetime
        
        if (::networkMonitor.isInitialized) {
            networkMonitor.unregisterCallback()
        }
        if (::audioCaptureService.isInitialized) {
            audioCaptureService.stopCapture()
        }
        webView.destroy()
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

    override fun onLowMemory() {
        super.onLowMemory()
        if (::lifecycleCoordinator.isInitialized) {
            lifecycleCoordinator.onLowMemory()
        }
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
