package com.axolync.android.activities

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityWebViewZoomGuardTest {

    @Test
    fun `MainActivity keeps touch suppression enabled by default while exposing a runtime mode flag`() {
        val source = File("src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()
        val buildGradle = File("build.gradle.kts").readText()

        assertTrue(source.contains("setSupportZoom(false)"))
        assertTrue(source.contains("builtInZoomControls = false"))
        assertTrue(source.contains("displayZoomControls = false"))
        assertTrue(source.contains("textZoom = 100"))
        assertTrue(source.contains("webView.isLongClickable = false"))
        assertTrue(source.contains("webView.setOnLongClickListener"))
        assertTrue(source.contains("webView.setOnTouchListener"))
        assertTrue(source.contains("webView.overScrollMode = View.OVER_SCROLL_NEVER"))
        assertTrue(source.contains("BuildConfig.TOUCH_SUPPRESSION_MODE"))
        assertTrue(source.contains("document.body.dataset.touchSuppressionMode"))
        assertTrue(source.contains("viewport.setAttribute('content', 'width=device-width, initial-scale=1, viewport-fit=cover');"))
        assertTrue(source.contains("user-scalable=no"))
        assertTrue(source.contains("webkitTouchCallout"))
        assertTrue(buildGradle.contains("AXOLYNC_ANDROID_TOUCH_SUPPRESSION_MODE"))
        assertTrue(buildGradle.contains("TOUCH_SUPPRESSION_MODE"))
    }
}
