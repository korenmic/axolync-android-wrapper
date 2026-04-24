package com.axolync.android.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
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
        assertTrue(source.contains("registerPlugin(AxolyncDebugArchiveSavePlugin::class.java)"))
        assertTrue(source.contains("registerPlugin(AxolyncNativeServiceCompanionHostPlugin::class.java)"))
        assertTrue(source.contains("showStartupSplashOverlay"))
    }

    @Test
    fun `main activity suppresses ordinary WebView text selection while preserving editable and debug copy surfaces`() {
        val source = repoFile("app/src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()
        assertTrue(source.contains("installAndroidTextSelectionPolicy"))
        assertTrue(source.contains("evaluateJavascript(ANDROID_TEXT_SELECTION_POLICY_SCRIPT"))
        assertTrue(source.contains("__AXOLYNC_ANDROID_TEXT_SELECTION_POLICY_INSTALLED__"))
        assertTrue(source.contains("-webkit-user-select: none"))
        assertTrue(source.contains("document.addEventListener(\"selectstart\", suppressIfOrdinaryText, true)"))
        assertTrue(source.contains("document.addEventListener(\"contextmenu\", suppressIfOrdinaryText, true)"))
        assertTrue(source.contains("isAllowedSelectionNode(selection.anchorNode)"))
        assertTrue(source.contains("input"))
        assertTrue(source.contains("textarea"))
        assertTrue(source.contains("[contenteditable]"))
        assertTrue(source.contains("[data-allow-native-text-selection]"))
        assertTrue(source.contains("#debug-log-output"))
        assertFalse(source.contains("isLongClickable = false"))
    }

    @Test
    fun `dedicated Capacitor debug archive plugin owns the Android-visible save capability`() {
        val source = repoFile("app/src/main/kotlin/com/axolync/android/bridge/AxolyncDebugArchiveSavePlugin.kt").readText()
        assertTrue(source.contains("@CapacitorPlugin(name = \"AxolyncDebugArchiveSave\")"))
        assertTrue(source.contains("fun saveDebugArchiveBase64"))
        assertTrue(source.contains("persistDebugArchive"))
    }

    @Test
    fun `native service companion plugin keeps the Capacitor host surface generic and addon-agnostic`() {
        val source = repoFile("app/src/main/kotlin/com/axolync/android/bridge/AxolyncNativeServiceCompanionHostPlugin.kt").readText()
        assertTrue(source.contains("@CapacitorPlugin(name = \"AxolyncNativeServiceCompanionHost\")"))
        assertTrue(source.contains("fun getStatus"))
        assertTrue(source.contains("fun setEnabled"))
        assertTrue(source.contains("fun start"))
        assertTrue(source.contains("fun stop"))
        assertTrue(source.contains("fun getHostInfo"))
        assertTrue(source.contains("fun request"))
        assertTrue(source.contains("fun getConnection"))
        assertTrue(source.contains("fun getDiagnostics"))
        assertTrue(source.contains("fun saveDebugArchiveBase64"))
        assertTrue(source.contains("publicationMode"))
        assertTrue(source.contains("capacitor-plugin-registry"))
        assertTrue(source.contains("pluginRegistryAssetPath"))
        assertTrue(source.contains("capacitor.plugins.json"))
        assertTrue(source.contains("Build.SUPPORTED_ABIS"))
        assertTrue(source.contains("Native bridge is unavailable in this bundle for the current host."))
        assertTrue(source.contains("public/native-service-companions/manifest.json"))
        assertTrue(source.contains("fun baseUrl(): String = \"http://127.0.0.1:\$listeningPort\${descriptor.listenPath}\""))
        assertTrue(source.contains("loopback-route-miss"))
        assertTrue(source.contains("upstream-html-response"))
        assertTrue(source.contains("upstream-json-parse-failure"))
        assertTrue(source.contains("runtime-operator-crash"))
        assertFalse(source.contains("vibra"))
        assertFalse(source.contains("No native service companion is registered on this Capacitor host."))
    }

    @Test
    fun `staged browser assets load the packaged Capacitor native bridge runtime before fallback failure`() {
        val source = repoFile("scripts/stage-browser-assets.mjs").readText()
        assertTrue(source.contains("BRIDGE_RUNTIME_SCRIPT_SRC"))
        assertTrue(source.contains("./native-bridge.js"))
        assertTrue(source.contains("ensureBridgeRuntimeLoaded"))
        assertTrue(source.contains("data-axolync-capacitor-native-bridge-runtime"))
        assertTrue(source.contains("Failed to load packaged Capacitor native bridge runtime."))
        assertTrue(source.contains("fs.copyFileSync("))
        assertTrue(source.contains("native-bridge.js"))
    }

    @Test
    fun `capacitor plugin registry publishes the native bridge host class at the asset root`() {
        val rootRegistry = repoFile("app/src/main/assets/capacitor.plugins.json").readText()
        val nestedRegistry = repoFile("app/src/main/assets/capacitor/capacitor.plugins.json").readText()

        assertTrue(rootRegistry.contains("com.axolync.android.bridge.AxolyncDebugArchiveSavePlugin"))
        assertTrue(rootRegistry.contains("axolync-debug-archive-save"))
        assertTrue(rootRegistry.contains("com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin"))
        assertTrue(rootRegistry.contains("axolync-native-bridge-host"))
        assertEquals(rootRegistry.trim(), nestedRegistry.trim())
    }

    @Test
    fun `manifest launches main activity directly without legacy splash or notification listener wiring`() {
        val manifest = repoFile("app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".activities.MainActivity"))
        assertTrue(manifest.contains("android.permission.RECORD_AUDIO"))
        assertTrue(manifest.contains("android.permission.MODIFY_AUDIO_SETTINGS"))
        assertTrue(manifest.contains("android:networkSecurityConfig=\"@xml/network_security_config\""))
        assertFalse(manifest.contains("SplashActivity"))
        assertFalse(manifest.contains("StatusBarSongSignalService"))
        assertFalse(manifest.contains("AxolyncApplication"))
    }

    @Test
    fun `network security config allows localhost cleartext without broad cleartext enablement`() {
        val xml = repoFile("app/src/main/res/xml/network_security_config.xml").readText()
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">localhost</domain>"))
        assertTrue(xml.contains("<domain includeSubdomains=\"false\">127.0.0.1</domain>"))
        assertTrue(xml.contains("<base-config cleartextTrafficPermitted=\"false\" />"))
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
