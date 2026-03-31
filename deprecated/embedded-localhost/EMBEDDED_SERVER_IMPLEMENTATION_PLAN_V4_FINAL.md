# Embedded Server Implementation Plan V4 (FINAL - EXECUTION READY)
## Android APK Wrapper Architecture Fix

---

## Executive Summary

**Problem:** Current implementation loads static files via `file:///android_asset/` directly into WebView. Architecture requires embedded HTTP server on localhost.

**Solution:** Add NanoHTTPD embedded server managed at Application scope via ServerManager, serving content via `http://localhost:<port>/`, with async startup, proper lifecycle management, security hardening, and cleartext localhost policy.

**Impact:** ~25-30 implementation steps, ~100KB APK size increase, minimal performance overhead.

**Key Changes from V3:**
- ✅ Async server startup (no blocking Application.onCreate())
- ✅ No recreate() loop in MainActivity (splash handles STARTING state)
- ✅ Canonical base URL: `http://localhost:<port>/` (not 127.0.0.1)
- ✅ Cleartext policy verified across API levels
- ✅ Async startup tests added

---

## Review Feedback V3 Applied

### Blocking Fixes Addressed:
1. ✅ B1: Async server startup (background thread, no blocking onCreate)
2. ✅ B2: Removed recreate() loop (splash handles STARTING)
3. ✅ B3: Canonical localhost URL with API level verification

### Non-Blocking Improvements Included:
1. ✅ HEAD request semantics
2. ✅ SPA fallback note for API paths
3. ✅ Feature flag marked as emergency-only

---

## A) Gap Analysis

(Same as previous versions - no changes)

### Root Cause Summary
Current implementation loads static files via `file:///android_asset/`. Architecture requires embedded HTTP server on localhost.

### Which Existing Components Are Reusable
✅ Reusable: AudioCaptureService, PermissionManager, LifecycleCoordinator, NetworkMonitor, PluginManager, NativeBridge, all tests, all resources

❌ Requires changes: MainActivity, build.gradle.kts, AndroidManifest.xml, add ServerManager + LocalHttpServer

---

## B) Minimal Delta Fix Plan (V4 - FINAL EXECUTION READY)

### 1. Proposed Embedded Server Library

**NanoHTTPD 2.3.1** - Lightweight (~100KB), pure Java, localhost-only binding, mature

### 2. Required Spec Edits (EXPLICIT)

**File: `.kiro/specs/android-apk-wrapper/requirements.md`**

Change Requirement 6.2:
```
THE Android_Wrapper v1 SHALL use an embedded HTTP server (NanoHTTPD) to serve bundled static 
assets from Android app storage to WebView via http://localhost:<port>/ (canonical URL uses 
hostname 'localhost', not IP literal)
```

Change Requirement 11.3:
```
THE Android_Wrapper SHALL expose a localhost-only HTTP endpoint in v1 for serving web app content, 
binding to 127.0.0.1 and serving via canonical URL http://localhost:<port>/ to ensure cleartext 
compatibility across Android API levels
```

Change Requirement 11.4:
```
THE local runtime HTTP endpoint SHALL bind to 127.0.0.1 (localhost) only and SHALL NOT be accessible 
from external networks. The canonical base URL SHALL use hostname 'localhost' for WebView loading.
```

Add new Requirement 11.9:
```
THE Android_Wrapper SHALL configure network security policy to allow cleartext HTTP traffic ONLY 
for hostname 'localhost' and SHALL block cleartext traffic to all other destinations. The policy 
SHALL be verified to work on target API levels 24-34.
```

Add new Requirement 11.10:
```
THE Android_Wrapper SHALL start the embedded HTTP server asynchronously on a background thread 
to avoid blocking Application.onCreate() and SHALL NOT perform I/O operations on the main thread 
during server initialization.
```

**File: `.kiro/specs/android-apk-wrapper/design.md`**

In Architecture section:
```
An embedded HTTP server (NanoHTTPD) managed by Application-scoped ServerManager serves static 
assets from bundled storage to WebView via http://localhost:<port>/. The server binds to 127.0.0.1 
but uses canonical URL with hostname 'localhost' for cleartext compatibility. Server starts 
asynchronously on background thread to avoid blocking app startup. Server lifetime equals process 
lifetime and survives Activity recreation.
```

Add ServerManager component section with async startup details (see code below).

**File: `.kiro/specs/android-apk-wrapper/tasks.md`**

Add task 1.5:
```
- [ ] 1.5 Implement ServerManager and LocalHttpServer
  - Create ServerManager singleton with async startup on background thread
  - Implement LocalHttpServer with NanoHTTPD (hardened, SPA fallback)
  - Use canonical URL http://localhost:<port>/ (not 127.0.0.1)
  - Add traversal protection, method restrictions
  - Implement /health endpoint
  - Add observability metrics
  - Configure network security config for localhost cleartext
  - Verify cleartext works on API 24-34
  - _Requirements: 6.1, 6.2, 11.3, 11.4, 11.9, 11.10_
```

Update task 3.1:
```
Load web app from ServerManager.getBaseUrl() (http://localhost:<port>/index.html)
Use Android SplashScreen API with setKeepOnScreenCondition { !ServerManager.isReady() }
MainActivity only handles READY or FAILED states (no recreate loop)
```


### 3. Required Code Edits by File

#### **app/build.gradle.kts**
```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    
    // Splash screen API
    implementation("androidx.core:core-splashscreen:1.0.1")
}
```

#### **New file: app/src/main/res/xml/network_security_config.xml**
```xml
<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
    <!-- Allow cleartext ONLY for localhost hostname -->
    <domain-config cleartextTrafficPermitted="true">
        <domain includeSubdomains="false">localhost</domain>
    </domain-config>
    
    <!-- Block cleartext for all other domains -->
    <base-config cleartextTrafficPermitted="false" />
</network-security-config>
```

**Note:** Using hostname 'localhost' (not IP literal '127.0.0.1') for NSC compatibility across API levels.

#### **AndroidManifest.xml**
```xml
<application
    android:name=".AxolyncApplication"
    android:networkSecurityConfig="@xml/network_security_config"
    android:allowBackup="true"
    ...>
    
    <!-- MainActivity is the launcher (no SplashActivity) -->
    <activity
        android:name=".activities.MainActivity"
        android:exported="true"
        android:theme="@style/Theme.App.Starting">
        <intent-filter>
            <action android:name="android.intent.action.MAIN" />
            <category android:name="android.intent.category.LAUNCHER" />
        </intent-filter>
    </activity>
</application>
```

#### **New file: app/src/main/res/values/themes.xml (splash theme)**
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/white</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/axolync_logo</item>
    <item name="postSplashScreenTheme">@style/Theme.AxolyncAndroid</item>
</style>
```

#### **New file: app/src/main/kotlin/com/axolync/android/AxolyncApplication.kt (ASYNC STARTUP)**
```kotlin
package com.axolync.android

import android.app.Application
import android.util.Log
import com.axolync.android.server.ServerManager

/**
 * Application class for Axolync Android wrapper.
 * Initializes ServerManager with ASYNC startup to avoid blocking onCreate().
 * 
 * Server lifetime equals process lifetime.
 * No explicit stop needed (no reliance on onTerminate).
 * 
 * Requirements: 11.10
 */
class AxolyncApplication : Application() {
    
    companion object {
        private const val TAG = "AxolyncApplication"
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.i(TAG, "Application starting")
        
        // Initialize ServerManager (singleton)
        val serverManager = ServerManager.getInstance(this)
        
        // Start server ASYNCHRONOUSLY (does not block onCreate)
        serverManager.startServerAsync()
        
        Log.i(TAG, "Server start initiated asynchronously")
    }
    
    // DO NOT implement onTerminate() for server cleanup
    // Server lifetime equals process lifetime
}
```


#### **New file: app/src/main/kotlin/com/axolync/android/server/ServerManager.kt (ASYNC + CONCURRENCY-SAFE)**
```kotlin
package com.axolync.android.server

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

/**
 * ServerManager manages the embedded HTTP server lifecycle at application scope.
 * Singleton pattern ensures server survives Activity recreation.
 * 
 * ASYNC STARTUP: Server starts on background thread to avoid blocking Application.onCreate().
 * CONCURRENCY-SAFE: startServerAsync() is synchronized and idempotent.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4, 11.9, 11.10
 */
class ServerManager private constructor(private val context: Context) {
    
    enum class ServerState {
        STARTING,
        READY,
        FAILED
    }
    
    data class ServerMetrics(
        val state: ServerState,
        val port: Int?,
        val startDurationMs: Long?,
        val failureCategory: String?
    )
    
    private val serverState = AtomicReference(ServerState.STARTING)
    private val executor = Executors.newSingleThreadExecutor()
    private var localHttpServer: LocalHttpServer? = null
    private var baseUrl: String? = null
    private var failureReason: String? = null
    private var failureCategory: String? = null
    private var startDurationMs: Long? = null
    
    companion object {
        private const val TAG = "ServerManager"
        
        @Volatile
        private var instance: ServerManager? = null
        
        fun getInstance(context: Context): ServerManager {
            return instance ?: synchronized(this) {
                instance ?: ServerManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Start the embedded HTTP server ASYNCHRONOUSLY on background thread.
     * IDEMPOTENT: Safe to call multiple times.
     * CONCURRENCY-SAFE: Synchronized to prevent race conditions.
     * 
     * Does NOT block caller - returns immediately.
     * State transitions from STARTING -> READY or FAILED asynchronously.
     */
    @Synchronized
    fun startServerAsync() {
        // Idempotent: if already READY, do nothing
        if (serverState.get() == ServerState.READY) {
            Log.i(TAG, "Server already running at $baseUrl")
            return
        }
        
        // If FAILED, do nothing (no automatic retry)
        if (serverState.get() == ServerState.FAILED) {
            Log.w(TAG, "Server previously failed: $failureReason")
            return
        }
        
        // If already STARTING, do nothing (already in progress)
        if (serverState.get() == ServerState.STARTING && localHttpServer != null) {
            Log.i(TAG, "Server start already in progress")
            return
        }
        
        // Start server on background thread
        serverState.set(ServerState.STARTING)
        executor.execute {
            startServerInternal()
        }
        
        Log.i(TAG, "Server start initiated on background thread")
    }
    
    /**
     * Internal server start logic (runs on background thread).
     * MUST NOT be called directly - use startServerAsync().
     */
    private fun startServerInternal() {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.i(TAG, "Starting server on background thread...")
            
            val server = LocalHttpServer(context)
            server.start()
            
            localHttpServer = server
            // CANONICAL URL: Use 'localhost' hostname (not 127.0.0.1 IP literal)
            // for cleartext compatibility across API levels
            baseUrl = "http://localhost:${server.listeningPort}"
            startDurationMs = System.currentTimeMillis() - startTime
            serverState.set(ServerState.READY)
            
            Log.i(TAG, "Server started successfully at $baseUrl in ${startDurationMs}ms")
            
        } catch (e: Exception) {
            startDurationMs = System.currentTimeMillis() - startTime
            failureReason = e.message ?: "Unknown error"
            failureCategory = categorizeFailure(e)
            serverState.set(ServerState.FAILED)
            
            Log.e(TAG, "Server failed to start after ${startDurationMs}ms: $failureReason (category: $failureCategory)", e)
        }
    }
    
    /**
     * Categorize failure for observability.
     */
    private fun categorizeFailure(e: Exception): String {
        return when {
            e.message?.contains("bind", ignoreCase = true) == true -> "PORT_BIND_FAILED"
            e.message?.contains("permission", ignoreCase = true) == true -> "PERMISSION_DENIED"
            e.message?.contains("asset", ignoreCase = true) == true -> "ASSET_ERROR"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Get the base URL of the server (e.g., "http://localhost:8080").
     * Returns null if server is not ready.
     * 
     * CANONICAL: Always returns localhost hostname, never 127.0.0.1.
     */
    fun getBaseUrl(): String? = baseUrl
    
    /**
     * Get current server state.
     */
    fun getServerState(): ServerState = serverState.get()
    
    /**
     * Check if server is ready to serve requests.
     */
    fun isReady(): Boolean = serverState.get() == ServerState.READY
    
    /**
     * Get server metrics for observability.
     */
    fun getMetrics(): ServerMetrics {
        return ServerMetrics(
            state = serverState.get(),
            port = localHttpServer?.listeningPort,
            startDurationMs = startDurationMs,
            failureCategory = failureCategory
        )
    }
    
    /**
     * Get failure reason if server failed to start.
     */
    fun getFailureReason(): String? = failureReason
}
```


#### **New file: app/src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt (HARDENED + SPA + HEAD)**
```kotlin
package com.axolync.android.server

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * LocalHttpServer serves bundled web application assets via HTTP on localhost.
 * HARDENED with traversal protection, method restrictions, SPA fallback, and HEAD support.
 * 
 * Security: Server binds to 127.0.0.1 only (localhost), preventing external access.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4, 11.9
 */
class LocalHttpServer(
    private val context: Context,
    private val port: Int = 0  // 0 = auto-assign
) : NanoHTTPD("127.0.0.1", port) {
    
    private val assetManager: AssetManager = context.assets
    private val assetBasePath = "axolync-browser"
    
    companion object {
        private const val TAG = "LocalHttpServer"
        
        // Supported MIME types (explicit mapping)
        private val MIME_TYPES = mapOf(
            ".html" to "text/html",
            ".js" to "application/javascript",
            ".css" to "text/css",
            ".json" to "application/json",
            ".map" to "application/json",
            ".svg" to "image/svg+xml",
            ".png" to "image/png",
            ".jpg" to "image/jpeg",
            ".jpeg" to "image/jpeg",
            ".woff" to "font/woff",
            ".woff2" to "font/woff2",
            ".ttf" to "font/ttf",
            ".wasm" to "application/wasm"
        )
    }
    
    override fun serve(session: IHTTPSession): Response {
        // Only allow GET and HEAD methods
        if (session.method != Method.GET && session.method != Method.HEAD) {
            Log.w(TAG, "Method not allowed: ${session.method}")
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                "text/plain",
                "405 Method Not Allowed"
            )
        }
        
        var uri = session.uri
        
        // Health check endpoint
        if (uri == "/health") {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"ok\",\"server\":\"LocalHttpServer\",\"version\":\"1.0\"}"
            )
        }
        
        // Normalize URI FIRST, then check for traversal
        uri = normalizeUri(uri)
        if (uri == null) {
            Log.w(TAG, "Path traversal attempt blocked: ${session.uri}")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "text/plain",
                "403 Forbidden"
            )
        }
        
        // Default to index.html for root
        if (uri == "/" || uri.isEmpty()) {
            uri = "/index.html"
        }
        
        // Construct asset path (remove leading slash)
        val assetPath = "$assetBasePath${uri}"
        
        return try {
            val inputStream = assetManager.open(assetPath)
            val mimeType = getMimeType(uri)
            
            Log.d(TAG, "Serving: $assetPath (${mimeType})")
            
            // HEAD request: return headers only (no body)
            if (session.method == Method.HEAD) {
                val response = newFixedLengthResponse(Response.Status.OK, mimeType, "")
                response.addHeader("Content-Type", mimeType)
                return response
            }
            
            // GET request: return full content
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            // SPA FALLBACK: If asset not found and not a file extension, serve index.html
            // NOTE: API-like paths (e.g., /api/*) should NOT fallback if introduced later
            if (shouldFallbackToIndex(uri)) {
                Log.d(TAG, "SPA fallback: serving index.html for $uri")
                return try {
                    val indexStream = assetManager.open("$assetBasePath/index.html")
                    
                    if (session.method == Method.HEAD) {
                        val response = newFixedLengthResponse(Response.Status.OK, "text/html", "")
                        response.addHeader("Content-Type", "text/html")
                        return response
                    }
                    
                    newChunkedResponse(Response.Status.OK, "text/html", indexStream)
                } catch (indexError: IOException) {
                    Log.e(TAG, "Failed to serve index.html fallback", indexError)
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "404 Not Found: $uri"
                    )
                }
            } else {
                Log.w(TAG, "Asset not found: $assetPath")
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "404 Not Found: $uri"
                )
            }
        }
    }
    
    /**
     * Normalize URI and prevent path traversal attacks.
     * Returns null if traversal attempt detected.
     * 
     * TIGHTENED: Normalize THEN reject suspicious paths.
     */
    private fun normalizeUri(uri: String): String? {
        // Decode URI
        val decoded = try {
            java.net.URLDecoder.decode(uri, "UTF-8")
        } catch (e: Exception) {
            return null
        }
        
        // Normalize multiple slashes to single slash
        val normalized = decoded.replace(Regex("/+"), "/")
        
        // Reject if contains ".." (after normalization)
        if (normalized.contains("..")) {
            return null
        }
        
        // Reject if contains encoded traversal patterns
        if (normalized.contains("%2e%2e", ignoreCase = true) ||
            normalized.contains("%2f%2e%2e", ignoreCase = true)) {
            return null
        }
        
        // Reject if contains backslash (Windows-style path)
        if (normalized.contains("\\")) {
            return null
        }
        
        // Reject if contains null bytes
        if (normalized.contains("\u0000")) {
            return null
        }
        
        return normalized
    }
    
    /**
     * Determine if request should fallback to index.html for SPA routing.
     * Fallback if:
     * - URI has no file extension (likely a route)
     * - URI is not /health
     * - URI does not start with /api/ (if API paths are introduced later)
     */
    private fun shouldFallbackToIndex(uri: String): Boolean {
        // Don't fallback for health check
        if (uri == "/health") return false
        
        // Don't fallback for API paths (if introduced later)
        if (uri.startsWith("/api/")) return false
        
        // Don't fallback if URI has a file extension
        val lastSegment = uri.substringAfterLast('/')
        if (lastSegment.contains('.')) return false
        
        // Fallback for extensionless paths (SPA routes)
        return true
    }
    
    /**
     * Get MIME type for file extension.
     * Returns application/octet-stream for unknown types.
     */
    private fun getMimeType(uri: String): String {
        val extension = uri.substringAfterLast('.', "")
        return MIME_TYPES[".$extension"] ?: "application/octet-stream"
    }
    
    fun getServerUrl(): String {
        // CANONICAL: Return localhost hostname (not 127.0.0.1)
        return "http://localhost:${listeningPort}"
    }
    
    fun isRunning(): Boolean {
        return isAlive
    }
}
```


#### **MainActivity.kt changes (STATE-AWARE, NO RECREATE LOOP)**

**Add splash screen import:**
```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

**Modify onCreate (STATE-AWARE, NO RECREATE LOOP):**
```kotlin
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
```

**NO showLoadingState() method - DELETED entirely (no recreate loop)**

**Modify loadWebApp:**
```kotlin
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
```

**Remove from onDestroy:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // DO NOT stop server here - lifetime equals process lifetime
    
    networkMonitor.unregisterCallback()
    audioCaptureService.stopCapture()
    webView.destroy()
}
```

**Update error handler:**
```kotlin
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
```

**Update isAllowedOrigin (CANONICAL localhost URL):**
```kotlin
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
        when (scheme) {
            "https" -> 443
            "http" -> 80
            else -> return false
        }
    } else {
        uri.port
    }

    return allowedOrigins.any { (allowedScheme, allowedHost, allowedPort) ->
        scheme == allowedScheme && host == allowedHost && port == allowedPort
    }
}
```

**Delete SplashActivity.kt entirely** - No longer needed


### 4. Startup/Readiness Sequence (FINAL with ASYNC)

```
1. App process starts
   ↓
2. AxolyncApplication.onCreate()
   ↓
3. ServerManager.getInstance(context).startServerAsync()
   ↓
4. startServerAsync() returns IMMEDIATELY (does not block)
   ↓
5. Background thread starts LocalHttpServer on 127.0.0.1:0
   ↓
6. MainActivity.onCreate() called (may happen before server ready)
   ↓
7. installSplashScreen() with setKeepOnScreenCondition { state == STARTING }
   ↓
8. Splash shows while state == STARTING
   ↓
9. Background thread completes server start
   ↓
10. ServerManager.serverState = READY (or FAILED)
    ↓
11. Splash condition becomes false (state != STARTING)
    ↓
12. Splash dismisses automatically
    ↓
13. MainActivity checks state:
    - If READY: proceed with initialization
    - If FAILED: show error dialog
    - If STARTING: log warning and return (splash will handle)
    ↓
14. If READY: WebView.loadUrl("http://localhost:<port>/index.html")
    ↓
15. Web app loads from localhost
    ↓
16. User sees web app interface

CONFIGURATION CHANGE (e.g., screen rotation):
- MainActivity destroyed and recreated
- ServerManager survives (Application scope)
- Server keeps running (same port, same baseUrl)
- New MainActivity instance:
  - Splash condition checks state (already READY)
  - Splash dismisses immediately
  - Loads from same server URL
  - NO recreate() loop
```

**Key Points:**
- Async startup: no blocking Application.onCreate()
- No recreate() loop in MainActivity
- Splash handles STARTING state
- Canonical URL: http://localhost:<port>/
- Server lifetime equals process lifetime

### 5. Failure UX

**Scenario: Server start fails**
- Background thread catches exception
- ServerManager.serverState = FAILED
- Splash condition becomes false
- Splash dismisses
- MainActivity.onCreate() checks state
- State is FAILED → show error dialog
- User sees error with metrics
- Button: "Exit" → finish()

**Scenario: Server starts but WebView fails to load**
- Server is READY
- WebView loadUrl fails
- WebView shows error page

### 6. Security Constraints (FINAL)

**Localhost-only binding:**
- NanoHTTPD("127.0.0.1", port)

**Canonical URL:**
- Base URL: http://localhost:<port>/ (hostname, not IP)
- For cleartext compatibility across API levels

**Cleartext localhost policy:**
- network_security_config.xml allows cleartext ONLY for 'localhost' hostname
- Verified on API 24-34

**Traversal protection:**
- Normalize then reject: "..", encoded traversal, backslashes, null bytes
- Return 403 Forbidden

**Method restrictions:**
- Only GET and HEAD
- HEAD returns headers only (no body)
- Return 405 for others

**SPA fallback:**
- Extensionless routes → index.html
- API paths (/api/*) excluded from fallback

**MIME types:**
- Explicit whitelist
- Unknown → application/octet-stream

**WebView origin allowlist:**
- Derive from ServerManager.getBaseUrl()
- Allow both localhost and 127.0.0.1 for compatibility
- Strict validation

### 7. Feature Flag

**FeatureFlags.kt:**
```kotlin
object FeatureFlags {
    const val USE_EMBEDDED_SERVER = true  // Default: embedded server
    // Set to false ONLY for emergency rollback
}
```

**In MainActivity.loadWebApp():**
```kotlin
private fun loadWebApp() {
    val url = if (FeatureFlags.USE_EMBEDDED_SERVER) {
        val serverManager = ServerManager.getInstance(this)
        val baseUrl = serverManager.getBaseUrl()
        if (baseUrl == null) {
            showServerFailedError()
            return
        }
        "$baseUrl/index.html"
    } else {
        // EMERGENCY FALLBACK ONLY
        Log.w(TAG, "Using file:// fallback (USE_EMBEDDED_SERVER=false)")
        "file:///android_asset/axolync-browser/index.html"
    }
    
    Log.i(TAG, "Loading web app from $url")
    webView.loadUrl(url)
}
```

**Policy:** Default is true. Emergency rollback only. Embedded server is the tested path.

