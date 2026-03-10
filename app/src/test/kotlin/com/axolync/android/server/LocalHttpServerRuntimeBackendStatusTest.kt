package com.axolync.android.server

import com.axolync.android.python.EmbeddedPythonRuntimeStatus
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
class LocalHttpServerRuntimeBackendStatusTest {

    @Test
    fun `runtime backend status endpoint exports embedded python readiness and logs health`() {
        ShadowLog.clear()
        val server = LocalHttpServer(
            RuntimeEnvironment.getApplication(),
            0,
            runtimeBackendStatusProvider = {
                EmbeddedPythonRuntimeStatus(
                    startupAttempted = true,
                    startupSucceeded = true,
                    reusedExistingRuntime = false,
                    criticalImportsSucceeded = true,
                    healthCheckSucceeded = true,
                    health = "ok"
                )
            }
        )

        server.start()
        try {
            val connection = URL("http://127.0.0.1:${server.listeningPort}/__axolync/runtime-backend-status")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONObject(body).getJSONObject("lyricflow")

            assertEquals(200, connection.responseCode)
            assertTrue(payload.getBoolean("pythonProcessSupported"))
            assertTrue(payload.getBoolean("launchAttempted"))
            assertTrue(payload.getBoolean("launchSucceeded"))
            assertTrue(payload.getBoolean("criticalImportsSucceeded"))
            assertTrue(payload.getBoolean("healthCheckSucceeded"))
            assertEquals("ok", payload.getString("health"))
            assertEquals("android-embedded-python", payload.getString("executionMode"))

            val healthyLog =
                ShadowLog.getLogsForTag("LocalHttpServer").any { it.msg.contains("runtime backend status healthy") }
            assertTrue(healthyLog)
        } finally {
            server.stop()
        }
    }
}
