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
        assertTrue(source.contains("registerPlugin(AxolyncNativeServiceCompanionHostPlugin::class.java)"))
        assertTrue(source.contains("showStartupSplashOverlay"))
    }

    @Test
    fun `native service companion plugin keeps the Capacitor host surface generic and addon-agnostic`() {
        val source = repoFile("app/src/main/kotlin/com/axolync/android/bridge/AxolyncNativeServiceCompanionHostPlugin.kt").readText()
        assertTrue(source.contains("@CapacitorPlugin(name = \"AxolyncNativeServiceCompanionHost\")"))
        assertTrue(source.contains("fun getStatus"))
        assertTrue(source.contains("fun setEnabled"))
        assertTrue(source.contains("fun start"))
        assertTrue(source.contains("fun stop"))
        assertTrue(source.contains("fun request"))
        assertTrue(source.contains("fun getConnection"))
        assertTrue(source.contains("No native service companion is registered on this Capacitor host."))
        assertFalse(source.contains("vibra"))
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
    fun `manifest keeps parity with Capacitor web audio capture permission requests`() {
        val bridgeWebChromeClient = repoFile(
            "node_modules/@capacitor/android/capacitor/src/main/java/com/getcapacitor/BridgeWebChromeClient.java"
        ).readText()
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()

        assertTrue(bridgeWebChromeClient.contains("android.webkit.resource.AUDIO_CAPTURE"))
        assertTrue(bridgeWebChromeClient.contains("Manifest.permission.RECORD_AUDIO"))
        assertTrue(bridgeWebChromeClient.contains("Manifest.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
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
