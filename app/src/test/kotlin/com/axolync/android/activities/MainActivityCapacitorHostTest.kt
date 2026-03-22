package com.axolync.android.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityCapacitorHostTest {

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Could not resolve $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `main activity is a thin Capacitor bridge host`() {
        val source = repoFile("app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()
        assertTrue(source.contains("BridgeActivity"))
        assertTrue(source.contains("class MainActivity : BridgeActivity()"))
    }

    @Test
    fun `manifest launches main activity directly without legacy splash or notification listener wiring`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".activities.MainActivity"))
        assertFalse(manifest.contains("SplashActivity"))
        assertFalse(manifest.contains("StatusBarSongSignalService"))
        assertFalse(manifest.contains("AxolyncApplication"))
        assertFalse(manifest.contains("network_security_config"))
    }
}
