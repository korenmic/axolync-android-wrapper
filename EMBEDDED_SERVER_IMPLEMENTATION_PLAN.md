# Embedded Server Implementation Plan
## Android APK Wrapper Architecture Fix

---

## Executive Summary

**Problem:** Current implementation loads static files via `file:///android_asset/` directly into WebView. This violates the intended architecture where an embedded HTTP server must run localhost, serving the axolync-browser web app.

**Solution:** Add NanoHTTPD embedded server to serve content via `http://127.0.0.1:<port>/`, with splash screen showing during server boot.

**Impact:** ~15 implementation steps, ~100KB APK size increase, minimal performance overhead.

---

## A) Gap Analysis

### Root Cause Summary
The current implementation loads static files via `file:///android_asset/` directly into WebView. This violates the intended architecture where an embedded HTTP server must run localhost, serving the axolync-browser web app. The WebView should connect to `http://127.0.0.1:<port>/` instead of loading static files.

### Exact Spec Lines Causing Wrong Architecture

**Requirements.md:**
- Requirement 6.1: "THE Android_Wrapper SHALL serve the web application content to the WebView" - implies active serving, not passive file loading
- Requirement 6.2: "THE Android_Wrapper v1 SHALL use bundled static assets served from Android app storage" - "served" implies HTTP server, not direct file access
- Requirement 11.4: "IF a local runtime endpoint is introduced in a future version, THEN it SHALL bind to localhost only" - This is written as future tense, but the architecture requires it NOW

**Design.md:**
- Architecture section states: "Built/bundled output from axolync-browser is served as static assets from `file:///android_asset/` (no embedded server in v1)" - This is the WRONG interpretation
- The design document contradicts the user's intent: server MUST run in v1

### Exact Code Paths Causing file:// Behavior

**MainActivity.kt line ~230:**
```kotlin
private fun loadWebApp() {
    webView.loadUrl("file:///android_asset/axolync-browser/index.html")
}
```

**MainActivity.kt lines ~140-160 (configureWebView):**
- WebView configured for `file://` protocol
- No server initialization
- No health check before loading

**SplashActivity.kt lines ~50-60:**
- Starts MainActivity immediately
- No server boot wait
- No health check polling

### Which Existing Components Are Reusable Unchanged

✅ **Reusable without changes:**
- AudioCaptureService
- PermissionManager  
- LifecycleCoordinator
- NetworkMonitor
- PluginManager
- NativeBridge (interface methods)
- All test files
- All resource files (layouts, drawables, etc.)

❌ **Requires changes:**
- MainActivity (add server initialization, change loadUrl)
- SplashActivity (add health check polling)
- AndroidManifest.xml (possibly add INTERNET permission if not present)
- build.gradle.kts (add embedded server dependency)

---

## B) Minimal Delta Fix Plan

### 1. Proposed Embedded Server Library

**Recommendation: NanoHTTPD**
- Lightweight (~100KB)
- Pure Java, no native dependencies
- Easy to embed
- Supports serving static files from assets
- Localhost-only binding supported
- Mature and stable

**Alternative: AndroidAsync HTTP Server**
- More features but heavier
- Async I/O

**Rationale for NanoHTTPD:**
- Minimal footprint
- Simple API
- Well-tested in Android apps
- Easy to configure for localhost-only

### 2. Required Spec Edits

**requirements.md:**
- Requirement 6.2: Change "v1 SHALL use bundled static assets served from Android app storage" to "v1 SHALL use an embedded HTTP server to serve bundled static assets from Android app storage to WebView via localhost"
- Requirement 11.3: Change from "SHALL NOT expose any externally reachable local runtime endpoint in v1" to "SHALL expose a localhost-only HTTP endpoint in v1 for serving web app content"
- Requirement 11.4: Change from "IF a local runtime endpoint is introduced in a future version" to "THE local runtime endpoint SHALL bind to localhost only"

**design.md:**
- Architecture section: Remove "(no embedded server in v1)" and replace with "An embedded HTTP server (NanoHTTPD) serves static assets from bundled storage to WebView via http://127.0.0.1:<port>/"
- Add new component section: "LocalHttpServer (Native Android)"

**tasks.md:**
- Add new task after task 1: "1.5 Implement LocalHttpServer for serving bundled assets"
- Update task 3.1: Change "Load bundled assets from file:///android_asset/" to "Load web app from http://127.0.0.1:<port>/"
- Update task 2.1: Add "Poll server health endpoint before navigating to MainActivity"

### 3. Required Code Edits by File

#### **app/build.gradle.kts**
```kotlin
dependencies {
    // ... existing dependencies ...
    
    // Embedded HTTP server
    implementation("org.nanohttpd:nanohttpd:2.3.1")
}
```

#### **New file: app/src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt**
```kotlin
package com.axolync.android.server

import android.content.Context
import android.content.res.AssetManager
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * LocalHttpServer serves bundled web application assets via HTTP on localhost.
 * This allows the WebView to load content from http://127.0.0.1:<port>/ instead of file://.
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
    
    override fun serve(session: IHTTPSession): Response {
        var uri = session.uri
        
        // Health check endpoint for splash screen polling
        if (uri == "/health") {
            return newFixedLengthResponse(
                Response.Status.OK, 
                "application/json", 
                "{\"status\":\"ok\"}"
            )
        }
        
        // Default to index.html
        if (uri == "/" || uri.isEmpty()) {
            uri = "/index.html"
        }
        
        // Remove leading slash and construct asset path
        val assetPath = "$assetBasePath${uri}"
        
        return try {
            val inputStream = assetManager.open(assetPath)
            val mimeType = getMimeType(uri)
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            android.util.Log.w("LocalHttpServer", "Asset not found: $assetPath")
            newFixedLengthResponse(
                Response.Status.NOT_FOUND, 
                "text/plain", 
                "404 Not Found: $uri"
            )
        }
    }
    
    private fun getMimeType(uri: String): String {
        return when {
            uri.endsWith(".html") -> "text/html"
            uri.endsWith(".js") -> "application/javascript"
            uri.endsWith(".css") -> "text/css"
            uri.endsWith(".json") -> "application/json"
            uri.endsWith(".png") -> "image/png"
            uri.endsWith(".jpg") || uri.endsWith(".jpeg") -> "image/jpeg"
            uri.endsWith(".svg") -> "image/svg+xml"
            uri.endsWith(".woff") || uri.endsWith(".woff2") -> "font/woff2"
            uri.endsWith(".ttf") -> "font/ttf"
            uri.endsWith(".map") -> "application/json"
            else -> "application/octet-stream"
        }
    }
    
    fun getServerUrl(): String {
        return "http://127.0.0.1:${listeningPort}"
    }
    
    fun isRunning(): Boolean {
        return isAlive
    }
}
```

#### **MainActivity.kt changes:**

**Add field:**
```kotlin
private lateinit var localHttpServer: LocalHttpServer

companion object {
    private const val TAG = "MainActivity"
    private const val PERMISSION_REQUEST_CODE = 1001
    
    @Volatile
    private var serverReady = false
    
    fun isServerReady(): Boolean = serverReady
}
```

**Modify onCreate (before configureWebView):**
```kotlin
override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.activity_main)

    webView = findViewById(R.id.webview)
    
    // Start embedded HTTP server FIRST
    localHttpServer = LocalHttpServer(this)
    try {
        localHttpServer.start()
        serverReady = true
        android.util.Log.i(TAG, "HTTP server started at ${localHttpServer.getServerUrl()}")
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Failed to start HTTP server", e)
        showServerStartError()
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
    
    // Signal SplashActivity that we're ready
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
    val serverUrl = localHttpServer.getServerUrl()
    android.util.Log.i(TAG, "Loading web app from $serverUrl")
    webView.loadUrl("$serverUrl/index.html")
}
```

**Add to onDestroy:**
```kotlin
override fun onDestroy() {
    super.onDestroy()
    
    // Stop HTTP server
    try {
        localHttpServer.stop()
        android.util.Log.i(TAG, "HTTP server stopped")
    } catch (e: Exception) {
        android.util.Log.e(TAG, "Error stopping HTTP server", e)
    }
    
    networkMonitor.unregisterCallback()
    audioCaptureService.stopCapture()
    webView.destroy()
}
```

**Add error handler:**
```kotlin
private fun showServerStartError() {
    androidx.appcompat.app.AlertDialog.Builder(this)
        .setTitle("Server Error")
        .setMessage("Failed to start internal server. Please restart the app.")
        .setPositiveButton("Exit") { _, _ -> finish() }
        .setCancelable(false)
        .show()
}
```

**Update isAllowedOrigin in configureWebView:**
```kotlin
private fun isAllowedOrigin(uri: Uri): Boolean {
    // Define allowed origins with explicit scheme, host, and port
    val allowedOrigins = mutableListOf<Triple<String, String, Int>>()
    
    // Add localhost server origin
    if (::localHttpServer.isInitialized && localHttpServer.isRunning()) {
        allowedOrigins.add(Triple("http", "127.0.0.1", localHttpServer.listeningPort))
        allowedOrigins.add(Triple("http", "localhost", localHttpServer.listeningPort))
    }
    
    // Add external API origins as needed
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

#### **SplashActivity.kt changes:**

**Modify checkInitialization:**
```kotlin
/**
 * Implements initialization check logic with timeout.
 * Waits for server to be ready via health check polling, then navigates to MainActivity.
 * 
 * CORRECTED BEHAVIOR:
 * - Splash shows IMMEDIATELY on app launch
 * - MainActivity starts in background and boots HTTP server
 * - Splash polls server health every 100ms
 * - When server ready OR timeout (5s): dismiss splash and show MainActivity
 */
private fun checkInitialization() {
    // Set up callback for ready signal
    readyCallback = {
        if (!hasNavigated) {
            handler.post {
                navigateToMain()
            }
        }
    }

    // Set up timeout - navigate to main after 5 seconds regardless
    handler.postDelayed({
        if (!hasNavigated) {
            android.util.Log.w("SplashActivity", "Timeout reached, navigating to main")
            navigateToMain()
        }
    }, splashTimeout)

    // Start MainActivity immediately (it will load in background)
    val intent = Intent(this, MainActivity::class.java)
    startActivity(intent)
    
    // Start polling server health
    pollServerHealth()
}

/**
 * Polls MainActivity.isServerReady() every 100ms until server is ready.
 * When ready, dismisses splash and shows MainActivity.
 */
private fun pollServerHealth() {
    handler.postDelayed(object : Runnable {
        override fun run() {
            if (hasNavigated) return
            
            // Check if server is ready via MainActivity static method
            if (MainActivity.isServerReady()) {
                android.util.Log.i("SplashActivity", "Server ready, navigating to main")
                navigateToMain()
            } else {
                // Poll again in 100ms
                handler.postDelayed(this, 100)
            }
        }
    }, 100)
}
```

### 4. Startup/Readiness Sequence (CORRECTED)

```
1. App launches
   ↓
2. SplashActivity.onCreate() - SPLASH SHOWS IMMEDIATELY
   ↓
3. SplashActivity starts MainActivity (in background, not visible yet)
   ↓
4. MainActivity.onCreate()
   ↓
5. LocalHttpServer.start() on 127.0.0.1:0 (auto-assign port)
   ↓
6. Set MainActivity.serverReady = true
   ↓
7. SplashActivity polls MainActivity.isServerReady() every 100ms
   ↓
8. When serverReady == true:
   ↓
9. SplashActivity.finish() - SPLASH DISAPPEARS
   ↓
10. MainActivity becomes visible
    ↓
11. WebView.loadUrl("http://127.0.0.1:<port>/index.html")
    ↓
12. Web app loads and signals appReady()
    ↓
13. User sees web app interface

TIMEOUT PATH (if server never ready):
8. After 5 seconds timeout:
   ↓
9. SplashActivity.finish() anyway
   ↓
10. MainActivity shows (may show error if server failed)
```

**Key Points:**
- Splash is ALWAYS visible from app launch
- MainActivity starts in background (not visible)
- Server boots while splash is showing
- Splash disappears only when server ready OR timeout
- User never sees blank screen or loading state

### 5. Failure UX When Server Fails to Start

**Scenario: Server start throws exception**
- MainActivity.onCreate() catches exception
- Set serverReady = false (never becomes true)
- Show AlertDialog: "Failed to start internal server. Please restart the app."
- Single button: "Exit" → finish()
- Log error with stack trace
- SplashActivity timeout (5s) will eventually dismiss splash
- User sees error dialog on MainActivity

**Scenario: Server starts but health check fails**
- Timeout after 5 seconds
- Navigate to MainActivity anyway (existing timeout behavior)
- If WebView fails to load, show error page

### 6. Security Constraints

**Localhost-only binding:**
- NanoHTTPD constructor: `NanoHTTPD("127.0.0.1", port)`
- NOT `NanoHTTPD(port)` which binds to 0.0.0.0
- Prevents external network access to server

**WebView origin allowlist:**
- Add `http://127.0.0.1:<port>` to allowed origins
- Add `http://localhost:<port>` to allowed origins
- Keep existing external API origins
- Maintain strict scheme+host+port validation

**No external access:**
- Server binds to 127.0.0.1 only
- Android firewall prevents external connections to localhost
- INTERNET permission already present for external APIs (no change needed)

---

## C) Testing Plan Additions

### New Unit Tests

**LocalHttpServerTest.kt:**
```kotlin
class LocalHttpServerTest {
    @Test fun testServerStartsOnLocalhost()
    @Test fun testServerServesIndexHtml()
    @Test fun testServerServesStaticAssets()
    @Test fun testServerReturnsHealthCheck()
    @Test fun testServerReturns404ForMissingAssets()
    @Test fun testServerBindsToLocalhostOnly()
    @Test fun testServerStopsCleanly()
    @Test fun testServerAutoAssignsPort()
}
```

**MainActivityServerTest.kt:**
```kotlin
class MainActivityServerTest {
    @Test fun testServerStartsBeforeWebViewLoad()
    @Test fun testWebViewLoadsFromLocalhost()
    @Test fun testServerStopsOnDestroy()
    @Test fun testServerStartFailureShowsError()
    @Test fun testServerReadyFlagSetAfterStart()
}
```

**SplashActivityServerTest.kt:**
```kotlin
class SplashActivityServerTest {
    @Test fun testSplashWaitsForServerReady()
    @Test fun testSplashPollsServerHealth()
    @Test fun testSplashTimeoutWhenServerNeverReady()
    @Test fun testSplashDismissesWhenServerReady()
    @Test fun testSplashShowsImmediately()
}
```

### New Integration Tests

**ServerWebViewIntegrationTest.kt:**
```kotlin
class ServerWebViewIntegrationTest {
    @Test fun testEndToEndServerToWebViewFlow()
    @Test fun testServerReadySignalTriggersNavigation()
    @Test fun testWebViewLoadsContentFromServer()
    @Test fun testSplashToMainTransition()
}
```

### Negative Test Cases

**ServerFailureTest.kt:**
```kotlin
class ServerFailureTest {
    @Test fun testServerStartFailureShowsErrorDialog()
    @Test fun testServerPortConflictHandling()
    @Test fun testServerStopDuringLoad()
    @Test fun testTimeoutWhenServerNeverStarts()
}
```

### Existing Tests Remain

- All existing unit tests for AudioCaptureService, PermissionManager, etc. remain unchanged
- All existing property tests remain unchanged
- New tests are ADDITIVE only

---

## D) Risk List + Rollback Plan

### Risks

1. **NanoHTTPD dependency adds ~100KB to APK**
   - Mitigation: Acceptable size increase for correct architecture
   - Impact: Low (APK currently 7.4MB, will become ~7.5MB)
   - Rollback: Remove dependency, revert to file://

2. **Server start failure on some devices**
   - Mitigation: Comprehensive error handling, user-friendly error message
   - Impact: Medium (app unusable if server fails)
   - Rollback: Fallback to file:// if server fails (requires feature flag)

3. **Performance regression (server overhead)**
   - Mitigation: Localhost HTTP is fast, minimal overhead (<10ms per request)
   - Impact: Low (localhost is faster than network, similar to file://)
   - Rollback: Measure performance, revert if >100ms regression

4. **Port conflict (unlikely with auto-assign)**
   - Mitigation: Use port 0 for auto-assignment by OS
   - Impact: Very Low (OS guarantees available port)
   - Rollback: Retry with different port or show error

5. **WebView security policy blocks localhost**
   - Mitigation: Update allowlist to include localhost
   - Impact: Low (localhost is standard WebView use case)
   - Rollback: Adjust security policy or revert to file://

6. **Splash screen timing issues**
   - Mitigation: 100ms polling interval, 5s timeout
   - Impact: Low (existing timeout behavior preserved)
   - Rollback: Adjust polling interval or timeout

### Rollback Plan

**If implementation fails:**
1. Revert MainActivity.kt changes (remove server code)
2. Revert SplashActivity.kt changes (remove health polling)
3. Revert build.gradle.kts (remove NanoHTTPD dependency)
4. Delete LocalHttpServer.kt
5. Restore `webView.loadUrl("file:///android_asset/axolync-browser/index.html")`
6. Git revert to last working commit

**Feature flag approach (safer):**
```kotlin
object FeatureFlags {
    const val USE_EMBEDDED_SERVER = true  // Set to false to use file://
}

// In MainActivity:
if (FeatureFlags.USE_EMBEDDED_SERVER) {
    // Use localhost server
} else {
    // Use file:// (fallback)
}
```
- Allows A/B testing and gradual rollout
- Can disable server without code changes
- Useful for debugging and testing

---

## E) Estimated Implementation Steps Count

1. Add NanoHTTPD dependency to build.gradle.kts (1 step)
2. Create LocalHttpServer.kt (1 step)
3. Modify MainActivity.kt (server initialization, loadUrl change, error handling, companion object) (4 steps)
4. Modify SplashActivity.kt (health polling, corrected flow) (2 steps)
5. Update WebView security allowlist in MainActivity (1 step)
6. Write unit tests for LocalHttpServer (1 step)
7. Write unit tests for MainActivity server integration (1 step)
8. Write unit tests for SplashActivity server polling (1 step)
9. Write integration tests (1 step)
10. Update spec files (requirements.md, design.md, tasks.md) (3 steps)
11. Test on physical device (1 step)
12. Verify APK size increase acceptable (1 step)
13. Run full test suite (1 step)
14. Commit and push (1 step)

**Total: 20 implementation steps**

---

## F) Success Criteria

### Functional Requirements
- ✅ Splash screen shows immediately on app launch
- ✅ HTTP server starts on localhost during splash
- ✅ Splash dismisses when server ready (or timeout)
- ✅ WebView loads from http://127.0.0.1:<port>/
- ✅ Web app functions identically to file:// version
- ✅ Server stops cleanly on app destroy
- ✅ Error handling for server start failure

### Performance Requirements
- ✅ Server start time < 500ms
- ✅ Splash to main transition < 5s (existing timeout)
- ✅ No noticeable performance regression vs file://
- ✅ APK size increase < 200KB

### Security Requirements
- ✅ Server binds to 127.0.0.1 only
- ✅ No external network access to server
- ✅ WebView allowlist includes localhost
- ✅ Strict origin validation maintained

### Testing Requirements
- ✅ All new unit tests pass
- ✅ All integration tests pass
- ✅ All existing tests still pass
- ✅ Physical device testing successful

---

## G) Implementation Order

**Phase 1: Core Server Implementation**
1. Add NanoHTTPD dependency
2. Create LocalHttpServer.kt
3. Write LocalHttpServer unit tests
4. Verify server can serve assets

**Phase 2: MainActivity Integration**
5. Add server initialization to MainActivity
6. Update loadWebApp() to use localhost
7. Add error handling
8. Update WebView allowlist
9. Write MainActivity server tests

**Phase 3: SplashActivity Integration**
10. Add health polling to SplashActivity
11. Add serverReady companion object to MainActivity
12. Write SplashActivity server tests
13. Write integration tests

**Phase 4: Spec Updates**
14. Update requirements.md
15. Update design.md
16. Update tasks.md

**Phase 5: Testing & Validation**
17. Run full test suite
18. Test on physical device
19. Verify APK size
20. Commit and push

---

## H) Open Questions

1. **Should we add a feature flag for easy rollback?**
   - Recommendation: Yes, add `USE_EMBEDDED_SERVER` boolean flag
   - Allows quick disable if issues found in production

2. **Should we add server metrics/logging?**
   - Recommendation: Yes, log server start time, request count, errors
   - Helps diagnose issues in production

3. **Should we add a /health endpoint with more details?**
   - Recommendation: Yes, return server version, uptime, asset count
   - Useful for debugging

4. **Should we cache server port in SharedPreferences?**
   - Recommendation: No, auto-assign is sufficient
   - Port may change between app restarts

5. **Should we add server restart capability?**
   - Recommendation: No, restart app if server fails
   - Simpler error handling

---

## I) Post-Implementation Validation

### Manual Testing Checklist
- [ ] Install APK on physical device
- [ ] Verify splash shows immediately
- [ ] Verify splash dismisses when server ready
- [ ] Verify web app loads and functions correctly
- [ ] Test app pause/resume (server should stay running)
- [ ] Test app kill/restart (server should restart)
- [ ] Test airplane mode (server should still work for local content)
- [ ] Verify APK size increase acceptable
- [ ] Check logcat for server start/stop messages
- [ ] Test on slow device (verify timeout works)

### Automated Testing Checklist
- [ ] All unit tests pass
- [ ] All integration tests pass
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

---

## J) Documentation Updates Needed

1. **README.md**: Add section on embedded server architecture
2. **ARCHITECTURE.md**: Update diagram to show LocalHttpServer
3. **TROUBLESHOOTING.md**: Add server start failure troubleshooting
4. **CHANGELOG.md**: Document architecture change
5. **API.md**: Document /health endpoint

---

## APPROVAL REQUIRED

This plan is ready for review. Please provide feedback or approval before proceeding with implementation.

**Estimated time to implement:** 4-6 hours
**Estimated time to test:** 2-3 hours
**Total estimated time:** 6-9 hours
