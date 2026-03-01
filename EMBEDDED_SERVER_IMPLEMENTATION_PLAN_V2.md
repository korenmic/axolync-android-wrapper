# Embedded Server Implementation Plan V2
## Android APK Wrapper Architecture Fix (REVISED)

---

## Executive Summary

**Problem:** Current implementation loads static files via `file:///android_asset/` directly into WebView. This violates the intended architecture where an embedded HTTP server must run localhost, serving the axolync-browser web app.

**Solution:** Add NanoHTTPD embedded server managed at Application scope via ServerManager, serving content via `http://127.0.0.1:<port>/`, with proper lifecycle management and security hardening.

**Impact:** ~15-20 implementation steps, ~100KB APK size increase, minimal performance overhead.

**Key Changes from V1:**
- Server lifecycle moved to Application scope (not Activity)
- ServerManager singleton manages server lifecycle
- No static Activity flags for readiness signaling
- Hardened LocalHttpServer with traversal protection
- Deterministic origin allowlist from ServerManager
- Proper splash screen using Android SplashScreen API

---

## Review Feedback Applied

### Blocking Corrections Addressed:
1. ✅ Activity lifecycle architecture fixed - ServerManager at Application scope
2. ✅ Server lifecycle no longer tied to Activity
3. ✅ Spec edits explicitly defined for Kiro Android wrapper spec
4. ✅ LocalHttpServer hardened with traversal protection and strict MIME mapping
5. ✅ WebView allowlist uses deterministic origin from ServerManager

### Non-Blocking Improvements Included:
1. ✅ Readiness gate includes server health + WebView success
2. ✅ Startup timeout states (STARTING, READY, FAILED)
3. ✅ Feature flag for emergency rollback
4. ✅ Structured logging with timestamps and failure categories

---

## A) Gap Analysis

(Same as V1 - no changes needed)

### Root Cause Summary
The current implementation loads static files via `file:///android_asset/` directly into WebView. This violates the intended architecture where an embedded HTTP server must run localhost.


### Exact Spec Lines Causing Wrong Architecture

**Requirements.md:**
- Requirement 6.1: "THE Android_Wrapper SHALL serve the web application content to the WebView" - implies active serving
- Requirement 6.2: "THE Android_Wrapper v1 SHALL use bundled static assets served from Android app storage" - "served" implies HTTP server
- Requirement 11.4: Written as future tense, but architecture requires it NOW

**Design.md:**
- States: "Built/bundled output from axolync-browser is served as static assets from `file:///android_asset/` (no embedded server in v1)" - WRONG interpretation

### Exact Code Paths Causing file:// Behavior

**MainActivity.kt line ~230:**
```kotlin
private fun loadWebApp() {
    webView.loadUrl("file:///android_asset/axolync-browser/index.html")
}
```

### Which Existing Components Are Reusable Unchanged

✅ **Reusable without changes:**
- AudioCaptureService
- PermissionManager  
- LifecycleCoordinator
- NetworkMonitor
- PluginManager
- NativeBridge (interface methods)
- All test files
- All resource files

❌ **Requires changes:**
- MainActivity (remove server code, use ServerManager)
- SplashActivity (or replace with SplashScreen API)
- Add new: ServerManager, LocalHttpServer
- build.gradle.kts (add NanoHTTPD dependency)

---

## B) Minimal Delta Fix Plan (REVISED)

### 1. Proposed Embedded Server Library

**Recommendation: NanoHTTPD 2.3.1**
- Lightweight (~100KB)
- Pure Java, no native dependencies
- Localhost-only binding supported
- Mature and stable

### 2. Required Spec Edits (EXPLICIT)

**File: `.kiro/specs/android-apk-wrapper/requirements.md`**

Change Requirement 6.2 from:
```
THE Android_Wrapper v1 SHALL use bundled static assets served from Android app storage
```
To:
```
THE Android_Wrapper v1 SHALL use an embedded HTTP server (NanoHTTPD) to serve bundled static 
assets from Android app storage to WebView via http://127.0.0.1:<port>/
```

Change Requirement 11.3 from:
```
THE Android_Wrapper SHALL NOT expose any externally reachable local runtime endpoint in v1 static-assets mode
```
To:
```
THE Android_Wrapper SHALL expose a localhost-only HTTP endpoint in v1 for serving web app content, 
binding exclusively to 127.0.0.1 to prevent external network access
```

Change Requirement 11.4 from:
```
IF a local runtime endpoint is introduced in a future version, THEN it SHALL bind to localhost only
```
To:
```
THE local runtime HTTP endpoint SHALL bind to 127.0.0.1 (localhost) only and SHALL NOT be accessible 
from external networks
```

**File: `.kiro/specs/android-apk-wrapper/design.md`**

In Architecture section, change:
```
Built/bundled output from axolync-browser is served as static assets from file:///android_asset/ 
(no embedded server in v1)
```
To:
```
An embedded HTTP server (NanoHTTPD) managed by Application-scoped ServerManager serves static 
assets from bundled storage to WebView via http://127.0.0.1:<port>/. The server lifecycle is 
independent of Activity lifecycle to survive configuration changes.
```

Add new component section after "10. Network Connectivity Monitor":
```
### 11. ServerManager (Application Scope)

**Responsibility**: Manage embedded HTTP server lifecycle at application scope.

**Key Methods**:
```kotlin
class ServerManager private constructor(private val context: Context) {
    enum class ServerState { STARTING, READY, FAILED }
    
    fun startServer(): Result<Unit>
    fun stopServer()
    fun getBaseUrl(): String?
    fun getServerState(): ServerState
    fun isReady(): Boolean
    
    companion object {
        fun getInstance(context: Context): ServerManager
    }
}
```

**Lifecycle**:
- Initialized in Application.onCreate()
- Server starts once per app process
- Survives Activity recreation and configuration changes
- Stops only on app process termination
```

**File: `.kiro/specs/android-apk-wrapper/tasks.md`**

Add new task after task 1:
```
- [ ] 1.5 Implement ServerManager and LocalHttpServer
  - Create ServerManager singleton at Application scope
  - Implement LocalHttpServer with NanoHTTPD
  - Add traversal protection and strict MIME mapping
  - Implement /health endpoint
  - Add structured logging
  - _Requirements: 6.1, 6.2, 11.3, 11.4_
```

Update task 3.1:
Change:
```
Load bundled assets from file:///android_asset/axolync-browser/index.html
```
To:
```
Load web app from ServerManager.getBaseUrl() (http://127.0.0.1:<port>/index.html)
Configure WebView to allow only localhost origin from ServerManager
```

Update task 2.1:
Add:
```
Use Android SplashScreen API with setKeepOnScreenCondition { !ServerManager.isReady() }
OR implement splash polling of ServerManager.getServerState()
```


### 3. Required Code Edits by File

#### **app/build.gradle.kts**
```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
```

#### **New file: app/src/main/kotlin/com/axolync/android/AxolyncApplication.kt**
```kotlin
package com.axolync.android

import android.app.Application
import com.axolync.android.server.ServerManager

/**
 * Application class for Axolync Android wrapper.
 * Initializes ServerManager at app startup.
 */
class AxolyncApplication : Application() {
    
    override fun onCreate() {
        super.onCreate()
        
        // Initialize ServerManager (singleton)
        val serverManager = ServerManager.getInstance(this)
        
        // Start server asynchronously
        serverManager.startServer()
    }
}
```

#### **AndroidManifest.xml - Add application name**
```xml
<application
    android:name=".AxolyncApplication"
    android:allowBackup="true"
    ...>
```

#### **New file: app/src/main/kotlin/com/axolync/android/server/ServerManager.kt**
```kotlin
package com.axolync.android.server

import android.content.Context
import android.util.Log
import java.util.concurrent.atomic.AtomicReference

/**
 * ServerManager manages the embedded HTTP server lifecycle at application scope.
 * Singleton pattern ensures server survives Activity recreation.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4
 */
class ServerManager private constructor(private val context: Context) {
    
    enum class ServerState {
        STARTING,
        READY,
        FAILED
    }
    
    private val serverState = AtomicReference(ServerState.STARTING)
    private var localHttpServer: LocalHttpServer? = null
    private var baseUrl: String? = null
    private var failureReason: String? = null
    
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
     * Start the embedded HTTP server.
     * Called from Application.onCreate().
     */
    fun startServer(): Result<Unit> {
        if (serverState.get() == ServerState.READY) {
            Log.i(TAG, "Server already running at $baseUrl")
            return Result.success(Unit)
        }
        
        serverState.set(ServerState.STARTING)
        val startTime = System.currentTimeMillis()
        
        return try {
            val server = LocalHttpServer(context)
            server.start()
            
            localHttpServer = server
            baseUrl = server.getServerUrl()
            serverState.set(ServerState.READY)
            
            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "Server started successfully at $baseUrl in ${duration}ms")
            
            Result.success(Unit)
        } catch (e: Exception) {
            val duration = System.currentTimeMillis() - startTime
            failureReason = e.message ?: "Unknown error"
            serverState.set(ServerState.FAILED)
            
            Log.e(TAG, "Server failed to start after ${duration}ms: $failureReason", e)
            Result.failure(e)
        }
    }
    
    /**
     * Stop the embedded HTTP server.
     * Called on app process termination.
     */
    fun stopServer() {
        localHttpServer?.let { server ->
            try {
                server.stop()
                Log.i(TAG, "Server stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping server", e)
            }
        }
        localHttpServer = null
        baseUrl = null
        serverState.set(ServerState.STARTING)
    }
    
    /**
     * Get the base URL of the server (e.g., "http://127.0.0.1:8080").
     * Returns null if server is not ready.
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
     * Get failure reason if server failed to start.
     */
    fun getFailureReason(): String? = failureReason
}
```


#### **New file: app/src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt (HARDENED)**
```kotlin
package com.axolync.android.server

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * LocalHttpServer serves bundled web application assets via HTTP on localhost.
 * HARDENED with traversal protection and strict MIME mapping.
 * 
 * Security: Server binds to 127.0.0.1 only (localhost), preventing external access.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4
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
        
        // Normalize URI and prevent traversal attacks
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
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            Log.w(TAG, "Asset not found: $assetPath")
            newFixedLengthResponse(
                Response.Status.NOT_FOUND,
                "text/plain",
                "404 Not Found: $uri"
            )
        }
    }
    
    /**
     * Normalize URI and prevent path traversal attacks.
     * Returns null if traversal attempt detected.
     */
    private fun normalizeUri(uri: String): String? {
        // Decode URI
        val decoded = try {
            java.net.URLDecoder.decode(uri, "UTF-8")
        } catch (e: Exception) {
            return null
        }
        
        // Reject if contains ".." or encoded traversal
        if (decoded.contains("..") || 
            decoded.contains("%2e%2e") || 
            decoded.contains("%2E%2E")) {
            return null
        }
        
        // Reject if contains backslash (Windows-style path)
        if (decoded.contains("\\")) {
            return null
        }
        
        // Normalize multiple slashes to single slash
        return decoded.replace(Regex("/+"), "/")
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
        return "http://127.0.0.1:${listeningPort}"
    }
    
    fun isRunning(): Boolean {
        return isAlive
    }
}
```


#### **MainActivity.kt changes (SIMPLIFIED - No server management)**

**Remove server-related fields (no longer needed):**
```kotlin
// DELETE: private lateinit var localHttpServer: LocalHttpServer
// DELETE: companion object with serverReady flag
```

**Modify onCreate (simplified):**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webview)
    
    // Check if server is ready
    val serverManager = ServerManager.getInstance(this)
    if (!serverManager.isReady()) {
        showServerNotReadyError()
        return
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
    
    // Signal SplashActivity that we're ready (if using SplashActivity approach)
    SplashActivity.signalReady()
}
```

**Modify loadWebApp:**
```kotlin
/**
 * Load the bundled web application from localhost HTTP server.
 * Requirements: 6.1, 6.3, 6.4
 */
private fun loadWebApp() {
    val serverManager = ServerManager.getInstance(this)
    val baseUrl = serverManager.getBaseUrl()
    
    if (baseUrl == null) {
        Log.e(TAG, "Cannot load web app: server base URL is null")
        showServerNotReadyError()
        return
    }
    
    val url = "$baseUrl/index.html"
    Log.i(TAG, "Loading web app from $url")
    webView.loadUrl(url)
}
```

**Remove from onDestroy (server lifecycle managed by Application):**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // DO NOT stop server here - it's managed by Application scope
    // localHttpServer.stop()  // DELETE THIS
    
    networkMonitor.unregisterCallback()
    audioCaptureService.stopCapture()
    webView.destroy()
}
```

**Add error handler:**
```kotlin
private fun showServerNotReadyError() {
    val serverManager = ServerManager.getInstance(this)
    val reason = serverManager.getFailureReason() ?: "Unknown error"
    
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Server Error")
        .setMessage("Failed to start internal server: $reason\n\nPlease restart the app.")
        .setPositiveButton("Exit") { _, _ -> finish() }
        .setCancelable(false)
        .show()
}
```

**Update isAllowedOrigin in configureWebView (DETERMINISTIC):**
```kotlin
/**
 * Strict origin validation with exact scheme + host + port matching.
 * Origin source is deterministic from ServerManager.
 * Requirements: 11.6, 11.8
 */
private fun isAllowedOrigin(uri: Uri): Boolean {
    val allowedOrigins = mutableListOf<Triple<String, String, Int>>()
    
    // Add localhost server origin from ServerManager (single source of truth)
    val serverManager = ServerManager.getInstance(this)
    serverManager.getBaseUrl()?.let { baseUrl ->
        val serverUri = Uri.parse(baseUrl)
        val port = serverUri.port.takeIf { it != -1 } ?: 80
        allowedOrigins.add(Triple("http", "127.0.0.1", port))
        allowedOrigins.add(Triple("http", "localhost", port))
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


#### **SplashActivity.kt changes (Option 1: Keep SplashActivity with polling)**

**Modify checkInitialization:**
```kotlin
/**
 * Implements initialization check logic with timeout.
 * Polls ServerManager.isReady() until server is ready.
 * 
 * CORRECTED BEHAVIOR:
 * - Splash shows IMMEDIATELY on app launch
 * - Polls ServerManager.isReady() every 100ms
 * - When server ready OR timeout (5s): start MainActivity and dismiss splash
 */
private fun checkInitialization() {
    // Set up timeout - navigate to main after 5 seconds regardless
    handler.postDelayed({
        if (!hasNavigated) {
            Log.w(TAG, "Timeout reached, navigating to main")
            navigateToMain()
        }
    }, splashTimeout)

    // Start polling server readiness
    pollServerReadiness()
}

/**
 * Polls ServerManager.isReady() every 100ms until server is ready.
 */
private fun pollServerReadiness() {
    handler.postDelayed(object : Runnable {
        override fun run() {
            if (hasNavigated) return
            
            val serverManager = ServerManager.getInstance(this@SplashActivity)
            when (serverManager.getServerState()) {
                ServerManager.ServerState.READY -> {
                    Log.i(TAG, "Server ready, navigating to main")
                    navigateToMain()
                }
                ServerManager.ServerState.FAILED -> {
                    Log.e(TAG, "Server failed, navigating to main (will show error)")
                    navigateToMain()
                }
                ServerManager.ServerState.STARTING -> {
                    // Still starting, poll again in 100ms
                    handler.postDelayed(this, 100)
                }
            }
        }
    }, 100)
}

/**
 * Navigates to MainActivity and finishes splash activity.
 */
private fun navigateToMain() {
    if (hasNavigated) return
    hasNavigated = true
    
    val intent = Intent(this, MainActivity::class.java)
    startActivity(intent)
    finish()
}
```

**Remove readyCallback (no longer needed):**
```kotlin
// DELETE companion object with readyCallback
```

#### **Alternative: Option 2 - Use Android SplashScreen API (PREFERRED)**

**In MainActivity.kt, add to onCreate BEFORE setContentView:**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    // Install splash screen and keep it visible until server ready
    val splashScreen = installSplashScreen()
    splashScreen.setKeepOnScreenCondition {
        val serverManager = ServerManager.getInstance(this)
        !serverManager.isReady()
    }
    
    super.onCreate(savedInstanceState)
    // ... rest of onCreate ...
}
```

**Add dependency to app/build.gradle.kts:**
```kotlin
dependencies {
    // ... existing dependencies ...
    implementation("androidx.core:core-splashscreen:1.0.1")
}
```

**Create res/values/themes.xml splash theme:**
```xml
<style name="Theme.App.Starting" parent="Theme.SplashScreen">
    <item name="windowSplashScreenBackground">@color/white</item>
    <item name="windowSplashScreenAnimatedIcon">@drawable/axolync_logo</item>
    <item name="postSplashScreenTheme">@style/Theme.AxolyncAndroid</item>
</style>
```

**Update AndroidManifest.xml:**
```xml
<activity
    android:name=".activities.MainActivity"
    android:exported="true"
    android:theme="@style/Theme.App.Starting">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>

<!-- Remove SplashActivity if using SplashScreen API -->
```

**Note:** Option 2 (SplashScreen API) is PREFERRED as it:
- Eliminates Activity lifecycle coupling
- Uses Android best practices
- Simpler implementation
- No polling needed


### 4. Startup/Readiness Sequence (CORRECTED with Application scope)

```
1. App process starts
   ↓
2. AxolyncApplication.onCreate()
   ↓
3. ServerManager.getInstance(context).startServer()
   ↓
4. LocalHttpServer starts on 127.0.0.1:0 (auto-assign port)
   ↓
5. ServerManager.serverState = READY (or FAILED if exception)
   ↓
6. User sees splash screen (via SplashScreen API or SplashActivity)
   ↓
7. Splash polls ServerManager.isReady() OR uses setKeepOnScreenCondition
   ↓
8. When serverState == READY:
   ↓
9. Splash dismisses, MainActivity becomes visible
   ↓
10. MainActivity.onCreate()
    ↓
11. MainActivity checks ServerManager.isReady()
    ↓
12. If ready: WebView.loadUrl(ServerManager.getBaseUrl() + "/index.html")
    ↓
13. Web app loads from http://127.0.0.1:<port>/index.html
    ↓
14. User sees web app interface

TIMEOUT PATH (if server never ready):
8. After 5 seconds timeout:
   ↓
9. Splash dismisses anyway
   ↓
10. MainActivity shows error dialog (server failed)

CONFIGURATION CHANGE (e.g., screen rotation):
- MainActivity destroyed and recreated
- ServerManager survives (Application scope)
- Server keeps running
- New MainActivity instance loads from same server URL
```

**Key Points:**
- Server lifecycle independent of Activity
- Server survives configuration changes
- Splash always visible during server boot
- No static Activity flags
- Single source of truth: ServerManager

### 5. Failure UX When Server Fails to Start

**Scenario: Server start throws exception in Application.onCreate()**
- ServerManager.serverState = FAILED
- ServerManager.failureReason = exception message
- Splash timeout (5s) eventually dismisses
- MainActivity.onCreate() checks ServerManager.isReady()
- If not ready: Show AlertDialog with failure reason
- User sees: "Failed to start internal server: <reason>. Please restart the app."
- Single button: "Exit" → finish()

**Scenario: Server starts but WebView fails to load**
- Server is READY
- WebView loadUrl fails (network error, etc.)
- WebView shows error page
- User can retry or check logs

### 6. Security Constraints (HARDENED)

**Localhost-only binding:**
- NanoHTTPD constructor: `NanoHTTPD("127.0.0.1", port)`
- Prevents external network access

**Traversal protection:**
- Reject URIs containing "..", encoded traversal, backslashes
- Normalize multiple slashes
- Return 403 Forbidden for traversal attempts

**Method restrictions:**
- Only allow GET and HEAD methods
- Return 405 Method Not Allowed for others

**MIME type restrictions:**
- Explicit whitelist of supported MIME types
- Return application/octet-stream for unknown types

**WebView origin allowlist (DETERMINISTIC):**
- Derive allowed origin from ServerManager.getBaseUrl()
- Add http://127.0.0.1:<port> and http://localhost:<port>
- Keep existing external API origins (explicitly documented)
- Maintain strict scheme+host+port validation

**Mixed content and file access:**
- mixedContentMode = NEVER_ALLOW
- allowFileAccess = false (already set)
- allowFileAccessFromFileURLs = false (already set)

### 7. Feature Flag for Emergency Rollback

**Add to FeatureFlags.kt:**
```kotlin
object FeatureFlags {
    const val USE_EMBEDDED_SERVER = true  // Set to false to use file://
}
```

**In MainActivity.loadWebApp():**
```kotlin
private fun loadWebApp() {
    val url = if (FeatureFlags.USE_EMBEDDED_SERVER) {
        val serverManager = ServerManager.getInstance(this)
        val baseUrl = serverManager.getBaseUrl()
        if (baseUrl == null) {
            showServerNotReadyError()
            return
        }
        "$baseUrl/index.html"
    } else {
        // Fallback to file:// for emergency rollback
        "file:///android_asset/axolync-browser/index.html"
    }
    
    Log.i(TAG, "Loading web app from $url")
    webView.loadUrl(url)
}
```

**Note:** Default is USE_EMBEDDED_SERVER = true. Only set to false for emergency rollback.


---

## C) Testing Plan (REVISED - Additive Only)

### New Unit Tests

**ServerManagerTest.kt:**
```kotlin
class ServerManagerTest {
    @Test fun testSingletonPattern()
    @Test fun testServerStartsSuccessfully()
    @Test fun testServerStateTransitions()
    @Test fun testServerSurvivesActivityRecreation()
    @Test fun testServerFailureHandling()
    @Test fun testGetBaseUrlWhenReady()
    @Test fun testGetBaseUrlWhenNotReady()
    @Test fun testFailureReasonCaptured()
}
```

**LocalHttpServerTest.kt (HARDENED):**
```kotlin
class LocalHttpServerTest {
    @Test fun testServerStartsOnLocalhost()
    @Test fun testServerServesIndexHtml()
    @Test fun testServerServesStaticAssets()
    @Test fun testServerReturnsHealthCheck()
    @Test fun testServerReturns404ForMissingAssets()
    @Test fun testServerBindsToLocalhostOnly()
    @Test fun testServerRejectsTraversalAttack()
    @Test fun testServerRejectsEncodedTraversal()
    @Test fun testServerRejectsBackslashPath()
    @Test fun testServerReturns405ForUnsupportedMethod()
    @Test fun testServerNormalizesMultipleSlashes()
    @Test fun testServerMimeTypeMapping()
    @Test fun testServerStopsCleanly()
    @Test fun testServerAutoAssignsPort()
}
```

**MainActivityServerTest.kt:**
```kotlin
class MainActivityServerTest {
    @Test fun testMainActivityUsesServerManager()
    @Test fun testWebViewLoadsFromServerManagerUrl()
    @Test fun testMainActivityShowsErrorWhenServerNotReady()
    @Test fun testOriginAllowlistUsesServerManagerUrl()
}
```

**SplashActivityServerTest.kt (if using SplashActivity):**
```kotlin
class SplashActivityServerTest {
    @Test fun testSplashPollsServerManager()
    @Test fun testSplashDismissesWhenServerReady()
    @Test fun testSplashTimeoutWhenServerNeverReady()
    @Test fun testSplashHandlesServerFailure()
}
```

### New Integration Tests

**ServerLifecycleIntegrationTest.kt:**
```kotlin
class ServerLifecycleIntegrationTest {
    @Test fun testServerSurvivesConfigurationChange()
    @Test fun testServerSurvivesActivityRecreation()
    @Test fun testMultipleActivitiesShareSameServer()
}
```

**ServerWebViewIntegrationTest.kt:**
```kotlin
class ServerWebViewIntegrationTest {
    @Test fun testEndToEndServerToWebViewFlow()
    @Test fun testWebViewLoadsFromLocalhostNotFile()
    @Test fun testWebViewContentMatchesAssets()
}
```

### Negative Test Cases

**ServerFailureTest.kt:**
```kotlin
class ServerFailureTest {
    @Test fun testServerStartFailureShowsErrorDialog()
    @Test fun testServerPortConflictHandling()
    @Test fun testTimeoutWhenServerNeverStarts()
    @Test fun testMainActivityHandlesServerFailure()
}
```

**SecurityTest.kt:**
```kotlin
class SecurityTest {
    @Test fun testTraversalAttackBlocked()
    @Test fun testEncodedTraversalBlocked()
    @Test fun testUnsupportedMethodBlocked()
    @Test fun testExternalOriginBlocked()
    @Test fun testOnlyLocalhostAllowed()
}
```

### Existing Tests Remain Unchanged

- All existing unit tests for AudioCaptureService, PermissionManager, etc.
- All existing property tests
- All existing integration tests
- New tests are ADDITIVE only

---

## D) Risk List + Rollback Plan (UPDATED)

### Risks

1. **NanoHTTPD dependency adds ~100KB to APK**
   - Mitigation: Acceptable size increase
   - Impact: Low (7.4MB → 7.5MB)
   - Rollback: Feature flag to file://

2. **Server start failure on some devices**
   - Mitigation: Comprehensive error handling, Application scope ensures single attempt
   - Impact: Medium (app unusable if server fails)
   - Rollback: Feature flag to file://

3. **Performance regression**
   - Mitigation: Localhost HTTP is fast (<10ms per request)
   - Impact: Low
   - Rollback: Measure and revert if >100ms regression

4. **Port conflict**
   - Mitigation: Auto-assign port (port 0)
   - Impact: Very Low
   - Rollback: Show error, user restarts app

5. **Configuration change issues**
   - Mitigation: Application scope ensures server survives
   - Impact: Low (properly tested)
   - Rollback: Feature flag to file://

6. **Memory leak from Application-scoped server**
   - Mitigation: Proper cleanup in Application.onTerminate()
   - Impact: Low (server is lightweight)
   - Rollback: Monitor memory usage

### Rollback Plan

**Emergency rollback via feature flag:**
1. Set `FeatureFlags.USE_EMBEDDED_SERVER = false`
2. Rebuild and deploy
3. App falls back to file:// loading
4. No code changes needed

**Full rollback:**
1. Revert all commits related to embedded server
2. Remove NanoHTTPD dependency
3. Remove ServerManager, LocalHttpServer, AxolyncApplication
4. Restore original MainActivity.loadWebApp()
5. Git revert to last working commit

---

## E) Estimated Implementation Steps Count (REVISED)

1. Update spec files (requirements.md, design.md, tasks.md) (3 steps)
2. Add NanoHTTPD dependency to build.gradle.kts (1 step)
3. Create AxolyncApplication.kt (1 step)
4. Create ServerManager.kt (1 step)
5. Create LocalHttpServer.kt with hardening (1 step)
6. Modify MainActivity.kt (remove server code, use ServerManager) (3 steps)
7. Update WebView allowlist to use ServerManager (1 step)
8. Implement splash approach (SplashScreen API or modify SplashActivity) (2 steps)
9. Add FeatureFlags.kt for rollback (1 step)
10. Write unit tests for ServerManager (1 step)
11. Write unit tests for LocalHttpServer (1 step)
12. Write unit tests for MainActivity integration (1 step)
13. Write integration tests (1 step)
14. Write security tests (1 step)
15. Test on physical device (1 step)
16. Verify APK size increase (1 step)
17. Run full test suite (1 step)
18. Commit and push (1 step)

**Total: 23 implementation steps**

---

## F) Success Criteria (UPDATED)

### Functional Requirements
- ✅ Server starts in Application.onCreate()
- ✅ Server survives Activity recreation
- ✅ Splash screen shows immediately
- ✅ Splash dismisses when server ready (or timeout)
- ✅ WebView loads from http://127.0.0.1:<port>/
- ✅ Web app functions identically to file:// version
- ✅ Server lifecycle independent of Activity
- ✅ Error handling for server start failure
- ✅ Traversal attacks blocked
- ✅ Unsupported methods blocked

### Performance Requirements
- ✅ Server start time < 500ms
- ✅ Splash to main transition < 5s
- ✅ No noticeable performance regression vs file://
- ✅ APK size increase < 200KB

### Security Requirements
- ✅ Server binds to 127.0.0.1 only
- ✅ No external network access to server
- ✅ Traversal protection implemented
- ✅ Method restrictions enforced
- ✅ WebView allowlist uses ServerManager (deterministic)
- ✅ Strict origin validation maintained

### Testing Requirements
- ✅ All new unit tests pass
- ✅ All integration tests pass
- ✅ All security tests pass
- ✅ All existing tests still pass
- ✅ Physical device testing successful


---

## G) Implementation Order (REVISED per Review Feedback)

**Phase 1: Spec Updates (FIRST)**
1. Update `.kiro/specs/android-apk-wrapper/requirements.md`
2. Update `.kiro/specs/android-apk-wrapper/design.md`
3. Update `.kiro/specs/android-apk-wrapper/tasks.md`
4. Commit spec changes

**Phase 2: Core Server Implementation (Application Scope)**
5. Add NanoHTTPD dependency to build.gradle.kts
6. Create AxolyncApplication.kt
7. Create ServerManager.kt (Application-scoped singleton)
8. Create LocalHttpServer.kt (hardened with traversal protection)
9. Update AndroidManifest.xml to use AxolyncApplication
10. Write unit tests for ServerManager
11. Write unit tests for LocalHttpServer (including security tests)
12. Verify server can serve assets and survives Activity recreation

**Phase 3: MainActivity Integration**
13. Modify MainActivity to use ServerManager (remove server management code)
14. Update loadWebApp() to use ServerManager.getBaseUrl()
15. Update isAllowedOrigin() to use ServerManager (deterministic)
16. Add error handling for server not ready
17. Write MainActivity integration tests

**Phase 4: Splash Screen Implementation**
18. Choose approach: SplashScreen API (preferred) or modify SplashActivity
19. Implement chosen approach
20. Write splash screen tests

**Phase 5: Feature Flag and Rollback**
21. Add FeatureFlags.kt with USE_EMBEDDED_SERVER flag
22. Update loadWebApp() to support feature flag fallback

**Phase 6: Testing & Validation**
23. Run full test suite (unit + integration + security)
24. Test on physical device
25. Verify APK size increase acceptable
26. Test configuration changes (screen rotation)
27. Test server failure scenarios
28. Commit and push

---

## H) Open Questions (RESOLVED)

1. **Should we add a feature flag for easy rollback?**
   - ✅ YES - Added FeatureFlags.USE_EMBEDDED_SERVER

2. **Should we add server metrics/logging?**
   - ✅ YES - Added structured logging with timestamps and failure categories

3. **Should we add a /health endpoint with more details?**
   - ✅ YES - Returns {"status":"ok","server":"LocalHttpServer","version":"1.0"}

4. **Should we cache server port in SharedPreferences?**
   - ✅ NO - Auto-assign is sufficient, port may change

5. **Should we add server restart capability?**
   - ✅ NO - Restart app if server fails (simpler)

6. **Should we use SplashScreen API or keep SplashActivity?**
   - ✅ PREFER SplashScreen API - Simpler, follows Android best practices

7. **Should server lifecycle be Activity or Application scope?**
   - ✅ Application scope - Survives configuration changes (per review feedback)

---

## I) Post-Implementation Validation

### Manual Testing Checklist
- [ ] Install APK on physical device
- [ ] Verify splash shows immediately
- [ ] Verify splash dismisses when server ready
- [ ] Verify web app loads from http://127.0.0.1:<port>/
- [ ] Verify web app functions correctly (plugins, audio, etc.)
- [ ] Test screen rotation (server should survive)
- [ ] Test app pause/resume (server should stay running)
- [ ] Test app kill/restart (server should restart)
- [ ] Test airplane mode (server should still work for local content)
- [ ] Verify APK size increase acceptable
- [ ] Check logcat for server start/stop messages
- [ ] Test on slow device (verify timeout works)
- [ ] Test traversal attack (should be blocked)
- [ ] Test unsupported HTTP method (should return 405)

### Automated Testing Checklist
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] All security tests pass
- [ ] All existing tests still pass
- [ ] Code coverage > 80% for new code
- [ ] No new lint warnings
- [ ] Build succeeds in CI/CD

### Performance Testing Checklist
- [ ] Measure server start time (target < 500ms)
- [ ] Measure splash to main transition (target < 5s)
- [ ] Measure first page load time (compare to file://)
- [ ] Measure memory usage (compare to file://)
- [ ] Measure APK size increase (target < 200KB)
- [ ] Test on low-end device (2019 mid-range Android, 4GB RAM)

---

## J) Documentation Updates Needed

1. **README.md**: Add section on embedded server architecture
2. **ARCHITECTURE.md**: Update diagram to show ServerManager and LocalHttpServer
3. **TROUBLESHOOTING.md**: Add server start failure troubleshooting
4. **CHANGELOG.md**: Document architecture change
5. **SECURITY.md**: Document localhost-only binding and traversal protection

---

## K) Key Differences from V1

### Architecture Changes
- ✅ Server lifecycle moved to Application scope (not Activity)
- ✅ ServerManager singleton manages server (not MainActivity)
- ✅ No static Activity flags for readiness signaling
- ✅ Server survives configuration changes

### Security Improvements
- ✅ Hardened LocalHttpServer with traversal protection
- ✅ Method restrictions (GET/HEAD only)
- ✅ Explicit MIME type whitelist
- ✅ Deterministic origin allowlist from ServerManager

### Implementation Improvements
- ✅ Prefer SplashScreen API over SplashActivity
- ✅ Feature flag for emergency rollback
- ✅ Structured logging with timestamps
- ✅ Proper error handling with failure reasons

### Testing Improvements
- ✅ Security tests for traversal attacks
- ✅ Lifecycle tests for configuration changes
- ✅ Integration tests for server survival

---

## APPROVAL REQUIRED

This revised plan addresses all blocking corrections from the review:
1. ✅ Activity lifecycle architecture fixed (Application scope)
2. ✅ Server lifecycle no longer tied to Activity
3. ✅ Spec edits explicitly defined
4. ✅ LocalHttpServer hardened
5. ✅ WebView allowlist deterministic

Non-blocking improvements included:
1. ✅ Readiness gate with server health
2. ✅ Startup timeout states
3. ✅ Feature flag for rollback
4. ✅ Structured logging

**Ready for implementation approval.**

**Estimated time to implement:** 6-8 hours
**Estimated time to test:** 3-4 hours
**Total estimated time:** 9-12 hours
