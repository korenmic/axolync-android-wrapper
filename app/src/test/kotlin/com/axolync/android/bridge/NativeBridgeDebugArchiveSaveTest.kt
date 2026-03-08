package com.axolync.android.bridge

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeBridgeDebugArchiveSaveTest {

    @Test
    fun `native bridge exposes debug archive save hook with structured result`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()

        assertTrue(source.contains("fun saveDebugArchiveBase64(fileName: String, base64Payload: String): String"))
        assertTrue(source.contains("MediaStore.Downloads"))
        assertTrue(source.contains("Base64.decode"))
        assertTrue(source.contains("put(\"success\", true)"))
        assertTrue(source.contains("put(\"uri\", savedUri.toString())"))
        assertTrue(source.contains("put(\"success\", false)"))
    }
}
