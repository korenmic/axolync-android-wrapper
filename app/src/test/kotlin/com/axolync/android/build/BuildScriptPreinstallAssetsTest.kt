package com.axolync.android.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BuildScriptPreinstallAssetsTest {

    @Test
    fun `build script supports builder bundle env path and preinstalled plugin assets`() {
        val candidates = listOf(
            File("build.gradle.kts"),
            File("app/build.gradle.kts")
        )
        val sourceFile = candidates.firstOrNull { it.exists() }
            ?: throw AssertionError("Cannot locate app build.gradle.kts")
        val source = sourceFile.readText()
        assertTrue(source.contains("AXOLYNC_BUILDER_BROWSER_NORMAL"))
        assertTrue(source.contains("from(sourceRoot)"))
        assertTrue(source.contains("builderHasCompiledRoot"))
        assertTrue(source.contains("manifest.json"))
    }
}
