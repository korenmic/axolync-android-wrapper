package com.axolync.android.activities

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import com.axolync.android.R
import com.axolync.android.bridge.AxolyncDebugArchiveSavePlugin
import com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin
import com.getcapacitor.BridgeActivity

class MainActivity : BridgeActivity() {

    private val mainHandler = Handler(Looper.getMainLooper())
    private var startupSplashOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        registerPlugin(AxolyncDebugArchiveSavePlugin::class.java)
        registerPlugin(AxolyncNativeServiceCompanionHostPlugin::class.java)
        super.onCreate(savedInstanceState)
        installAndroidTextSelectionPolicy()
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

    private fun installAndroidTextSelectionPolicy() {
        val webView = bridge?.webView ?: return
        injectAndroidTextSelectionPolicy(webView)
        mainHandler.postDelayed({ injectAndroidTextSelectionPolicy(webView) }, TEXT_SELECTION_POLICY_RETRY_DELAY_MS)
        mainHandler.postDelayed({ injectAndroidTextSelectionPolicy(webView) }, TEXT_SELECTION_POLICY_LATE_RETRY_DELAY_MS)
    }

    private fun injectAndroidTextSelectionPolicy(webView: WebView) {
        webView.evaluateJavascript(ANDROID_TEXT_SELECTION_POLICY_SCRIPT, null)
    }

    companion object {
        private const val STARTUP_SPLASH_MIN_DURATION_MS = 2200L
        private const val STARTUP_SPLASH_FADE_OUT_MS = 260L
        private const val TEXT_SELECTION_POLICY_RETRY_DELAY_MS = 450L
        private const val TEXT_SELECTION_POLICY_LATE_RETRY_DELAY_MS = 1600L
        private val ANDROID_TEXT_SELECTION_POLICY_SCRIPT = """
            (function () {
              if (window.__AXOLYNC_ANDROID_TEXT_SELECTION_POLICY_INSTALLED__) {
                return "already-installed";
              }
              window.__AXOLYNC_ANDROID_TEXT_SELECTION_POLICY_INSTALLED__ = true;
              var allowSelector = [
                "input",
                "textarea",
                "select",
                "[contenteditable]",
                "[data-allow-native-text-selection]",
                "#debug-log-output",
                "#debug-log-output *",
                ".debug-log-line"
              ].join(",");
              function isAllowedSelectionTarget(target) {
                if (!target || typeof target.closest !== "function") return false;
                var match = target.closest(allowSelector);
                if (!match) return false;
                if (match.matches && match.matches("[contenteditable='false']")) return false;
                return true;
              }
              function isAllowedSelectionNode(node) {
                if (!node) return false;
                var element = node.nodeType === Node.TEXT_NODE ? node.parentElement : node;
                return isAllowedSelectionTarget(element);
              }
              function clearAccidentalSelection() {
                var selection = window.getSelection ? window.getSelection() : null;
                if (selection && selection.removeAllRanges) {
                  selection.removeAllRanges();
                }
              }
              function suppressIfOrdinaryText(event) {
                if (isAllowedSelectionTarget(event.target)) return;
                event.preventDefault();
                clearAccidentalSelection();
              }
              function installStyle() {
                if (document.getElementById("axolync-android-text-selection-policy")) return;
                var style = document.createElement("style");
                style.id = "axolync-android-text-selection-policy";
                style.textContent = [
                  "body * { -webkit-touch-callout: none !important; -webkit-user-select: none !important; user-select: none !important; }",
                  "input, textarea, select, [contenteditable]:not([contenteditable='false']), [data-allow-native-text-selection], [data-allow-native-text-selection] *, #debug-log-output, #debug-log-output *, .debug-log-line { -webkit-touch-callout: default !important; -webkit-user-select: text !important; user-select: text !important; }"
                ].join("\n");
                (document.head || document.documentElement).appendChild(style);
              }
              installStyle();
              document.addEventListener("selectstart", suppressIfOrdinaryText, true);
              document.addEventListener("contextmenu", suppressIfOrdinaryText, true);
              document.addEventListener("selectionchange", function () {
                var active = document.activeElement;
                if (isAllowedSelectionTarget(active)) return;
                var selection = window.getSelection ? window.getSelection() : null;
                if (selection && (
                  isAllowedSelectionNode(selection.anchorNode) ||
                  isAllowedSelectionNode(selection.focusNode)
                )) return;
                clearAccidentalSelection();
              }, true);
              return "installed";
            })();
        """.trimIndent()
    }
}
