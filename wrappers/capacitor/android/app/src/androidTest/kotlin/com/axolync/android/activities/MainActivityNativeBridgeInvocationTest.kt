package com.axolync.android.activities

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.json.JSONTokener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@RunWith(AndroidJUnit4::class)
class MainActivityNativeBridgeInvocationTest {

    @Test
    fun webviewCanInvokePublishedNativeBridgeHostAndCollectStructuredDiagnostics() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            val readySnapshot = waitForReadySnapshot(scenario)
            assertTrue(readySnapshot.optBoolean("hasInjectedNativeBridgeHost", false))

            startDiagnosticsProbe(scenario)
            val result = waitForDiagnosticsProbeResult(scenario)

            assertTrue(result.optBoolean("invoked", false))
            val snapshot = result.getJSONObject("snapshot")
            assertTrue(snapshot.optBoolean("hasInjectedNativeBridgeHost", false))

            val diagnostics = result.optJSONObject("diagnostics")
            val statusResult = result.optJSONObject("statusResult")
            val enabledResult = result.optJSONObject("enabledResult")
            val startResult = result.optJSONObject("startResult")
            val connectionResult = result.optJSONObject("connectionResult")
            val statusAfterStart = result.optJSONObject("statusAfterStart")
            if (diagnostics != null) {
                val bridgePublication = diagnostics.optJSONObject("bridgePublication")
                requireNotNull(bridgePublication) { "Expected bridge publication metadata in diagnostics." }
                assertEquals("AxolyncNativeServiceCompanionHost", bridgePublication.optString("pluginName"))
                assertEquals("capacitor-plugin-registry", bridgePublication.optString("publicationMode"))
                assertTrue(diagnostics.optLong("generatedAtMs", 0L) > 0L)
                assertTrue(diagnostics.has("collectionMethod"))
                val logs = diagnostics.optJSONArray("logs")
                requireNotNull(logs) { "Expected diagnostics logs to describe the host bootstrap. Result: $result" }
                assertTrue(
                    (0 until logs.length()).any { index ->
                        logs.optJSONObject(index)?.optString("event") == "host.info.requested"
                    },
                )
                requireNotNull(statusResult) { "Expected getStatus() to return a structured status payload. Result: $result" }
                assertEquals("axolync-addon-vibra", statusResult.optString("addonId"))
                assertEquals("vibra_proxy", statusResult.optString("companionId"))
                assertTrue(statusResult.has("status"))
                requireNotNull(enabledResult) { "Expected setEnabled() to return a structured status payload. Result: $result" }
                assertTrue(enabledResult.has("status"))
                requireNotNull(startResult) { "Expected start() to return a structured status payload. Result: $result" }
                assertTrue(startResult.has("status"))
                requireNotNull(connectionResult) { "Expected getConnection() to return a structured payload. Result: $result" }
                assertTrue(connectionResult.has("connection"))
                requireNotNull(statusAfterStart) { "Expected post-start getStatus() to return a structured payload. Result: $result" }
                assertTrue(statusAfterStart.has("status"))
            } else {
                val error = result.optJSONObject("error")
                requireNotNull(error) { "Expected either diagnostics or a structured invocation error." }
                assertTrue(error.optString("message").isNotBlank())
            }
        }
    }

    private fun waitForReadySnapshot(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 30_000L
    ): JSONObject {
        var lastSnapshot = JSONObject()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val snapshot = evaluateSnapshot(scenario)
            lastSnapshot = snapshot
            if (
                snapshot.optString("readyState") == "complete" &&
                snapshot.optBoolean("hasInjectedNativeBridgeHost", false)
            ) {
                return snapshot
            }
            Thread.sleep(250L)
        }
        throw AssertionError("Timed out waiting for the Android WebView runtime host to publish. Last snapshot: $lastSnapshot")
    }

    private fun startDiagnosticsProbe(scenario: ActivityScenario<MainActivity>) {
        evaluateScript(
            scenario,
            """
            (() => {
              const snapshot = {
                readyState: document.readyState,
                hasWindowCapacitor: Boolean(window.Capacitor),
                hasCapacitorPluginsContainer: Boolean(window.Capacitor && window.Capacitor.Plugins),
                hasPublishedNativeBridgePlugin: Boolean(window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.AxolyncNativeServiceCompanionHost),
                hasCapacitorNativePromise: Boolean(window.Capacitor && typeof window.Capacitor.nativePromise === 'function'),
                hasInjectedNativeBridgeHost: Boolean(window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__),
              };
              window.__AXOLYNC_NATIVE_BRIDGE_TEST_RESULT__ = null;
              const finish = (payload) => {
                if (!window.__AXOLYNC_NATIVE_BRIDGE_TEST_RESULT__ || !window.__AXOLYNC_NATIVE_BRIDGE_TEST_RESULT__.ready) {
                  window.__AXOLYNC_NATIVE_BRIDGE_TEST_RESULT__ = payload;
                }
              };
              const timeoutId = setTimeout(() => {
                finish({
                  ready: true,
                  invoked: true,
                  snapshot,
                  timeout: true,
                  error: {
                    message: 'Timed out waiting for native bridge host diagnostics.',
                    code: 'axolync_native_bridge_timeout',
                    details: {
                      snapshot,
                    },
                  },
                });
              }, 5000);
              Promise.resolve()
                .then(() => window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.getDiagnostics())
                .then(async (diagnostics) => {
                  const publishedPlugin = window.Capacitor && window.Capacitor.Plugins
                    ? window.Capacitor.Plugins.AxolyncNativeServiceCompanionHost
                    : null;
                  const hostInfo = publishedPlugin && typeof publishedPlugin.getHostInfo === 'function'
                    ? await publishedPlugin.getHostInfo({})
                    : null;
                  const statusResult = await window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.getStatus('axolync-addon-vibra', 'vibra_proxy');
                  const enabledResult = await window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.setEnabled('axolync-addon-vibra', 'vibra_proxy', true);
                  const startResult = await window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.start('axolync-addon-vibra', 'vibra_proxy');
                  const connectionResult = await window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.getConnection('axolync-addon-vibra', 'vibra_proxy');
                  const statusAfterStart = await window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__.getStatus('axolync-addon-vibra', 'vibra_proxy');
                  clearTimeout(timeoutId);
                  finish({
                    ready: true,
                    invoked: true,
                    snapshot,
                    diagnostics,
                    hostInfo,
                    statusResult,
                    enabledResult,
                    startResult,
                    connectionResult,
                    statusAfterStart,
                  });
                })
                .catch((error) => {
                  clearTimeout(timeoutId);
                  finish({
                    ready: true,
                    invoked: true,
                    snapshot,
                    error: {
                      message: error && error.message ? error.message : String(error),
                      code: error && error.code ? error.code : null,
                      details: error && error.details ? error.details : null,
                    },
                  });
                });
              return "started";
            })()
            """.trimIndent()
        )
    }

    private fun waitForDiagnosticsProbeResult(
        scenario: ActivityScenario<MainActivity>,
        timeoutMs: Long = 30_000L
    ): JSONObject {
        var lastResult = JSONObject()
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        while (System.nanoTime() < deadline) {
            val probe = evaluateScript(
                scenario,
                """
                (() => JSON.stringify(
                  window.__AXOLYNC_NATIVE_BRIDGE_TEST_RESULT__ || {
                    ready: false,
                    invoked: false,
                  }
                ))()
                """.trimIndent()
            )
            lastResult = JSONObject(probe)
            if (lastResult.optBoolean("ready", false)) {
                return lastResult
            }
            Thread.sleep(250L)
        }
        throw AssertionError("Timed out waiting for native bridge diagnostics probe result. Last result: $lastResult")
    }

    private fun evaluateSnapshot(scenario: ActivityScenario<MainActivity>): JSONObject =
        JSONObject(
            evaluateScript(
                scenario,
                """
                (() => JSON.stringify({
                  readyState: document.readyState,
                  hasWindowCapacitor: Boolean(window.Capacitor),
                  hasCapacitorPluginsContainer: Boolean(window.Capacitor && window.Capacitor.Plugins),
                  hasPublishedNativeBridgePlugin: Boolean(window.Capacitor && window.Capacitor.Plugins && window.Capacitor.Plugins.AxolyncNativeServiceCompanionHost),
                  hasCapacitorNativePromise: Boolean(window.Capacitor && typeof window.Capacitor.nativePromise === 'function'),
                  hasInjectedNativeBridgeHost: Boolean(window.__AXOLYNC_NATIVE_SERVICE_COMPANION_HOST__),
                }))()
                """.trimIndent()
            )
        )

    private fun evaluateScript(
        scenario: ActivityScenario<MainActivity>,
        script: String,
        timeoutSeconds: Long = 15L
    ): String {
        val resultRef = AtomicReference<String?>()
        val latch = CountDownLatch(1)
        scenario.onActivity { activity ->
            val bridge = requireNotNull(activity.bridge) { "BridgeActivity did not initialize its Capacitor bridge." }
            val webView = requireNotNull(bridge.webView) { "Capacitor bridge does not expose a WebView." }
            activity.runOnUiThread {
                webView.evaluateJavascript(script) { rawResult ->
                    resultRef.set(rawResult)
                    latch.countDown()
                }
            }
        }
        assertTrue("Timed out waiting for WebView JavaScript evaluation.", latch.await(timeoutSeconds, TimeUnit.SECONDS))
        val rawResult = resultRef.get() ?: throw AssertionError("WebView JavaScript evaluation returned null.")
        return decodeJavascriptResult(rawResult)
    }

    private fun decodeJavascriptResult(rawResult: String): String {
        val decoded = JSONTokener(rawResult).nextValue()
        return when (decoded) {
            null -> "null"
            is String -> decoded
            else -> decoded.toString()
        }
    }
}
