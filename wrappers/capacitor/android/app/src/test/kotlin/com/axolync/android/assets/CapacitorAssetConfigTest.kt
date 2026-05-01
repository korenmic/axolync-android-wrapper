package com.axolync.android.assets

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CapacitorAssetConfigTest {

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Could not resolve $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `capacitor package metadata is checked in for the active host`() {
        val packageJson = repoFile("package.json").readText()
        assertTrue(packageJson.contains("@capacitor/android"))
        assertTrue(packageJson.contains("@capacitor/core"))
        assertTrue(packageJson.contains("@capacitor/cli"))

        val config = repoFile("capacitor.config.json").readText()
        assertTrue(config.contains("\"appId\": \"com.axolync.android\""))
        assertTrue(config.contains("\"webDir\": \"app/src/main/assets/public\""))
    }

    @Test
    fun `asset staging script targets capacitor public assets and cordova stubs`() {
        val source = repoFile("scripts/stage-browser-assets.mjs").readText()
        assertTrue(source.contains("'app', 'src', 'main', 'assets', 'public'"))
        assertTrue(source.contains("cordova.js"))
        assertTrue(source.contains("cordova_plugins.js"))
        assertTrue(source.contains("AXOLYNC_BUILDER_BROWSER_NORMAL"))
    }

    @Test
    fun `gradle staging task always reruns before packaging capacitor assets`() {
        val buildScript = repoFile("app/build.gradle.kts").readText()
        assertTrue(buildScript.contains("stageCapacitorBrowserAssets"))
        assertTrue(buildScript.contains("outputs.upToDateWhen { false }"))
    }
}
