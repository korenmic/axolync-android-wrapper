package com.axolync.android.build

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EmbeddedPythonSourceStagingTest {

    @Test
    fun `build script stages embedded python sources from lyricflow backend repo`() {
        val source = File("build.gradle.kts").readText()
        assertTrue(source.contains("AXOLYNC_LYRICFLOW_BACKEND_ROOT"))
        assertTrue(source.contains("axolync-lyricflow-plugin/backend-python"))
        assertTrue(source.contains("src/axolync_lyricflow_backend"))
        assertTrue(source.contains("src/axolync_android_bridge"))
        assertTrue(source.contains("Stage Android-usable Python sources from the LyricFlow backend repo"))
    }

    @Test
    fun `focused build creates staged python backend and android bridge entry files`() {
        val stagedRoot = File("build/generated/axolync-python/src/main/python")
        val backendInit = File(stagedRoot, "axolync_lyricflow_backend/__init__.py")
        val bridgeInit = File(stagedRoot, "axolync_android_bridge/__init__.py")
        val bridgeEntry = File(stagedRoot, "axolync_android_bridge/lyricflow_bridge.py")

        assertTrue(stagedRoot.exists())
        assertTrue(backendInit.exists())
        assertTrue(bridgeInit.exists())
        assertTrue(bridgeEntry.exists())
        assertTrue(bridgeEntry.readText().contains("entrypoint_placeholder"))
    }
}
