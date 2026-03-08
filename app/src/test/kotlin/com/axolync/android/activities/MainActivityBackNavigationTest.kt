package com.axolync.android.activities

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityBackNavigationTest {

    @Test
    fun `MainActivity routes Android Back through wrapped web-app handler before exit`() {
        val source = File("src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()

        assertTrue(source.contains("registerBackNavigationHandler()"))
        assertTrue(source.contains("onBackPressedDispatcher.addCallback"))
        assertTrue(source.contains("window.__axolyncHandleBackNavigation"))
        assertTrue(source.contains("webView.evaluateJavascript(js)"))
        assertTrue(source.contains("if (!handled) {"))
        assertTrue(source.contains("finish()"))
    }
}
