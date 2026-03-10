package com.axolync.android.server

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalHttpServerNativeRuntimeLogTest {

    @Test
    fun `RuntimeNativeLogStore exposes a bounded JSON export surface in source`() {
        val source = File("src/main/kotlin/com/axolync/android/logging/RuntimeNativeLogStore.kt").readText()

        assertTrue(source.contains("object RuntimeNativeLogStore"))
        assertTrue(source.contains("private const val MAX_ENTRIES = 400"))
        assertTrue(source.contains("fun record("))
        assertTrue(source.contains("fun toJsonArray(): JSONArray"))
        assertTrue(source.contains("put(\"source\", entry.source)"))
        assertTrue(source.contains("put(\"details\", entry.details ?: JSONObject.NULL)"))
    }

    @Test
    fun `LocalHttpServer exposes native bridge logs and structured embedded lyricflow failures in source`() {
        val source = File("src/main/kotlin/com/axolync/android/server/LocalHttpServer.kt").readText()

        assertTrue(source.contains("private const val RUNTIME_NATIVE_LOG_PATH = \"/__axolync/runtime-native-log\""))
        assertTrue(source.contains("private fun serveRuntimeNativeLog"))
        assertTrue(source.contains("RuntimeNativeLogStore.toJsonArray()"))
        assertTrue(source.contains("Embedded LyricFlow bridge request started"))
        assertTrue(source.contains("Embedded LyricFlow bridge request succeeded"))
        assertTrue(source.contains("Embedded LyricFlow bridge request failed"))
        assertTrue(source.contains("Embedded LyricFlow bridge request timed out"))
        assertTrue(source.contains("\"BRIDGE_TIMEOUT\""))
        assertTrue(source.contains("\"BRIDGE_INVOCATION_FAILED\""))
        assertTrue(source.contains("invokeEmbeddedLyricflowBridgeWithTimeout"))
    }
}
