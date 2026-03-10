package com.axolync.android.python

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform
import com.axolync.android.logging.RuntimeNativeLogStore

class EmbeddedPythonManager internal constructor(
    private val appContext: Context,
    private val launcher: PythonRuntimeLauncher = ChaquopyPythonRuntimeLauncher,
    private val healthProbe: PythonHealthProbe = DefaultPythonHealthProbe,
    private val bridgeInvoker: PythonBridgeInvoker = ChaquopyPythonBridgeInvoker,
) {

    interface PythonRuntimeLauncher {
        fun isStarted(): Boolean
        fun start(context: Context)
        fun getInstance(): Any
    }

    interface PythonHealthProbe {
        fun run(pythonRuntime: Any): EmbeddedPythonRuntimeStatus
    }

    interface PythonBridgeInvoker {
        fun invokeLyricFlow(pythonRuntime: Any, operation: String, payloadJson: String = "{}", headersJson: String? = null): String
    }

    private object ChaquopyPythonRuntimeLauncher : PythonRuntimeLauncher {
        override fun isStarted(): Boolean = Python.isStarted()

        override fun start(context: Context) {
            Python.start(AndroidPlatform(context))
        }

        override fun getInstance(): Any = Python.getInstance()
    }

    private object DefaultPythonHealthProbe : PythonHealthProbe {
        override fun run(pythonRuntime: Any): EmbeddedPythonRuntimeStatus {
            val python = pythonRuntime as? Python
                ?: return EmbeddedPythonRuntimeStatus(
                    criticalImportsSucceeded = false,
                    healthCheckSucceeded = false,
                    health = "failed",
                    startupFailureStage = "health-check",
                    startupFailureMessage = "Python runtime instance unavailable"
                )

            return try {
                python.getModule("axolync_lyricflow_backend")
                python.getModule("axolync_android_bridge")
                val bridgeModule = python.getModule("axolync_android_bridge.lyricflow_bridge")
                bridgeModule.callAttr("entrypoint_placeholder")
                EmbeddedPythonRuntimeStatus(
                    criticalImportsSucceeded = true,
                    healthCheckSucceeded = true,
                    health = "ok"
                )
            } catch (error: Exception) {
                EmbeddedPythonRuntimeStatus(
                    criticalImportsSucceeded = false,
                    healthCheckSucceeded = false,
                    health = "failed",
                    startupFailureStage = "health-check",
                    startupFailureMessage = error.message ?: error.javaClass.simpleName
                )
            }
        }
    }

    private object ChaquopyPythonBridgeInvoker : PythonBridgeInvoker {
        override fun invokeLyricFlow(
            pythonRuntime: Any,
            operation: String,
            payloadJson: String,
            headersJson: String?
        ): String {
            val python = pythonRuntime as? Python
                ?: throw IllegalStateException("Embedded Python runtime missing Python instance")
            val bridgeModule = python.getModule("axolync_android_bridge.lyricflow_bridge")
            val bridgeResult: PyObject = if (headersJson == null) {
                bridgeModule.callAttr("invoke_json", operation, payloadJson)
            } else {
                bridgeModule.callAttr("invoke_json", operation, payloadJson, headersJson)
            }
            return bridgeResult.toString()
        }
    }

    @Volatile
    private var status = EmbeddedPythonRuntimeStatus()

    @Volatile
    private var pythonRuntime: Any? = null

    fun startIfNeeded(): EmbeddedPythonRuntimeStatus = synchronized(this) {
        if (status.startupSucceeded && pythonRuntime != null) {
            status = status.copy(reusedExistingRuntime = true)
            Log.i(TAG, "Embedded Python runtime already ready; reusing existing runtime")
            return status
        }

        if (status.startupAttempted && !status.startupSucceeded) {
            Log.w(
                TAG,
                "Embedded Python runtime previously failed at ${status.startupFailureStage}: ${status.startupFailureMessage}"
            )
            return status
        }

        val reusedExistingRuntime = launcher.isStarted()
        try {
            if (!reusedExistingRuntime) {
                Log.i(TAG, "Starting embedded Python runtime")
                RuntimeNativeLogStore.record(TAG, "info", "Embedded Python runtime start requested")
                launcher.start(appContext)
            } else {
                Log.i(TAG, "Embedded Python runtime already started by host process; acquiring instance")
                RuntimeNativeLogStore.record(TAG, "info", "Embedded Python runtime already started; reusing host runtime")
            }
        } catch (error: Exception) {
            status = EmbeddedPythonRuntimeStatus(
                startupAttempted = true,
                startupSucceeded = false,
                startupFailureStage = "start",
                startupFailureMessage = error.message ?: error.javaClass.simpleName,
                reusedExistingRuntime = reusedExistingRuntime,
                health = "failed"
            )
            Log.e(TAG, "Embedded Python runtime failed to start", error)
            RuntimeNativeLogStore.record(
                TAG,
                "error",
                "Embedded Python runtime failed to start",
                error.message ?: error.javaClass.simpleName
            )
            return status
        }

        return try {
            pythonRuntime = launcher.getInstance()
            status = EmbeddedPythonRuntimeStatus(
                startupAttempted = true,
                startupSucceeded = true,
                startupFailureStage = null,
                startupFailureMessage = null,
                reusedExistingRuntime = reusedExistingRuntime,
                health = "started"
            )
            Log.i(TAG, "Embedded Python runtime is ready")
            RuntimeNativeLogStore.record(TAG, "info", "Embedded Python runtime ready")
            status
        } catch (error: Exception) {
            status = EmbeddedPythonRuntimeStatus(
                startupAttempted = true,
                startupSucceeded = false,
                startupFailureStage = "get-instance",
                startupFailureMessage = error.message ?: error.javaClass.simpleName,
                reusedExistingRuntime = reusedExistingRuntime,
                health = "failed"
            )
            Log.e(TAG, "Embedded Python runtime failed after start during instance acquisition", error)
            RuntimeNativeLogStore.record(
                TAG,
                "error",
                "Embedded Python runtime failed during instance acquisition",
                error.message ?: error.javaClass.simpleName
            )
            status
        }
    }

    fun runSelfTest(): EmbeddedPythonRuntimeStatus = synchronized(this) {
        val startupStatus = startIfNeeded()
        if (!startupStatus.startupSucceeded || pythonRuntime == null) {
            return status
        }

        val probeStatus = healthProbe.run(pythonRuntime as Any)
        status = status.copy(
            startupFailureStage = probeStatus.startupFailureStage,
            startupFailureMessage = probeStatus.startupFailureMessage,
            criticalImportsSucceeded = probeStatus.criticalImportsSucceeded,
            healthCheckSucceeded = probeStatus.healthCheckSucceeded,
            health = probeStatus.health
        )

        if (status.health == "ok") {
            Log.i(TAG, "Embedded Python runtime health check passed")
            RuntimeNativeLogStore.record(TAG, "info", "Embedded Python runtime health check passed")
        } else {
            Log.e(
                TAG,
                "Embedded Python runtime health check failed at ${status.startupFailureStage}: ${status.startupFailureMessage}"
            )
            RuntimeNativeLogStore.record(
                TAG,
                "error",
                "Embedded Python runtime health check failed",
                "${status.startupFailureStage ?: "unknown"} | ${status.startupFailureMessage ?: "unknown"}"
            )
        }
        return status
    }

    fun getStatus(): EmbeddedPythonRuntimeStatus = status

    fun isReady(): Boolean = status.startupSucceeded && pythonRuntime != null

    fun getPython(): Python? = pythonRuntime as? Python

    fun runSmokeOperation(): String {
        val runtimeStatus = runSelfTest()
        if (!runtimeStatus.startupSucceeded || runtimeStatus.health != "ok") {
            throw IllegalStateException(
                "Embedded Python runtime unavailable: ${
                    runtimeStatus.startupFailureMessage ?: runtimeStatus.startupFailureStage ?: runtimeStatus.health
                }"
            )
        }
        val runtime = pythonRuntime
            ?: throw IllegalStateException("Embedded Python runtime missing Python instance")
        return bridgeInvoker.invokeLyricFlow(runtime, "smoke_ping")
    }

    fun invokeLyricFlowBridge(operation: String, payloadJson: String = "{}", headersJson: String? = null): String {
        val runtimeStatus = runSelfTest()
        if (!runtimeStatus.startupSucceeded || runtimeStatus.health != "ok") {
            throw IllegalStateException(
                "Embedded Python runtime unavailable: ${
                    runtimeStatus.startupFailureMessage ?: runtimeStatus.startupFailureStage ?: runtimeStatus.health
                }"
            )
        }

        val runtime = pythonRuntime
            ?: throw IllegalStateException("Embedded Python runtime missing Python instance")
        return bridgeInvoker.invokeLyricFlow(runtime, operation, payloadJson, headersJson)
    }

    companion object {
        private const val TAG = "EmbeddedPythonManager"

        @Volatile
        private var instance: EmbeddedPythonManager? = null

        fun getInstance(context: Context): EmbeddedPythonManager {
            return instance ?: synchronized(this) {
                instance ?: EmbeddedPythonManager(context.applicationContext).also { instance = it }
            }
        }
    }
}
