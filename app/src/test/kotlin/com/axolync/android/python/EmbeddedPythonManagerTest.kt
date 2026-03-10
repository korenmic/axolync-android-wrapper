package com.axolync.android.python

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class EmbeddedPythonManagerTest {

    private val appContext: Context
        get() = RuntimeEnvironment.getApplication().applicationContext

    @Test
    fun `first startup starts runtime and reports success`() {
        val launcher = FakePythonRuntimeLauncher(initiallyStarted = false)
        val manager = EmbeddedPythonManager(appContext, launcher, FakePythonHealthProbe())

        val status = manager.startIfNeeded()

        assertTrue(status.startupAttempted)
        assertTrue(status.startupSucceeded)
        assertFalse(status.reusedExistingRuntime)
        assertEquals(1, launcher.startCalls)
        assertTrue(manager.isReady())
    }

    @Test
    fun `repeated startup reuses runtime without restarting it`() {
        val launcher = FakePythonRuntimeLauncher(initiallyStarted = false)
        val manager = EmbeddedPythonManager(appContext, launcher, FakePythonHealthProbe())

        manager.startIfNeeded()
        val secondStatus = manager.startIfNeeded()

        assertTrue(secondStatus.startupAttempted)
        assertTrue(secondStatus.startupSucceeded)
        assertTrue(secondStatus.reusedExistingRuntime)
        assertEquals(1, launcher.startCalls)
        assertTrue(manager.isReady())
    }

    @Test
    fun `startup failure reports stage and message`() {
        val launcher = FakePythonRuntimeLauncher(
            initiallyStarted = false,
            startFailure = IllegalStateException("python boot failed")
        )
        val manager = EmbeddedPythonManager(appContext, launcher, FakePythonHealthProbe())

        val status = manager.startIfNeeded()

        assertTrue(status.startupAttempted)
        assertFalse(status.startupSucceeded)
        assertEquals("start", status.startupFailureStage)
        assertEquals("python boot failed", status.startupFailureMessage)
        assertEquals(1, launcher.startCalls)
        assertFalse(manager.isReady())
    }

    @Test
    fun `self test records healthy imports and health result`() {
        val launcher = FakePythonRuntimeLauncher(initiallyStarted = false)
        val manager = EmbeddedPythonManager(appContext, launcher, FakePythonHealthProbe())

        val status = manager.runSelfTest()

        assertTrue(status.startupSucceeded)
        assertEquals(true, status.criticalImportsSucceeded)
        assertEquals(true, status.healthCheckSucceeded)
        assertEquals("ok", status.health)
    }

    private class FakePythonRuntimeLauncher(
        initiallyStarted: Boolean,
        private val startFailure: Exception? = null,
        private val instanceFailure: Exception? = null
    ) : EmbeddedPythonManager.PythonRuntimeLauncher {
        private var started = initiallyStarted
        var startCalls: Int = 0
            private set

        override fun isStarted(): Boolean = started

        override fun start(context: Context) {
            startCalls += 1
            startFailure?.let { throw it }
            started = true
        }

        override fun getInstance(): Any {
            instanceFailure?.let { throw it }
            return Any()
        }
    }

    private class FakePythonHealthProbe(
        private val result: EmbeddedPythonRuntimeStatus = EmbeddedPythonRuntimeStatus(
            criticalImportsSucceeded = true,
            healthCheckSucceeded = true,
            health = "ok"
        )
    ) : EmbeddedPythonManager.PythonHealthProbe {
        override fun run(pythonRuntime: Any): EmbeddedPythonRuntimeStatus = result
    }
}
