package com.axolync.android.server

import com.axolync.android.python.EmbeddedPythonRuntimeStatus
import fi.iki.elonen.NanoHTTPD
import java.io.ByteArrayInputStream
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class LocalHttpServerEmbeddedLyricflowProofOfLifeTest {

    @Test
    fun `LocalHttpServer completes lyricflow initialize get-lyrics dispose through embedded bridge`() {
        val operations = mutableListOf<String>()
        val server = LocalHttpServer(
            RuntimeEnvironment.getApplication(),
            0,
            runtimeBackendStatusProvider = {
                EmbeddedPythonRuntimeStatus(
                    startupAttempted = true,
                    startupSucceeded = true,
                    criticalImportsSucceeded = true,
                    healthCheckSucceeded = true,
                    health = "ok"
                )
            },
            lyricflowBridgeInvoker = { operation, payloadJson, _ ->
                operations += operation
                when (operation) {
                    "initialize_session" -> JSONObject()
                        .put("ok", true)
                        .put("sessionId", JSONObject(payloadJson).getString("sessionId"))
                        .toString()

                    "get_lyrics" -> JSONObject()
                        .put("granularity", "line")
                        .put(
                            "units",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("text", "Do the evolution")
                                        .put("inSongMs", 1200)
                                        .put("durationMs", 900)
                                )
                        )
                        .toString()

                    "dispose_session" -> JSONObject()
                        .put("ok", true)
                        .put("sessionId", JSONObject(payloadJson).getString("sessionId"))
                        .toString()

                    else -> throw IllegalArgumentException("Unexpected operation: $operation")
                }
            }
        )

        val initialize = serveJson(
            server,
            "/__axolync/bridge/lyricflow/v1/lyricflow/initialize",
            JSONObject()
                .put("sessionId", "android-proof-session")
                .put("requestId", "android-proof-init")
        )
        assertEquals(200, initialize.first)
        assertTrue(JSONObject(initialize.second).getBoolean("ok"))

        val lyrics = serveJson(
            server,
            "/__axolync/bridge/lyricflow/v1/lyricflow/get-lyrics",
            JSONObject()
                .put("sessionId", "android-proof-session")
                .put("requestId", "android-proof-lyrics")
                .put("songId", "Pearl Jam - Do the Evolution")
                .put("granularity", "line")
                .put("chunkMeta", JSONObject())
                .put("settings", JSONObject().put("adapterIds", JSONArray().put("lrclib")))
        )
        assertEquals(200, lyrics.first)
        val lyricPayload = JSONObject(lyrics.second)
        assertEquals("line", lyricPayload.getString("granularity"))
        assertEquals("Do the evolution", lyricPayload.getJSONArray("units").getJSONObject(0).getString("text"))

        val dispose = serveJson(
            server,
            "/__axolync/bridge/lyricflow/v1/lyricflow/dispose",
            JSONObject()
                .put("sessionId", "android-proof-session")
                .put("requestId", "android-proof-dispose")
        )
        assertEquals(200, dispose.first)
        assertTrue(JSONObject(dispose.second).getBoolean("ok"))

        assertEquals(
            listOf("initialize_session", "get_lyrics", "dispose_session"),
            operations
        )
    }

    private fun serveJson(server: LocalHttpServer, path: String, payload: JSONObject): Pair<Int, String> {
        val response = server.serve(
            FakeSession(
                server = server,
                uri = path,
                method = NanoHTTPD.Method.POST,
                headers = linkedMapOf("content-type" to "application/json"),
                body = payload.toString()
            )
        )
        val code = response.status.requestStatus
        val body = response.data.bufferedReader().use { it.readText() }
        return code to body
    }

    private class FakeSession(
        server: LocalHttpServer,
        private val uri: String,
        private val method: NanoHTTPD.Method,
        private val headers: Map<String, String>,
        body: String,
    ) : NanoHTTPD.IHTTPSession {
        private val stream = ByteArrayInputStream(body.toByteArray())
        private val cookieHandler = server.CookieHandler(headers)

        override fun execute() = Unit

        override fun getCookies(): NanoHTTPD.CookieHandler = cookieHandler

        override fun getHeaders(): MutableMap<String, String> = headers.toMutableMap()

        override fun getInputStream() = stream

        override fun getMethod(): NanoHTTPD.Method = method

        override fun getParms(): MutableMap<String, String> = linkedMapOf()

        override fun getParameters(): MutableMap<String, MutableList<String>> = linkedMapOf()

        override fun getQueryParameterString(): String? = null

        override fun getUri(): String = uri

        override fun parseBody(files: MutableMap<String, String>) = Unit

        override fun getRemoteIpAddress(): String = "127.0.0.1"

        override fun getRemoteHostName(): String = "localhost"
    }
}
