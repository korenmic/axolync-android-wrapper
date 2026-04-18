package com.axolync.android.bridge

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import fi.iki.elonen.NanoHTTPD
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

private const val CAPACITOR_HOST_FAMILY = "capacitor"
private const val CAPACITOR_HOST_PLATFORM = "android"
private const val ASSET_MANIFEST_PATH = "public/native-service-companions/manifest.json"
private const val UNSUPPORTED_BUNDLE_MESSAGE = "Native bridge is unavailable in this bundle for the current host."

private data class NativeBridgeOperatorGeo(
    val altitude: Double,
    val latitude: Double,
    val longitude: Double
)

private data class NativeBridgeOperatorDescriptor(
    val runtimeOperatorKind: String,
    val listenPath: String,
    val upstreamMethod: String,
    val upstreamUrlTemplate: String,
    val contentLanguage: String,
    val geo: NativeBridgeOperatorGeo,
    val userAgents: List<String>,
    val timezones: List<String>
)

private data class NativeBridgeRegistration(
    val addonId: String,
    val companionId: String,
    val displayName: String,
    val wrapper: String,
    val entrypoint: String,
    val operator: NativeBridgeOperatorDescriptor
)

private class NativeBridgeRuntimeOperator(private val registration: NativeBridgeRegistration) {
    private var loopbackServer: ShazamDiscoveryLoopbackServer? = null

    fun start() {
        if (loopbackServer != null) {
            return
        }
        val server = ShazamDiscoveryLoopbackServer(registration.operator)
        server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
        loopbackServer = server
    }

    fun stop() {
        loopbackServer?.stop()
        loopbackServer = null
    }

    fun getConnection(): JSObject? {
        val server = loopbackServer ?: return null
        return JSObject().apply {
            put("kind", "loopback-http-base-url")
            put("baseUrl", server.baseUrl())
        }
    }

    fun handleRequest(operation: String): JSObject {
        if (operation == "proxy.health") {
            val connection = getConnection()
            return JSObject().apply {
                put("ok", true)
                put("addonId", registration.addonId)
                put("companionId", registration.companionId)
                put("hostFamily", CAPACITOR_HOST_FAMILY)
                put("baseUrl", connection?.getString("baseUrl"))
            }
        }
        throw IllegalArgumentException("Unsupported native bridge operation \"$operation\".")
    }
}

private class ShazamDiscoveryLoopbackServer(
    private val descriptor: NativeBridgeOperatorDescriptor
) : NanoHTTPD("127.0.0.1", 0) {

    fun baseUrl(): String = "http://127.0.0.1:$listeningPort${descriptor.listenPath}"

    override fun serve(session: IHTTPSession): Response {
        if (session.uri != descriptor.listenPath) {
            return jsonResponse(
                Response.Status.NOT_FOUND,
                JSONObject().put("error", "Unknown runtime operator path.").toString()
            )
        }
        val uri = session.parameters["uri"]?.firstOrNull()
        val sampleMs = session.parameters["samplems"]?.firstOrNull()
        if (uri.isNullOrBlank() || sampleMs.isNullOrBlank()) {
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                JSONObject().put("error", "Missing required query parameters: uri and samplems").toString()
            )
        }
        return try {
            val responseBody = proxyShazamDiscoveryRequest(uri, sampleMs)
            jsonResponse(Response.Status.OK, responseBody)
        } catch (error: Throwable) {
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                JSONObject()
                    .put("error", "Failed to make request to Shazam API")
                    .put("details", error.message ?: error.toString())
                    .toString()
            )
        }
    }

    private fun proxyShazamDiscoveryRequest(uri: String, sampleMs: String): String {
        val apiUrl = buildUpstreamUrl(descriptor.upstreamUrlTemplate)
        val requestBody = JSONObject().apply {
            put(
                "geolocation",
                JSONObject().apply {
                    put("altitude", descriptor.geo.altitude)
                    put("latitude", descriptor.geo.latitude)
                    put("longitude", descriptor.geo.longitude)
                }
            )
            val nowMs = System.currentTimeMillis()
            put(
                "signature",
                JSONObject().apply {
                    put("uri", uri)
                    put("samplems", sampleMs.toInt())
                    put("timestamp", nowMs)
                }
            )
            put("timestamp", nowMs)
            put("timezone", pickOne(descriptor.timezones) ?: "UTC")
        }.toString()

        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = descriptor.upstreamMethod
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Content-Language", descriptor.contentLanguage)
            setRequestProperty("User-Agent", pickOne(descriptor.userAgents) ?: "Axolync Native Bridge")
        }
        connection.outputStream.use { stream ->
            stream.write(requestBody.toByteArray(StandardCharsets.UTF_8))
        }
        return (if ((connection.responseCode in 200..399)) connection.inputStream else connection.errorStream)
            ?.bufferedReader(StandardCharsets.UTF_8)
            ?.use { it.readText() }
            ?.ifBlank { "{}" }
            ?: "{}"
    }

    private fun buildUpstreamUrl(template: String): String {
        var result = template
        while (result.contains("{uuid}")) {
            result = result.replaceFirst("{uuid}", UUID.randomUUID().toString())
        }
        return result
    }

    private fun jsonResponse(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body)
    }

    private fun pickOne(values: List<String>): String? {
        if (values.isEmpty()) {
            return null
        }
        return values[Random.nextInt(values.size)]
    }
}

private fun normalizeHostAbi(rawAbi: String?): String? {
    return when (rawAbi?.trim()?.lowercase()) {
        "", null -> null
        "arm64-v8a", "aarch64", "arm64" -> "arm64"
        "x86_64", "amd64", "x64" -> "x64"
        "armeabi-v7a", "armv7", "armv7l" -> "armv7"
        "armeabi", "arm" -> "arm"
        "x86", "i386", "i686" -> "x86"
        else -> rawAbi.trim().lowercase()
    }
}

private fun detectHostAbi(): String? {
    val supportedAbis = Build.SUPPORTED_ABIS
    if (!supportedAbis.isNullOrEmpty()) {
        return normalizeHostAbi(supportedAbis.firstOrNull())
    }
    return normalizeHostAbi(System.getProperty("os.arch"))
}

@CapacitorPlugin(name = "AxolyncNativeServiceCompanionHost")
class AxolyncNativeServiceCompanionHostPlugin : Plugin() {

    private val enabledState = mutableMapOf<String, Boolean>()
    private val lastErrorState = mutableMapOf<String, String?>()
    private val runtimeOperators = mutableMapOf<String, NativeBridgeRuntimeOperator>()
    private val registrationByKey: MutableMap<String, NativeBridgeRegistration> by lazy { loadRegistrations().toMutableMap() }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun getHostInfo(call: PluginCall) {
        call.resolve(
            JSObject().apply {
                put("hostFamily", CAPACITOR_HOST_FAMILY)
                put("hostPlatform", CAPACITOR_HOST_PLATFORM)
                put("hostAbi", detectHostAbi())
            }
        )
    }

    @PluginMethod
    fun setEnabled(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val enabled = call.getBoolean("enabled", false) ?: false
        val key = companionKey(addonId, companionId)
        if (resolveRegistration(addonId, companionId) == null) {
            call.resolve(buildUnsupportedStatusEnvelope(addonId, companionId))
            return
        }
        enabledState[key] = enabled
        if (!enabled) {
            runtimeOperators.remove(key)?.stop()
            lastErrorState.remove(key)
        }
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun start(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val registration = resolveRegistration(addonId, companionId)
        if (registration == null) {
            call.resolve(buildUnsupportedStatusEnvelope(addonId, companionId))
            return
        }
        val key = companionKey(addonId, companionId)
        enabledState[key] = true
        try {
            val runtime = runtimeOperators.getOrPut(key) { NativeBridgeRuntimeOperator(registration) }
            runtime.start()
            lastErrorState.remove(key)
        } catch (error: Throwable) {
            lastErrorState[key] = error.message ?: error.toString()
        }
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val key = companionKey(addonId, companionId)
        runtimeOperators.remove(key)?.stop()
        lastErrorState.remove(key)
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun request(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val key = companionKey(addonId, companionId)
        val runtime = runtimeOperators[key]
        if (resolveRegistration(addonId, companionId) == null) {
            call.resolve(
                buildResponseEnvelope(
                    addonId,
                    companionId,
                    ok = false,
                    payload = null,
                    error = UNSUPPORTED_BUNDLE_MESSAGE
                )
            )
            return
        }
        if (runtime == null) {
            call.resolve(
                buildResponseEnvelope(
                    addonId,
                    companionId,
                    ok = false,
                    payload = null,
                    error = "Native bridge runtime operator is not running."
                )
            )
            return
        }
        try {
            val operation = call.getObject("request")?.getString("operation").orEmpty()
            val payload = runtime.handleRequest(operation)
            lastErrorState.remove(key)
            call.resolve(buildResponseEnvelope(addonId, companionId, ok = true, payload = payload, error = null))
        } catch (error: Throwable) {
            lastErrorState[key] = error.message ?: error.toString()
            call.resolve(
                buildResponseEnvelope(
                    addonId,
                    companionId,
                    ok = false,
                    payload = null,
                    error = lastErrorState[key]
                )
            )
        }
    }

    @PluginMethod
    fun getConnection(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "connection",
            runtimeOperators[companionKey(addonId, companionId)]?.getConnection() ?: JSONObject.NULL
        )
        call.resolve(envelope)
    }

    override fun handleOnDestroy() {
        runtimeOperators.values.forEach { it.stop() }
        runtimeOperators.clear()
        super.handleOnDestroy()
    }

    private fun companionKey(addonId: String, companionId: String): String = "${addonId.trim()}::${companionId.trim()}"

    private fun resolveRegistration(addonId: String, companionId: String): NativeBridgeRegistration? =
        registrationByKey[companionKey(addonId, companionId)]

    private fun buildStatusEnvelope(addonId: String, companionId: String, lastErrorOverride: String?): JSObject {
        val registration = resolveRegistration(addonId, companionId)
        if (registration == null) {
            return buildUnsupportedStatusEnvelope(addonId, companionId)
        }
        val key = companionKey(addonId, companionId)
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "status",
            JSObject().apply {
                put(
                    "state",
                    when {
                        runtimeOperators.containsKey(key) -> "running"
                        lastErrorOverride != null || lastErrorState[key] != null -> "error"
                        else -> "idle"
                    }
                )
                put("available", true)
                put("enabled", enabledState[key] == true)
                put("lastError", lastErrorOverride ?: lastErrorState[key] ?: JSONObject.NULL)
            }
        )
        return envelope
    }

    private fun buildUnsupportedStatusEnvelope(addonId: String, companionId: String): JSObject {
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "status",
            JSObject().apply {
                put("state", "unsupported")
                put("available", false)
                put("enabled", false)
                put("lastError", UNSUPPORTED_BUNDLE_MESSAGE)
            }
        )
        return envelope
    }

    private fun buildResponseEnvelope(
        addonId: String,
        companionId: String,
        ok: Boolean,
        payload: Any?,
        error: String?
    ): JSObject {
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "response",
            JSObject().apply {
                put("ok", ok)
                put("payload", payload ?: JSONObject.NULL)
                put("error", error ?: JSONObject.NULL)
            }
        )
        return envelope
    }

    private fun loadRegistrations(): Map<String, NativeBridgeRegistration> {
        val manifestText = readAssetText(ASSET_MANIFEST_PATH) ?: return emptyMap()
        val parsed = JSONObject(manifestText)
        val companions = parsed.optJSONArray("companions") ?: JSONArray()
        val resolved = mutableMapOf<String, NativeBridgeRegistration>()
        for (index in 0 until companions.length()) {
            val row = companions.optJSONObject(index) ?: continue
            val wrapper = row.optString("wrapper").trim()
            if (wrapper != CAPACITOR_HOST_FAMILY) {
                continue
            }
            val addonId = row.optString("addonId").trim()
            val companionId = row.optString("companionId").trim()
            val entrypoint = row.optString("entrypoint").trim()
            if (addonId.isEmpty() || companionId.isEmpty() || entrypoint.isEmpty()) {
                continue
            }
            val descriptorText = readAssetText("public/native-service-companions/$entrypoint") ?: continue
            val descriptor = parseOperatorDescriptor(JSONObject(descriptorText))
            resolved[companionKey(addonId, companionId)] = NativeBridgeRegistration(
                addonId = addonId,
                companionId = companionId,
                displayName = row.optString("displayName").trim(),
                wrapper = wrapper,
                entrypoint = entrypoint,
                operator = descriptor
            )
        }
        return resolved
    }

    private fun parseOperatorDescriptor(parsed: JSONObject): NativeBridgeOperatorDescriptor {
        val geo = parsed.optJSONObject("geo") ?: JSONObject()
        return NativeBridgeOperatorDescriptor(
            runtimeOperatorKind = parsed.optString("runtime_operator_kind").trim(),
            listenPath = parsed.optString("listen_path").trim(),
            upstreamMethod = parsed.optString("upstream_method").trim().ifEmpty { "POST" },
            upstreamUrlTemplate = parsed.optString("upstream_url_template").trim(),
            contentLanguage = parsed.optString("content_language").trim().ifEmpty { "en_US" },
            geo = NativeBridgeOperatorGeo(
                altitude = geo.optDouble("altitude", 0.0),
                latitude = geo.optDouble("latitude", 0.0),
                longitude = geo.optDouble("longitude", 0.0)
            ),
            userAgents = parsed.optJSONArray("user_agents").toStringList(),
            timezones = parsed.optJSONArray("timezones").toStringList()
        )
    }

    private fun readAssetText(assetPath: String): String? {
        return try {
            context.assets.open(assetPath).bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotEmpty()) {
                    add(value)
                }
            }
        }
    }
}
