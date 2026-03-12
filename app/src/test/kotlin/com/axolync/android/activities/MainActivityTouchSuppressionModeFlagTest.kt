package com.axolync.android.activities

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityTouchSuppressionModeFlagTest {

    @Test
    fun `MainActivity exposes a full and off suppression mode in the wrapped runtime bootstrap`() {
        val source = File("src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()

        assertTrue(source.contains("private fun androidTouchSuppressionMode(): String"))
        assertTrue(source.contains("\"off\" -> \"off\""))
        assertTrue(source.contains("document.body.dataset.touchSuppressionMode"))
        assertTrue(source.contains("document.body.style.touchAction = 'none';"))
        assertTrue(source.contains("document.body.style.touchAction = '';"))
        assertTrue(source.contains("maximum-scale=1, user-scalable=no"))
    }
}
