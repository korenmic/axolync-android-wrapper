package com.axolync.android.properties

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PhysicalDeviceValidationAssetsTest {

    @Test
    fun `physical device validation script and doc are present`() {
        val rootCandidates = listOf(
            File("."),
            File(".."),
            File("../..")
        )
        val root = rootCandidates.firstOrNull {
            File(it, "scripts").exists() && File(it, "docs").exists()
        } ?: File(".")
        val script = File(root, "scripts/run-physical-device-validation.sh")
        val doc = File(root, "docs/physical-device-validation.md")
        assertTrue(script.exists())
        assertTrue(doc.exists())
    }
}
