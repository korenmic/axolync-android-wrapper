package com.axolync.android.python

data class EmbeddedPythonRuntimeStatus(
    val startupAttempted: Boolean = false,
    val startupSucceeded: Boolean = false,
    val startupFailureStage: String? = null,
    val startupFailureMessage: String? = null,
    val reusedExistingRuntime: Boolean = false,
    val criticalImportsSucceeded: Boolean? = null,
    val healthCheckSucceeded: Boolean? = null,
    val health: String = "not-started"
)
