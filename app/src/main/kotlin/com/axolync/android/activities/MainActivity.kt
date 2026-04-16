package com.axolync.android.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.axolync.android.R
import com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var startupSplashOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(AxolyncNativeServiceCompanionHostPlugin::class.java)
        super.onCreate(savedInstanceState)
        showStartupSplashOverlay()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        removeStartupSplashOverlay(immediate = true)
        super.onDestroy()
    }

    private fun showStartupSplashOverlay() {
        if (startupSplashOverlay != null) return
        val overlay = LayoutInflater.from(this).inflate(R.layout.activity_splash, null, false).apply {
            alpha = 1f
            isClickable = true
            isFocusable = true
        }
        addContentView(
            overlay,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        startupSplashOverlay = overlay
        mainHandler.postDelayed({
            overlay.animate()
                .alpha(0f)
                .setDuration(STARTUP_SPLASH_FADE_OUT_MS)
                .withEndAction {
                    if (startupSplashOverlay === overlay) {
                        removeStartupSplashOverlay()
                    }
                }
                .start()
        }, STARTUP_SPLASH_MIN_DURATION_MS)
    }

    private fun removeStartupSplashOverlay(immediate: Boolean = false) {
        val overlay = startupSplashOverlay ?: return
        startupSplashOverlay = null
        overlay.animate().cancel()
        val parent = overlay.parent as? ViewGroup
        if (immediate || parent == null) {
            parent?.removeView(overlay)
            return
        }
        parent.removeView(overlay)
    }

    companion object {
        private const val STARTUP_SPLASH_MIN_DURATION_MS = 2200L
        private const val STARTUP_SPLASH_FADE_OUT_MS = 260L
    }
}
