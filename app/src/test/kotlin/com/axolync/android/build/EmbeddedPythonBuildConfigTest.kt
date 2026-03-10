package com.axolync.android.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmbeddedPythonBuildConfigTest {

    private fun locate(vararg candidates: String): File {
        return candidates
            .asSequence()
            .map(::File)
            .firstOrNull(File::exists)
            ?: throw AssertionError("Could not locate any of: ${candidates.joinToString()}")
    }

    @Test
    fun `top-level build script declares Chaquopy plugin version 17`() {
        val source = locate("../build.gradle.kts", "build.gradle.kts").readText()
        assertTrue(source.contains("id(\"com.chaquo.python\") version \"17.0.0\" apply false"))
    }

    @Test
    fun `app build script applies Chaquopy and keeps minsdk 24`() {
        val source = locate("build.gradle.kts", "app/build.gradle.kts").readText()
        assertTrue(source.contains("id(\"com.chaquo.python\")"))
        assertTrue(source.contains("minSdk = 24"))
        assertTrue(source.contains("abiFilters += listOf(\"arm64-v8a\", \"x86_64\")"))
    }

    @Test
    fun `app build script wires generated python staging inputs and placeholder requirements`() {
        val source = locate("build.gradle.kts", "app/build.gradle.kts").readText()
        assertTrue(source.contains("generated/axolync-python"))
        assertTrue(source.contains("chaquopy {"))
        assertTrue(source.contains("srcDir(embeddedPythonSourceDir)"))
        assertTrue(source.contains("lyricflowAndroidRequirementsFile"))
        assertTrue(source.contains("requirements-android.txt"))
        assertTrue(source.contains("axolync-lyricflow-plugin/backend-python"))
        assertTrue(!source.contains("embeddedPythonRequirementsFile"))
        assertTrue(source.contains("requirements-android.txt"))
        assertTrue(source.contains("install(\"-r\", lyricflowAndroidRequirementsFile.get().absolutePath)"))
        assertTrue(source.contains("prepareEmbeddedPythonScaffold"))
        assertTrue(source.contains("dependsOn(prepareEmbeddedPythonScaffold)"))
    }
}
