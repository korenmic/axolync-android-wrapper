package com.axolync.android.activities

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
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
    
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasLoadedWebApp = false

    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_REQUEST_CODE = 1001
        private const val SERVER_READY_TIMEOUT_MS = 5000L
        private const val RUNTIME_MODE_QUERY_PARAM = "axolync_runtime"
        private const val CLIENT_PLATFORM_HEADER = "X-Axolync-Client-Platform"
        private const val ANDROID_RUNTIME_MODE = "android-wrapper"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        
        // Initialize all services
        initializeServices()
        
        // Configure WebView
        configureWebView()
        registerBackNavigationHandler()
        
        // Check server state and handle appropriately
        val serverManager = ServerManager.getInstance(this)
        when (serverManager.getServerState()) {
            ServerManager.ServerState.STARTING -> {
                // Still starting - wait for it to become ready
                Log.i(TAG, "Server still starting, will wait...")
                waitForServerReady()
            }
            ServerManager.ServerState.FAILED -> {
                // Server failed - show error
                Log.e(TAG, "Server failed to start")
                showServerFailedError()
                return
            }
            ServerManager.ServerState.READY -> {
                // Server ready - load immediately
                Log.i(TAG, "Server ready, loading web app")
                loadWebApp()
            }
        }
    }
    
    /**
     * Wait for server to become ready, then load web app.
     */
    private fun waitForServerReady() {
        val startTime = System.currentTimeMillis()
        mainHandler.postDelayed(object : Runnable {
            override fun run() {
                val serverManager = ServerManager.getInstance(this@MainActivity)
                val elapsed = System.currentTimeMillis() - startTime
                when (serverManager.getServerState()) {
                    ServerManager.ServerState.READY -> {
                        Log.i(TAG, "Server became ready, loading web app")
                        loadWebApp()
                    }
                    ServerManager.ServerState.FAILED -> {
                        Log.e(TAG, "Server failed while waiting")
                        showServerFailedError()
                    }
                    ServerManager.ServerState.STARTING -> {
                        if (elapsed >= SERVER_READY_TIMEOUT_MS) {
                            Log.e(TAG, "Timed out waiting for embedded server to become ready")
                            showServerTimeoutError()
                        } else {
                            // Still starting, check again
                            mainHandler.postDelayed(this, 100)
                        }
                    }
                }
            }
        }, 100)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // DO NOT stop server here - lifetime equals process lifetime
        mainHandler.removeCallbacksAndMessages(null)
        
        if (::networkMonitor.isInitialized) {
            networkMonitor.unregisterCallback()
        }
        if (::audioCaptureService.isInitialized) {
            audioCaptureService.stopCapture()
        }
        webView.destroy()
    }

    private fun registerBackNavigationHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleWrappedRuntimeBackNavigation()
            }
        })
    }

    private fun handleWrappedRuntimeBackNavigation() {
        if (!hasLoadedWebApp) {
            finish()
            return
        }
        val js = """
            (function () {
              try {
                return Boolean(window.__axolyncHandleBackNavigation && window.__axolyncHandleBackNavigation());
              } catch (_error) {
                return false;
              }
            })();
        """.trimIndent()
        webView.evaluateJavascript(js) { rawResult ->
            val handled = rawResult == "true"
            if (!handled) {
                finish()
            }
        }
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
            context = this,
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
            
            // Keep responsive layout while locking browser/page zoom at native level.
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            textZoom = 100

            // Enable media playback
            mediaPlaybackRequiresUserGesture = false
        }

        webView.isVerticalScrollBarEnabled = false
        webView.isHorizontalScrollBarEnabled = false
        webView.overScrollMode = View.OVER_SCROLL_NEVER
        webView.isLongClickable = false
        webView.setHapticFeedbackEnabled(false)
        // Disable long-press text-selection/context menu while preserving JS touch events.
        webView.setOnLongClickListener { true }

        // Defensive pinch block: consume multi-touch gestures before WebView scaling logic.
        webView.setOnTouchListener { view, motionEvent ->
            if (motionEvent.actionMasked == MotionEvent.ACTION_POINTER_DOWN || motionEvent.pointerCount > 1) {
                true
            } else {
                view.parent?.requestDisallowInterceptTouchEvent(true)
                false
            }
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

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                val js = """
                    (function () {
                      document.body.dataset.platform = 'android-wrapper';
                      var viewport = document.querySelector('meta[name="viewport"]');
                      if (!viewport) {
                        viewport = document.createElement('meta');
                        viewport.setAttribute('name', 'viewport');
                        document.head.appendChild(viewport);
                      }
                      viewport.setAttribute('content', 'width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no, viewport-fit=cover');
                      document.documentElement.style.overscrollBehavior = 'none';
                      document.body.style.overscrollBehavior = 'none';
                      document.body.style.touchAction = 'none';
                      document.documentElement.style.webkitUserSelect = 'none';
                      document.body.style.webkitUserSelect = 'none';
                      document.body.style.userSelect = 'none';
                      document.body.style.webkitTouchCallout = 'none';
                    })();
                """.trimIndent()
                view.evaluateJavascript(js, null)
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
        if (hasLoadedWebApp) {
            return
        }
        val serverManager = ServerManager.getInstance(this)
        val baseUrl = serverManager.getBaseUrl()
        
        if (baseUrl == null) {
            Log.e(TAG, "Cannot load web app: server base URL is null")
            showServerFailedError()
            return
        }
        
        val urlBuilder = Uri.parse(baseUrl).buildUpon()
            .appendPath("index.html")
            .appendQueryParameter(RUNTIME_MODE_QUERY_PARAM, ANDROID_RUNTIME_MODE)
        if (BuildConfig.DEMO_MODE) {
            urlBuilder.appendQueryParameter("mode", "demo-android")
        }
        val url = urlBuilder.build().toString()
        val requestHeaders = mapOf(CLIENT_PLATFORM_HEADER to ANDROID_RUNTIME_MODE)
        Log.i(TAG, "Loading web app from $url (canonical localhost URL, demoMode=${BuildConfig.DEMO_MODE})")
        webView.loadUrl(url, requestHeaders)
        hasLoadedWebApp = true
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

    private fun showServerTimeoutError() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Server Startup Timeout")
            .setMessage("Embedded server did not become ready in time. Please restart the app.")
            .setPositiveButton("Exit") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    override fun onResume() {
        super.onResume()
        if (::lifecycleCoordinator.isInitialized) {
            lifecycleCoordinator.onAppResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::lifecycleCoordinator.isInitialized) {
            lifecycleCoordinator.onAppPause()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::lifecycleCoordinator.isInitialized) {
            val state = lifecycleCoordinator.saveState()
            outState.putAll(state)
        }
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
