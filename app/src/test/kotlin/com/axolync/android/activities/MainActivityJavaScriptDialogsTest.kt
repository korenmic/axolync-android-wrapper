package com.axolync.android.activities

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MainActivityJavaScriptDialogsTest {

    @Test
    fun `MainActivity wires WebChromeClient JavaScript dialog handling`() {
        val source = File("src/main/kotlin/com/axolync/android/activities/MainActivity.kt").readText()
        assertTrue(source.contains("webView.webChromeClient = object : WebChromeClient()"))
        assertTrue(source.contains("override fun onJsConfirm("))
        assertTrue(source.contains("override fun onJsAlert("))
        assertTrue(source.contains("AlertDialog.Builder(this@MainActivity)"))
    }
}
