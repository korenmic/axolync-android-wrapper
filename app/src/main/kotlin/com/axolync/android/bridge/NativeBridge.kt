package com.axolync.android.bridge

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.axolync.android.BuildConfig
import com.axolync.android.services.AudioCaptureService
import com.axolync.android.services.PermissionManager
import com.axolync.android.services.StatusBarSongSignalService
import org.json.JSONArray
import org.json.JSONObject

/**
 * NativeBridge provides minimal bidirectional communication between native Android and web application.
 * Exposed to JavaScript as window.AndroidBridge.
 * 
 * Requirements: 3.3, 3.4, 7.3, 10.1, 10.2
 */
class NativeBridge(
    private val context: Context,
    private val webView: WebView,
    private val audioCaptureService: AudioCaptureService,
    private val permissionManager: PermissionManager,
    private val getNetworkStatusCallback: () -> Pair<Boolean, String>
) {

    companion object {
        private const val TAG = "NativeBridge"
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * Start audio capture from microphone.
     * Returns JSON with success status.
     * Requirements: 3.3, 3.4
     */
    @JavascriptInterface
    fun startAudioCapture(): String {
        return try {
            val permissionStatus = permissionManager.checkMicrophonePermission()
            if (permissionStatus != PermissionManager.PermissionStatus.GRANTED) {
                JSONObject().apply {
                    put("success", false)
                    put("error", "Microphone permission not granted")
                }.toString()
            } else {
                // Set callback for audio chunks
                audioCaptureService.setAudioCallback { audioData ->
                    deliverAudioChunk(audioData)
                }
                
                // Start capture
                val result = audioCaptureService.startCapture()
                if (result.isSuccess) {
                    JSONObject().apply {
                        put("success", true)
                    }.toString()
                } else {
                    JSONObject().apply {
                        put("success", false)
                        put("error", result.exceptionOrNull()?.message ?: "Unknown error")
                    }.toString()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start audio capture", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Stop audio capture.
     * Returns JSON with success status.
     * Requirements: 3.3, 3.4
     */
    @JavascriptInterface
    fun stopAudioCapture(): String {
        return try {
            audioCaptureService.stopCapture()
            JSONObject().apply {
                put("success", true)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop audio capture", e)
            JSONObject().apply {
                put("success", false)
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Check microphone permission status.
     * Returns JSON with status: "granted", "denied", or "denied_permanently".
     * Requirements: 3.1, 3.2
     */
    @JavascriptInterface
    fun checkMicrophonePermission(): String {
        val status = permissionManager.checkMicrophonePermission()
        return JSONObject().apply {
            put("status", when (status) {
                PermissionManager.PermissionStatus.GRANTED -> "granted"
                PermissionManager.PermissionStatus.DENIED -> "denied"
                PermissionManager.PermissionStatus.DENIED_PERMANENTLY -> "denied_permanently"
            })
        }.toString()
    }

    /**
     * Request microphone permission from user.
     * Result will be delivered via notifyPermissionResult().
     * Requirements: 3.1, 3.2
     */
    @JavascriptInterface
    fun requestMicrophonePermission() {
        mainHandler.post {
            permissionManager.requestMicrophonePermission()
        }
    }

    /**
     * Open app settings for user to manually grant permissions.
     * Requirements: 3.2
     */
    @JavascriptInterface
    fun openAppSettings() {
        mainHandler.post {
            permissionManager.openAppSettings()
        }
    }

    /**
     * Get current network connectivity status.
     * Returns JSON with online status and connection type.
     * Requirements: 7.2, 7.3
     */
    @JavascriptInterface
    fun getNetworkStatus(): String {
        val (isOnline, connectionType) = getNetworkStatusCallback()
        return JSONObject().apply {
            put("online", isOnline)
            put("connectionType", connectionType)
        }.toString()
    }

    /**
     * Returns structured status-bar notification listener access state required by the Shazam adapter.
     *
     * Response JSON shape:
     *   enabled     : Boolean  – true if notification listener is currently granted
     *   state       : String   – "granted" | "not_granted" | "restricted" | "unavailable" | "error"
     *   reasonCode  : String   – stable short code for the non-granted reason (may be empty)
     *   message     : String   – human-readable diagnostic (may be empty)
     *
     * "restricted" state means the app was sideloaded on Android 13+ and the user must first
     * go to App Info → Allow Restricted Settings before notification access can be toggled.
     */
    @JavascriptInterface
    fun getStatusBarAccessStatus(): String {
        return try {
            val enabled = isNotificationAccessEnabled()
            val restricted = !enabled && isRestrictedFromSensitiveSettings()
            val state = when {
                enabled -> "granted"
                restricted -> "restricted"
                else -> "not_granted"
            }
            val reasonCode = when (state) {
                "restricted" -> "restricted_settings"
                "not_granted" -> "listener_not_enabled"
                else -> ""
            }
            val message = when (state) {
                "restricted" -> "App requires 'Allow Restricted Settings' in App Info before notification access can be granted"
                else -> ""
            }
            JSONObject().apply {
                put("enabled", enabled)
                put("state", state)
                put("reasonCode", reasonCode)
                put("message", message)
            }.toString()
        } catch (e: Exception) {
            Log.e(TAG, "getStatusBarAccessStatus failed", e)
            JSONObject().apply {
                put("enabled", false)
                put("state", "error")
                put("reasonCode", "bridge_exception")
                put("message", e.message ?: "Unknown error")
            }.toString()
        }
    }

    /**
     * Opens Android notification listener settings so user can grant status-bar access.
     *
     * Routing order:
     *   1. ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS (API 30+, component-scoped)
     *   2. ACTION_NOTIFICATION_LISTENER_SETTINGS (full list fallback)
     *
     * If the app is in "restricted" state (sideloaded on Android 13+), callers should
     * invoke openAppInfoSettings() first so the user can allow restricted settings.
     */
    @JavascriptInterface
    fun requestStatusBarAccessPermission() {
        mainHandler.post {
            try {
                val resolver = context.packageManager
                val listenerComponent = ComponentName(context, StatusBarSongSignalService::class.java)
                val detailIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).apply {
                    // API 30+: Settings expects a flattened ComponentName string.
                    putExtra(
                        Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                        listenerComponent.flattenToString()
                    )
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
                    detailIntent.resolveActivity(resolver) != null
                ) {
                    Log.d(TAG, "requestStatusBarAccessPermission: routing to DETAIL_SETTINGS")
                    context.startActivity(detailIntent)
                    return@post
                }

                Log.d(TAG, "requestStatusBarAccessPermission: routing to LISTENER_SETTINGS fallback")
                val fallbackIntent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification listener settings", e)
            }
        }
    }

    /**
     * Opens the system App Info page for this app.
     * On Android 13+, the user can tap ⋮ → "Allow Restricted Settings" here to unblock
     * notification access for sideloaded builds before going to notification access settings.
     */
    @JavascriptInterface
    fun openAppInfoSettings() {
        mainHandler.post {
            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                Log.d(TAG, "openAppInfoSettings: routing to APPLICATION_DETAILS_SETTINGS")
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open app info settings", e)
            }
        }
    }

    /**
     * Returns latest song-like status-bar signal captured by native notification listener.
     * Confidence is always 1.0 for direct status-bar matches.
     */
    @JavascriptInterface
    fun getAutoShazamStatusBarMatch(): String {
        val enabled = isNotificationAccessEnabled()
        val match = StatusBarSongSignalStore.latestSignal()
        return JSONObject().apply {
            put("enabled", enabled)
            if (match != null) {
                put(
                    "match",
                    JSONObject().apply {
                        put("songId", "${match.sourcePackage}:${match.title}:${match.artist ?: ""}")
                        put("title", match.title)
                        put("artist", match.artist ?: "")
                        put("confidence", 1.0)
                        put("source", "statusbar_shazam")
                        put("capturedAtMs", match.capturedAtMs)
                        put("sourcePackage", match.sourcePackage)
                    }
                )
            } else {
                put("match", JSONObject.NULL)
            }
        }.toString()
    }

    /**
     * Enables or disables debug capture of raw status-bar notification payloads.
     * When disabled, existing buffer is preserved but no new rows are appended.
     */
    @JavascriptInterface
    fun setStatusBarDebugCaptureEnabled(enabled: Boolean) {
        StatusBarSongSignalStore.setDebugCaptureEnabled(enabled)
    }

    /**
     * Returns captured raw notification debug rows for in-app diagnostics UI.
     */
    @JavascriptInterface
    fun getStatusBarDebugCaptureLog(): String {
        val rows = StatusBarSongSignalStore.debugEntriesSnapshot(limit = 400)
        return JSONObject().apply {
            put("debugBuild", BuildConfig.DEBUG)
            put("captureEnabled", StatusBarSongSignalStore.isDebugCaptureEnabled())
            put("entryCount", rows.size)
            put(
                "entries",
                JSONArray().apply {
                    rows.forEach { row ->
                        put(
                            JSONObject().apply {
                                put("sourcePackage", row.sourcePackage)
                                put("titleRaw", row.titleRaw ?: "")
                                put("textRaw", row.textRaw ?: "")
                                put("subTextRaw", row.subTextRaw ?: "")
                                put("bigTextRaw", row.bigTextRaw ?: "")
                                put("tickerRaw", row.tickerRaw ?: "")
                                put("category", row.category ?: "")
                                put("capturedAtMs", row.capturedAtMs)
                                put("parseReasonCode", row.parseReasonCode)
                                put("parsedTitle", row.parsedTitle ?: "")
                                put("parsedArtist", row.parsedArtist ?: "")
                            }
                        )
                    }
                }
            )
        }.toString()
    }

    @JavascriptInterface
    fun clearStatusBarDebugCaptureLog() {
        StatusBarSongSignalStore.clearDebugEntries()
    }

    @JavascriptInterface
    fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    /**
     * Called by web application when initialization is complete.
     * No longer needed with Android SplashScreen API (splash dismisses automatically).
     * Kept for backward compatibility with web app.
     * Requirements: 2.1, 2.2
     */
    @JavascriptInterface
    fun appReady() {
        Log.d(TAG, "Web app signaled ready state")
        // No action needed - Android SplashScreen API handles splash dismissal
    }

    /**
     * Log error from web application to native logs.
     * Requirements: 7.3
     */
    @JavascriptInterface
    fun logError(message: String) {
        Log.e(TAG, "Web app error: $message")
    }

    /**
     * Deliver audio chunk to web application.
     * Converts Float32Array to JSON array for transfer.
     * Requirements: 3.4, 8.1
     */
    fun deliverAudioChunk(float32Pcm: FloatArray) {
        mainHandler.post {
            try {
                // Convert FloatArray to JSON array for JavaScript
                val jsonArray = JSONArray()
                for (sample in float32Pcm) {
                    jsonArray.put(sample.toDouble())
                }
                
                val javascript = """
                    if (window.AxolyncNative && window.AxolyncNative.onAudioChunk) {
                        window.AxolyncNative.onAudioChunk(new Float32Array($jsonArray));
                    }
                """.trimIndent()
                
                webView.evaluateJavascript(javascript, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to deliver audio chunk", e)
            }
        }
    }

    /**
     * Notify web application of lifecycle event.
     * Requirements: 10.1, 10.2
     */
    fun notifyLifecycleEvent(event: String) {
        mainHandler.post {
            try {
                val javascript = """
                    if (window.AxolyncNative && window.AxolyncNative.onLifecycle) {
                        window.AxolyncNative.onLifecycle('$event');
                    }
                """.trimIndent()
                
                webView.evaluateJavascript(javascript, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify lifecycle event", e)
            }
        }
    }

    /**
     * Notify web application of permission request result.
     * Requirements: 3.2
     */
    fun notifyPermissionResult(status: String) {
        mainHandler.post {
            try {
                val javascript = """
                    if (window.AxolyncNative && window.AxolyncNative.onPermissionResult) {
                        window.AxolyncNative.onPermissionResult('$status');
                    }
                """.trimIndent()
                
                webView.evaluateJavascript(javascript, null)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to notify permission result", e)
            }
        }
    }

    private fun isNotificationAccessEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: return false
        return enabledListeners.contains(context.packageName, ignoreCase = true)
    }

    /**
     * Returns true if this app is likely subject to Android 13+ Restricted Settings.
     *
     * Restricted Settings blocks sideloaded apps (installed outside Play Store / Galaxy Store)
     * from appearing in sensitive settings screens such as Notification Access until the user
     * manually allows restricted settings via App Info → ⋮ → Allow Restricted Settings.
     *
     * Detection: if the installing source is not a known trusted store, we treat the app
     * as restricted. This is a heuristic — it cannot be read directly from the system.
     */
    private fun isRestrictedFromSensitiveSettings(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        return try {
            val installSourceInfo = context.packageManager.getInstallSourceInfo(context.packageName)
            val installer = installSourceInfo.installingPackageName
            val trustedInstallers = setOf(
                "com.android.vending",              // Google Play Store
                "com.sec.android.app.samsungapps"   // Samsung Galaxy Store
            )
            installer == null || installer !in trustedInstallers
        } catch (e: Exception) {
            Log.w(TAG, "isRestrictedFromSensitiveSettings: could not determine installer", e)
            false
        }
    }
}
