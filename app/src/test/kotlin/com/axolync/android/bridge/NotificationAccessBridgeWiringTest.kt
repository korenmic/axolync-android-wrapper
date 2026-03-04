package com.axolync.android.bridge

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class NotificationAccessBridgeWiringTest {

    @Test
    fun `manifest exposes notification listener service to system settings`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("StatusBarSongSignalService"))
        assertTrue(manifest.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"))
        assertTrue(manifest.contains("android:exported=\"true\""))
        assertTrue(manifest.contains("android.service.notification.NotificationListenerService"))
    }

    @Test
    fun `native bridge prefers app scoped notification listener settings intent`() {
        val source = File("src/main/kotlin/com/axolync/android/bridge/NativeBridge.kt").readText()
        assertTrue(source.contains("NOTIFICATION_LISTENER_DETAIL_SETTINGS"))
        assertTrue(source.contains("Settings.EXTRA_APP_PACKAGE"))
        assertTrue(source.contains("ACTION_NOTIFICATION_LISTENER_SETTINGS"))
    }
}
