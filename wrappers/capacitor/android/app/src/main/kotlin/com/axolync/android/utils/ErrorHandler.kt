package com.axolync.android.utils

import android.app.AlertDialog
import android.content.Context
import android.util.Log
import android.widget.Toast

/**
 * ErrorHandler provides centralized error handling and user feedback.
 * 
 * Requirements: 3.2, 5.11, 7.2, 7.5
 */
object ErrorHandler {

    private const val TAG = "ErrorHandler"

    /**
     * Show error dialog to user.
     * Requirements: 3.2, 5.11
     */
    fun showErrorDialog(
        context: Context,
        title: String,
        message: String,
        onDismiss: (() -> Unit)? = null
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
                onDismiss?.invoke()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * Show permission denied error.
     * Requirements: 3.2
     */
    fun showPermissionDeniedError(context: Context, onOpenSettings: () -> Unit) {
        AlertDialog.Builder(context)
            .setTitle("Microphone Permission Required")
            .setMessage("Axolync needs microphone access to capture audio. Please grant permission in settings.")
            .setPositiveButton("Open Settings") { dialog, _ ->
                dialog.dismiss()
                onOpenSettings()
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    /**
     * Show audio hardware error.
     * Requirements: 3.2
     */
    fun showAudioHardwareError(context: Context) {
        showErrorDialog(
            context,
            "Audio Capture Error",
            "Failed to access microphone. Please check that no other app is using the microphone and try again."
        )
    }

    /**
     * Show audio capture timeout error.
     * Requirements: 3.2
     */
    fun showAudioCaptureTimeoutError(context: Context) {
        showErrorDialog(
            context,
            "Audio Capture Timeout",
            "Audio capture timed out. Please try again."
        )
    }

    /**
     * Show plugin installation error.
     * Requirements: 5.11
     */
    fun showPluginInstallError(context: Context, pluginId: String, error: String) {
        showErrorDialog(
            context,
            "Plugin Installation Failed",
            "Failed to install plugin '$pluginId': $error"
        )
        logError("Plugin installation failed: $pluginId", Exception(error))
    }

    /**
     * Show plugin update error.
     * Requirements: 5.11
     */
    fun showPluginUpdateError(context: Context, pluginId: String, error: String) {
        showErrorDialog(
            context,
            "Plugin Update Failed",
            "Failed to update plugin '$pluginId': $error. The previous version has been restored."
        )
        logError("Plugin update failed: $pluginId", Exception(error))
    }

    /**
     * Show network error message.
     * Requirements: 7.2
     */
    fun showNetworkError(context: Context, message: String = "No internet connection") {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        logError("Network error: $message", null)
    }

    /**
     * Show connectivity status message.
     * Requirements: 7.2, 7.5
     */
    fun showConnectivityStatus(context: Context, isOnline: Boolean) {
        val message = if (isOnline) "Connected" else "Offline"
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /**
     * Show state restoration failure.
     * Requirements: 7.5
     */
    fun showStateRestorationError(context: Context) {
        Toast.makeText(
            context,
            "Failed to restore previous state. Starting fresh.",
            Toast.LENGTH_LONG
        ).show()
        logError("State restoration failed", null)
    }

    /**
     * Show asset load failure.
     * Requirements: 7.2
     */
    fun showAssetLoadError(context: Context) {
        showErrorDialog(
            context,
            "Failed to Load Application",
            "The web application failed to load. Please restart the app."
        )
    }

    /**
     * Log error with context.
     * Requirements: 3.2, 5.11, 7.2
     */
    fun logError(message: String, throwable: Throwable?) {
        if (throwable != null) {
            Log.e(TAG, message, throwable)
        } else {
            Log.e(TAG, message)
        }
    }

    /**
     * Log warning with context.
     */
    fun logWarning(message: String) {
        Log.w(TAG, message)
    }

    /**
     * Log info with context.
     */
    fun logInfo(message: String) {
        Log.i(TAG, message)
    }
}
