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
        assertTrue(source.contains("class MainActivity : BridgeActivity"))
        assertTrue(source.contains("showStartupSplashOverlay"))
    }

    @Test
    fun `manifest launches main activity directly without legacy splash or notification listener wiring`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".activities.MainActivity"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertFalse(manifest.contains("SplashActivity"))
        assertFalse(manifest.contains("StatusBarSongSignalService"))
        assertFalse(manifest.contains("AxolyncApplication"))
        assertFalse(manifest.contains("network_security_config"))
    }

    @Test
    fun `startup splash layout keeps a shared matte between backdrop and centered artwork`() {
        val layout = repoFile("app/src/main/res/layout/activity_splash.xml").readText()
        assertTrue(layout.contains("android:id=\"@+id/splash_foreground_group\""))
        assertTrue(layout.contains("android:id=\"@+id/splash_image_foreground\""))
        assertTrue(layout.contains("android:alpha=\"0.88\""))
        assertTrue(layout.contains("android:background=\"#000000\""))
        assertTrue(layout.contains("android:scaleType=\"fitCenter\""))
    }
}
