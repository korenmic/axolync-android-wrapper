package com.axolync.android.activities

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartupSplashConfigTest {

    private fun repoFile(relativePath: String): File {
        val candidates = listOf(
            File(relativePath),
            File("..", relativePath)
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw java.io.FileNotFoundException("Could not resolve $relativePath from ${File(".").absolutePath}")
    }

    @Test
    fun `starting theme avoids the stock Android icon splash and hands off to the in app overlay`() {
        val themes = repoFile("app/src/main/res/values/themes.xml").readText()
        val themesV31 = repoFile("app/src/main/res/values-v31/themes.xml").readText()

        assertTrue(themes.contains("@drawable/splash_logo_placeholder"))
        assertTrue(themes.contains("<item name=\"windowSplashScreenAnimationDuration\">0</item>"))
        assertTrue(themesV31.contains("@drawable/splash_logo_placeholder"))
        assertTrue(themesV31.contains("<item name=\"windowSplashScreenAnimationDuration\">0</item>"))
    }
}
