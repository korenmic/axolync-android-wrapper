package com.axolync.android.server

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHttpServerEmbeddedLyricflowBridgeTest {

    @Test
    fun `LocalHttpServer maps lyricflow bridge routes onto embedded python operations`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("private fun handleEmbeddedLyricflowBridgeRequest"))
        assertTrue(source.contains("\"/v1/lyricflow/initialize\" -> \"initialize_session\""))
        assertTrue(source.contains("\"/v1/lyricflow/get-lyrics\" -> \"get_lyrics\""))
        assertTrue(source.contains("\"/v1/lyricflow/dispose\" -> \"dispose_session\""))
        assertTrue(source.contains("invokeEmbeddedLyricflowBridgeWithTimeout(operation, requestBody, session.headers)"))
        assertTrue(source.contains("\"BRIDGE_TIMEOUT\""))
        assertTrue(source.contains("lyricflowBridgeTimeoutMs"))
        assertTrue(source.contains("return lyricflowBridgeInvoker(operation, payloadJson, headerJson)"))
    }

    @Test
    fun `LocalHttpServer blocks lyricflow bridge requests when embedded python runtime is unhealthy`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("if (!runtimeStatus.startupSucceeded || runtimeStatus.health != \"ok\")"))
        assertTrue(source.contains("\"Embedded Python runtime unavailable for LyricFlow\""))
        assertTrue(source.contains("\"PROVIDER_UNAVAILABLE\""))
        assertTrue(source.contains("Embedded LyricFlow bridge request blocked by unhealthy Python runtime"))
    }
}
