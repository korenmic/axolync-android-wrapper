package com.axolync.android.server

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHttpServerBridgeProxyTest {

    @Test
    fun `LocalHttpServer exposes runtime bridge config and bridge proxy endpoints`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("private const val BRIDGE_CONFIG_PATH = \"/__axolync/runtime-bridge-config\""))
        assertTrue(source.contains("private const val RUNTIME_BACKEND_STATUS_PATH = \"/__axolync/runtime-backend-status\""))
        assertTrue(source.contains("private const val BRIDGE_PROXY_PREFIX = \"/__axolync/bridge/\""))
        assertTrue(source.contains("private fun serveRuntimeBridgeConfig"))
        assertTrue(source.contains("private fun serveRuntimeBackendStatus"))
        assertTrue(source.contains("private fun proxyBridgeRequest"))
        assertTrue(source.contains("resolveBridgeTargetUrl(session.uri)"))
    }

    @Test
    fun `LocalHttpServer allows POST for wrapped bridge routes instead of rejecting them as static-only`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("session.method == Method.POST"))
        assertTrue(source.contains("if (bridgeTarget != null && (session.method == Method.GET || session.method == Method.HEAD || session.method == Method.POST))"))
        assertTrue(source.contains("requestMethod = session.method.name"))
        assertTrue(source.contains("if (session.method == Method.POST)"))
    }

    @Test
    fun `LocalHttpServer uses localhost bridge backend URLs for cleartext-safe Android proxying`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("private const val BRIDGE_HOST = \"localhost\""))
        assertTrue(source.contains("\"host\": \"localhost\""))
        assertTrue(source.contains("\"songsense\": \"http://\$BRIDGE_HOST:\$SONGSENSE_BACKEND_PORT\""))
        assertTrue(source.contains("\"syncengine\": \"http://\$BRIDGE_HOST:\$SYNCENGINE_BACKEND_PORT\""))
        assertTrue(source.contains("\"lyricflow\": \"http://\$BRIDGE_HOST:\$LYRICFLOW_BACKEND_PORT\""))
        assertTrue(source.contains("return \"http://\$BRIDGE_HOST:\$port\$suffix\""))
    }

    @Test
    fun `LocalHttpServer can satisfy lyricflow bridge requests locally when the packaged backend is absent`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("handleLyricflowBridgeFallback"))
        assertTrue(source.contains("LRCLIB_GET_URL"))
        assertTrue(source.contains("LRCLIB_SEARCH_URL"))
        assertTrue(source.contains("fetchDirectLrcLibLyrics"))
        assertTrue(source.contains("\"android-local-lrclib\""))
        assertTrue(source.contains("LyricFlow backend unavailable, served local fallback"))
        assertTrue(source.contains("isLyricflowBackendProcessSupported(): Boolean = false"))
        assertTrue(source.contains("LyricFlow packaged backend process unsupported on Android, served local fallback"))
        assertTrue(source.contains("if (bridgeKind == \"lyricflow\" && !isLyricflowBackendProcessSupported())"))
    }

    @Test
    fun `LocalHttpServer exposes explicit backend-launch capability status for wrapped Android runtime`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("\"runtime\": \"android-wrapper\""))
        assertTrue(source.contains("val lyricflowPythonProcessSupported = false"))
        assertTrue(source.contains("\"pythonProcessSupported\": \$lyricflowPythonProcessSupported"))
        assertTrue(source.contains("\"launchAttempted\": false"))
        assertTrue(source.contains("\"launchSucceeded\": false"))
        assertTrue(source.contains("\"executionMode\": \"android-local-lrclib-fallback\""))
    }
}
