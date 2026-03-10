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

        assertTrue(source.contains("handleEmbeddedLyricflowBridgeRequest"))
        assertTrue(source.contains("lyricflowBridgeInvoker"))
        assertTrue(source.contains("invokeEmbeddedLyricflowBridge"))
        assertTrue(source.contains("Embedded LyricFlow bridge invocation failed"))
        assertTrue(source.contains("\"PROVIDER_UNAVAILABLE\""))
        assertTrue(source.contains("Embedded Python runtime unavailable for LyricFlow"))
        assertTrue(source.contains("Embedded LyricFlow bridge request blocked by unhealthy Python runtime"))
        assertTrue(source.contains("if (bridgeKind == \"lyricflow\")"))
    }

    @Test
    fun `LocalHttpServer exposes explicit backend-launch capability status for wrapped Android runtime`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("\"runtime\": \"android-wrapper\""))
        assertTrue(source.contains("val lyricflowPythonProcessSupported = true"))
        assertTrue(source.contains("\"pythonProcessSupported\": \$lyricflowPythonProcessSupported"))
        assertTrue(source.contains("\"launchAttempted\": \${lyricflowRuntimeStatus.startupAttempted}"))
        assertTrue(source.contains("\"launchSucceeded\": \${lyricflowRuntimeStatus.startupSucceeded}"))
        assertTrue(source.contains("\"executionMode\": \"android-embedded-python\""))
        assertTrue(source.contains("runtimeBackendStatusProvider"))
    }
}
