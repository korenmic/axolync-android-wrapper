package com.axolync.android.bridge

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.axolync.android.services.AudioCaptureService
import com.axolync.android.services.PermissionManager
import org.json.JSONArray
import org.json.JSONObject

/**
 * NativeBridge provides minimal bidirectional communication between native Android and web application.
 * Exposed to JavaScript as window.AndroidBridge.
 * 
 * Requirements: 3.3, 3.4, 7.3, 10.1, 10.2
 */
class NativeBridge(
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
}
