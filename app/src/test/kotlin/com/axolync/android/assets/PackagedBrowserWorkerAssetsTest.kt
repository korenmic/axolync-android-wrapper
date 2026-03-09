package com.axolync.android.assets

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PackagedBrowserWorkerAssetsTest {

    @Test
    fun `checked-in browser asset tree contains packaged bridge worker javascript entrypoints`() {
        val browserAssetsRoot = listOf(
            File("app/src/main/assets/axolync-browser"),
            File("src/main/assets/axolync-browser")
        ).firstOrNull(File::exists)
            ?: throw AssertionError("Cannot locate checked-in axolync-browser asset tree")
        assertTrue(browserAssetsRoot.exists())

        val lyricflowWorker = File(browserAssetsRoot, "workers/lyricflowBridgeWorker.js")
        val syncengineWorker = File(browserAssetsRoot, "workers/syncengineBridgeWorker.js")
        val staleTsWorkers = browserAssetsRoot.resolve("assets").listFiles()?.filter {
            it.name.startsWith("lyricflowBridgeWorker-") && it.name.endsWith(".ts")
                || it.name.startsWith("syncengineBridgeWorker-") && it.name.endsWith(".ts")
        }.orEmpty()

        assertTrue(lyricflowWorker.exists())
        assertTrue(syncengineWorker.exists())
        assertFalse(lyricflowWorker.readText().contains("interface BridgeWorkerMessage"))
        assertFalse(syncengineWorker.readText().contains("interface BridgeWorkerMessage"))
        assertFalse(lyricflowWorker.readText().contains("export {};"))
        assertFalse(syncengineWorker.readText().contains("export {};"))
        assertFalse(lyricflowWorker.readText().contains("import { fetchDirectLrcLibLyrics }"))
        assertFalse(lyricflowWorker.readText().contains("Object.defineProperty(exports"))
        assertFalse(lyricflowWorker.readText().contains("require(\"./directLrcLibFallback.js\")"))
        assertTrue(lyricflowWorker.readText().contains("function parseSongIdentity(songId)"))
        assertTrue(staleTsWorkers.isEmpty())
    }
}
