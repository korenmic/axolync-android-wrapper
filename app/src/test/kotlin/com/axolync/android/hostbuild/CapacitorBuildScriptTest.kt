package com.axolync.android.hostbuild

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CapacitorBuildScriptTest {

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Could not resolve $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `app build uses capacitor host dependencies and staged public assets`() {
        val source = repoFile("app/build.gradle.kts").readText()
        assertTrue(source.contains("project(\":capacitor-android\")"))
        assertTrue(source.contains("assets.setSrcDirs(listOf(\"src/main/assets\"))"))
        assertTrue(source.contains("stageCapacitorBrowserAssets"))
        assertTrue(source.contains("create(\"normal\")"))
        assertTrue(source.contains("create(\"demo\")"))
        assertTrue(source.contains("create(\"axolyncTrackedDebug\")"))
        assertTrue(source.contains("signing/axolync-debug.keystore"))
        assertTrue(source.contains("signingConfig = signingConfigs.getByName(\"axolyncTrackedDebug\")"))
        assertFalse(source.contains(".android/debug.keystore"))
        assertFalse(source.contains("com.chaquo.python"))
        assertFalse(source.contains("nanohttpd"))
    }

    @Test
    fun `settings file includes capacitor android module from node_modules`() {
        val source = repoFile("settings.gradle.kts").readText()
        assertTrue(source.contains("include(\":capacitor-android\")"))
        assertTrue(source.contains("node_modules/@capacitor/android/capacitor"))
    }
}
