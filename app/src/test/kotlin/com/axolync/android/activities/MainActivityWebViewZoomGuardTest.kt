package com.axolync.android.activities

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityWebViewZoomGuardTest {

    @Test
    fun `MainActivity disables WebView zoom and pinches`() {
        val source = File("src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()
        assertTrue(source.contains("setSupportZoom(false)"))
        assertTrue(source.contains("builtInZoomControls = false"))
        assertTrue(source.contains("displayZoomControls = false"))
        assertTrue(source.contains("textZoom = 100"))
        assertTrue(source.contains("webView.setOnTouchListener"))
        assertTrue(source.contains("webView.overScrollMode = View.OVER_SCROLL_NEVER"))
        assertTrue(source.contains("viewport.setAttribute('content'"))
        assertTrue(source.contains("user-scalable=no"))
    }
}
