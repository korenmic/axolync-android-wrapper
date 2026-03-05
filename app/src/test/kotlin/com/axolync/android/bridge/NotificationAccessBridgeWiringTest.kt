package com.axolync.android.bridge

import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class NotificationAccessBridgeWiringTest {

    @Test
    fun `manifest exposes notification listener service to system settings`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("StatusBarSongSignalService"))
        assertTrue(manifest.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        // Service must be exported so Samsung and other OEM Settings apps can enumerate it
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertFalse("Service must NOT be exported=false", manifest.contains("android:exported=\"false\""))
        assertTrue(manifest.contains("android.service.notification.NotificationListenerService"))
    }

    @Test
    fun `native bridge uses component-scoped detail settings intent with fallback`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS"))
        assertTrue(source.contains("EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME"))
        assertTrue(source.contains("StatusBarSongSignalService"))
        assertTrue(source.contains("ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }

    @Test
    fun `native bridge exposes openAppInfoSettings for restricted settings bypass`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("openAppInfoSettings"))
        assertTrue(source.contains("ACTION_APPLICATION_DETAILS_SETTINGS"))
    }

    @Test
    fun `native bridge getStatusBarAccessStatus returns structured state with required fields`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("\"state\""))
        assertTrue(source.contains("\"reasonCode\""))
        assertTrue(source.contains("\"message\""))
        assertTrue(source.contains("\"granted\""))
        assertTrue(source.contains("\"not_granted\""))
        assertTrue(source.contains("\"restricted\""))
        assertTrue(source.contains("\"error\""))
    }

    @Test
    fun `native bridge detects restricted settings state on Android 13 plus`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("isRestrictedFromSensitiveSettings"))
        assertTrue(source.contains("TIRAMISU"))
        assertTrue(source.contains("getInstallSourceInfo"))
        assertTrue(source.contains("com.android.vending"))
        assertTrue(source.contains("com.sec.android.app.samsungapps"))
    }

    @Test
    fun `native bridge exposes status-bar debug capture methods for debug UI`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("setStatusBarDebugCaptureEnabled"))
        assertTrue(source.contains("getStatusBarDebugCaptureLog"))
        assertTrue(source.contains("clearStatusBarDebugCaptureLog"))
        assertTrue(source.contains("isDebugBuild"))
    }
}
