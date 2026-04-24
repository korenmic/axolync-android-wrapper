package com.axolync.android.bridge

import com.axolync.android.logging.RuntimeNativeLogStore
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import android.util.Log
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import fi.iki.elonen.NanoHTTPD
import fi.iki.elonen.NanoHTTPD.Response
import android.os.Build
import org.brotli.dec.BrotliInputStream
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import kotlin.random.Random

private const val CAPACITOR_HOST_FAMILY = "capacitor"
private const val CAPACITOR_HOST_PLATFORM = "android"
private const val ASSET_MANIFEST_PATH = "public/native-service-companions/manifest.json"
private const val UNSUPPORTED_BUNDLE_MESSAGE = "Native bridge is unavailable in this bundle for the current host."
private const val MAX_NATIVE_BRIDGE_DIAGNOSTICS = 200
private const val OPERATOR_KIND_SHAZAM_DISCOVERY = "shazam-discovery-loopback-v1"
private const val OPERATOR_KIND_LRCLIB_LOCAL = "lrclib-local-loopback-v1"
private val LOOPBACK_CORS_HEADERS = mapOf(
    "Access-Control-Allow-Origin" to "*",
    "Access-Control-Allow-Methods" to "GET, OPTIONS",
    "Access-Control-Allow-Headers" to "Accept, Content-Type, x-axolync-lrclib-local-result",
    "Access-Control-Expose-Headers" to "x-axolync-lrclib-local-result"
)

private typealias NativeBridgeDiagnosticLogger = (
    source: String,
    level: String,
    addonId: String?,
    companionId: String?,
    event: String,
    details: Map<String, Any?>?
) -> Unit

private data class NativeBridgeOperatorGeo(
    val altitude: Double,
    val latitude: Double,
    val longitude: Double
)

private data class NativeBridgeOperatorDbConfig(
    val compressedAssetPath: String,
    val packagedAssetPath: String,
    val packagedProvenancePath: String,
    val deployedFileName: String,
    val deployPolicy: String,
    val sqliteHeaderRequired: Boolean
)

private data class NativeBridgeOperatorDescriptor(
    val runtimeOperatorKind: String,
    val listenPath: String,
    val upstreamMethod: String,
    val upstreamUrlTemplate: String,
    val contentLanguage: String,
    val geo: NativeBridgeOperatorGeo,
    val userAgents: List<String>,
    val timezones: List<String>,
    val db: NativeBridgeOperatorDbConfig?,
    val localResultHeader: String
)

private data class NativeBridgeRegistration(
    val addonId: String,
    val companionId: String,
    val displayName: String,
    val wrapper: String,
    val entrypoint: String,
    val operator: NativeBridgeOperatorDescriptor
)

private data class NativeBridgeDiagnosticEntry(
    val atMs: Long,
    val source: String,
    val level: String,
    val addonId: String?,
    val companionId: String?,
    val event: String,
    val details: Map<String, Any?>?
)

private data class LrclibDbDeploymentResult(
    val dbFile: File,
    val metadataFile: File,
    val compressedAssetPath: String,
    val compressedAssetSha256: String,
    val reused: Boolean,
    val replaced: Boolean
)

private data class LrclibQueryResponse(
    val status: Response.Status,
    val body: String,
    val classification: String
)

private interface NativeBridgeLoopbackServer {
    fun baseUrl(): String
    fun startServer()
    fun stopServer()
}

private class NativeBridgeRuntimeOperator(
    private val context: Context,
    private val registration: NativeBridgeRegistration,
    private val logger: NativeBridgeDiagnosticLogger
) {
    private var loopbackServer: NativeBridgeLoopbackServer? = null

    fun start() {
        if (loopbackServer != null) {
            logger(
                "runtime-operator",
                "info",
                registration.addonId,
                registration.companionId,
                "runtime-operator.start.skipped-already-running",
                null
            )
            return
        }
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.start.requested",
            mapOf("entrypoint" to registration.entrypoint)
        )
        val server = createLoopbackServer()
        server.startServer()
        loopbackServer = server
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.started",
            mapOf("baseUrl" to server.baseUrl())
        )
    }

    private fun createLoopbackServer(): NativeBridgeLoopbackServer {
        val operatorKind = registration.operator.runtimeOperatorKind
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.dispatch.selected",
            mapOf("runtimeOperatorKind" to operatorKind)
        )
        return when (operatorKind) {
            OPERATOR_KIND_SHAZAM_DISCOVERY -> ShazamDiscoveryLoopbackServer(registration, registration.operator, logger)
            OPERATOR_KIND_LRCLIB_LOCAL -> LrclibLocalLoopbackServer(context, registration, registration.operator, logger)
            else -> {
                logger(
                    "runtime-operator",
                    "error",
                    registration.addonId,
                    registration.companionId,
                    "runtime-operator.dispatch.unsupported",
                    mapOf("runtimeOperatorKind" to operatorKind)
                )
                throw IllegalArgumentException("Unsupported runtime operator kind \"$operatorKind\".")
            }
        }
    }

    fun stop() {
        val baseUrl = loopbackServer?.baseUrl()
        loopbackServer?.stopServer()
        loopbackServer = null
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.stopped",
            mapOf("baseUrl" to baseUrl)
        )
    }

    fun getConnection(): JSObject? {
        val server = loopbackServer ?: return null
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "companion.connection.resolved",
            mapOf("kind" to "loopback-http-base-url", "baseUrl" to server.baseUrl())
        )
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
    private val registration: NativeBridgeRegistration,
    private val descriptor: NativeBridgeOperatorDescriptor,
    private val logger: NativeBridgeDiagnosticLogger
) : NanoHTTPD("127.0.0.1", 0), NativeBridgeLoopbackServer {

    override fun baseUrl(): String = "http://127.0.0.1:$listeningPort${descriptor.listenPath}"

    override fun startServer() {
        start(SOCKET_READ_TIMEOUT, false)
    }

    override fun stopServer() {
        stop()
    }

    override fun serve(session: IHTTPSession): Response {
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.loopback.request.received",
            mapOf(
                "method" to session.method.name,
                "path" to session.uri
            )
        )
        if (session.method == Method.OPTIONS) {
            return jsonResponse(Response.Status.NO_CONTENT, "")
        }
        if (session.uri != descriptor.listenPath) {
            logger(
                "runtime-operator",
                "warn",
                registration.addonId,
                registration.companionId,
                "runtime-operator.loopback.request.unknown-path",
                mapOf(
                    "path" to session.uri,
                    "classification" to "loopback-route-miss"
                )
            )
            return jsonResponse(
                Response.Status.NOT_FOUND,
                JSONObject()
                    .put("ok", false)
                    .put("error", "Unknown runtime operator path.")
                    .put("classification", "loopback-route-miss")
                    .toString()
            )
        }
        val uri = session.parameters["uri"]?.firstOrNull()
        val sampleMs = session.parameters["samplems"]?.firstOrNull()
        if (uri.isNullOrBlank() || sampleMs.isNullOrBlank()) {
            logger(
                "runtime-operator",
                "warn",
                registration.addonId,
                registration.companionId,
                "runtime-operator.loopback.request.missing-query",
                mapOf(
                    "hasUri" to (!uri.isNullOrBlank()),
                    "hasSampleMs" to (!sampleMs.isNullOrBlank()),
                    "classification" to "missing-query"
                )
            )
            return jsonResponse(
                Response.Status.BAD_REQUEST,
                JSONObject()
                    .put("ok", false)
                    .put("error", "Missing required query parameters: uri and samplems")
                    .put("classification", "missing-query")
                    .toString()
            )
        }
        return try {
            val responseBody = proxyShazamDiscoveryRequest(uri, sampleMs)
            val contractFailure = classifyProxyResponseBody(responseBody)
            if (contractFailure != null) {
                logger(
                    "runtime-operator",
                    "error",
                    registration.addonId,
                    registration.companionId,
                    "runtime-operator.loopback.response-contract.failed",
                    mapOf(
                        "sampleMs" to sampleMs,
                        "classification" to contractFailure,
                        "bodyPrefix" to bodyPrefix(responseBody)
                    )
                )
                return jsonResponse(
                    Response.Status.INTERNAL_ERROR,
                    JSONObject()
                        .put("ok", false)
                        .put("error", "Shazam upstream response violated the JSON response contract.")
                        .put("classification", contractFailure)
                        .put("bodyPrefix", bodyPrefix(responseBody))
                        .toString()
                )
            }
            logger(
                "runtime-operator",
                "info",
                registration.addonId,
                registration.companionId,
                "runtime-operator.loopback.request.completed",
                mapOf("sampleMs" to sampleMs)
            )
            jsonResponse(Response.Status.OK, responseBody)
        } catch (error: Throwable) {
            logger(
                "runtime-operator",
                "error",
                registration.addonId,
                registration.companionId,
                "runtime-operator.loopback.request.failed",
                mapOf(
                    "sampleMs" to sampleMs,
                    "error" to (error.message ?: error.toString()),
                    "classification" to "runtime-operator-crash"
                )
            )
            jsonResponse(
                Response.Status.INTERNAL_ERROR,
                JSONObject()
                    .put("ok", false)
                    .put("error", "Failed to make request to Shazam API")
                    .put("classification", "runtime-operator-crash")
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

    private fun classifyProxyResponseBody(body: String): String? {
        val trimmed = body.trimStart()
        val lowered = trimmed.lowercase()
        if (lowered.startsWith("<!doctype") || lowered.startsWith("<html") || lowered.contains("<body")) {
            return "upstream-html-response"
        }
        return try {
            if (trimmed.startsWith("[")) {
                JSONArray(trimmed)
            } else {
                JSONObject(trimmed)
            }
            null
        } catch (_: Throwable) {
            "upstream-json-parse-failure"
        }
    }

    private fun bodyPrefix(body: String): String =
        body.replace(Regex("\\s+"), " ").trim().take(160)

    private fun buildUpstreamUrl(template: String): String {
        var result = template
        while (result.contains("{uuid}")) {
            result = result.replaceFirst("{uuid}", UUID.randomUUID().toString())
        }
        return result
    }

    private fun jsonResponse(status: Response.Status, body: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            LOOPBACK_CORS_HEADERS.forEach { (name, value) -> addHeader(name, value) }
        }
    }

    private fun pickOne(values: List<String>): String? {
        if (values.isEmpty()) {
            return null
        }
        return values[Random.nextInt(values.size)]
    }
}

private class LrclibLocalLoopbackServer(
    private val context: Context,
    private val registration: NativeBridgeRegistration,
    private val descriptor: NativeBridgeOperatorDescriptor,
    private val logger: NativeBridgeDiagnosticLogger
) : NanoHTTPD("127.0.0.1", 0), NativeBridgeLoopbackServer {
    private var deployment: LrclibDbDeploymentResult? = null
    private var queryEngine: LrclibNativeQueryEngine? = null

    override fun baseUrl(): String = "http://127.0.0.1:$listeningPort${descriptor.listenPath.ifEmpty { "/api" }}"

    override fun startServer() {
        deployment = deployLrclibDbOnce()
        queryEngine = LrclibNativeQueryEngine(
            dbFile = deployment!!.dbFile,
            registration = registration,
            logger = logger
        )
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.lrclib.start.ready",
            mapOf(
                "runtimeOperatorKind" to descriptor.runtimeOperatorKind,
                "dbDeployed" to (deployment?.dbFile?.absolutePath ?: ""),
                "dbReused" to (deployment?.reused ?: false)
            )
        )
        start(SOCKET_READ_TIMEOUT, false)
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "runtime-operator.lrclib.loopback.bound",
            mapOf("baseUrl" to baseUrl())
        )
    }

    override fun stopServer() {
        stop()
    }

    override fun serve(session: IHTTPSession): Response {
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "lrclib.loopback.request.received",
            mapOf("method" to session.method.name, "path" to session.uri)
        )
        if (session.method == Method.OPTIONS) {
            return jsonResponse(Response.Status.NO_CONTENT, "", "not-applicable")
        }
        val engine = queryEngine
            ?: return jsonResponse(
                Response.Status.INTERNAL_ERROR,
                JSONObject().put("ok", false).put("classification", "sqlite-not-ready").put("error", "LRCLIB query engine is not ready.").toString(),
                "sqlite-not-ready"
            )
        val response = when (session.uri) {
            "${descriptor.listenPath.ifEmpty { "/api" }}/get" -> engine.handleGet(session.parameters)
            "${descriptor.listenPath.ifEmpty { "/api" }}/search" -> engine.handleSearch(session.parameters)
            else -> LrclibQueryResponse(
                Response.Status.NOT_FOUND,
                JSONObject()
                    .put("ok", false)
                    .put("classification", "loopback-route-miss")
                    .put("error", "Unknown LRCLIB local route.")
                    .toString(),
                "loopback-route-miss"
            )
        }
        logger(
            "runtime-operator",
            if (response.status.requestStatus in 200..399) "info" else "warn",
            registration.addonId,
            registration.companionId,
            "lrclib.loopback.request.completed",
            mapOf("path" to session.uri, "classification" to response.classification, "status" to response.status.requestStatus)
        )
        return jsonResponse(response.status, response.body, response.classification)
    }

    private fun jsonResponse(status: Response.Status, body: String, classification: String): Response {
        return newFixedLengthResponse(status, "application/json; charset=utf-8", body).apply {
            LOOPBACK_CORS_HEADERS.forEach { (name, value) -> addHeader(name, value) }
            addHeader(descriptor.localResultHeader.ifEmpty { "x-axolync-lrclib-local-result" }, classification)
        }
    }

    private fun deployLrclibDbOnce(): LrclibDbDeploymentResult {
        val dbConfig = descriptor.db
            ?: throw IllegalStateException("LRCLIB native operator descriptor is missing db deployment config.")
        if (dbConfig.deployPolicy.isNotBlank() && dbConfig.deployPolicy != "once-per-compressed-sha256") {
            throw IllegalStateException("Unsupported LRCLIB DB deploy policy: ${dbConfig.deployPolicy}")
        }
        val packagedAssetPath = packagedNativeAssetPath(registration.entrypoint, dbConfig.packagedAssetPath.ifEmpty { dbConfig.compressedAssetPath })
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "lrclib.db.asset.resolved",
            mapOf("assetPath" to packagedAssetPath, "deployPolicy" to dbConfig.deployPolicy)
        )
        val compressedSha256 = sha256Asset(context, packagedAssetPath)
        val deployedFileName = dbConfig.deployedFileName.ifEmpty { "db.sqlite3" }
        val deployDir = File(
            context.filesDir,
            listOf(
                "axolync",
                "native-service-companions",
                safeFileToken(registration.addonId),
                safeFileToken(registration.companionId),
                safeFileToken(descriptor.runtimeOperatorKind),
                compressedSha256
            ).joinToString(File.separator)
        )
        val dbFile = File(deployDir, deployedFileName)
        val metadataFile = File(deployDir, "deployment.json")
        val metadata = readJsonObject(metadataFile)
        if (
            dbFile.isFile
            && metadata?.optString("compressedAssetSha256") == compressedSha256
            && (!dbConfig.sqliteHeaderRequired || hasSqliteHeader(dbFile))
        ) {
            logger(
                "runtime-operator",
                "info",
                registration.addonId,
                registration.companionId,
                "lrclib.db.deploy.reused",
                mapOf("assetPath" to packagedAssetPath, "assetSha256" to compressedSha256, "dbPath" to dbFile.absolutePath)
            )
            return LrclibDbDeploymentResult(dbFile, metadataFile, packagedAssetPath, compressedSha256, reused = true, replaced = false)
        }

        val replaced = deployDir.exists()
        deployDir.deleteRecursively()
        deployDir.mkdirs()
        BrotliInputStream(context.assets.open(packagedAssetPath)).use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        }
        if (dbConfig.sqliteHeaderRequired && !hasSqliteHeader(dbFile)) {
            dbFile.delete()
            throw IllegalStateException("Deployed LRCLIB DB does not have a SQLite header.")
        }
        metadataFile.writeText(
            JSONObject()
                .put("compressedAssetPath", packagedAssetPath)
                .put("compressedAssetSha256", compressedSha256)
                .put("deployedDbPath", dbFile.absolutePath)
                .put("deployedAtMs", System.currentTimeMillis())
                .toString(),
            StandardCharsets.UTF_8
        )
        logger(
            "runtime-operator",
            "info",
            registration.addonId,
            registration.companionId,
            "lrclib.db.deploy.completed",
            mapOf("assetPath" to packagedAssetPath, "assetSha256" to compressedSha256, "dbPath" to dbFile.absolutePath, "replaced" to replaced)
        )
        return LrclibDbDeploymentResult(dbFile, metadataFile, packagedAssetPath, compressedSha256, reused = false, replaced = replaced)
    }
}

private class LrclibNativeQueryEngine(
    private val dbFile: File,
    private val registration: NativeBridgeRegistration,
    private val logger: NativeBridgeDiagnosticLogger
) {
    fun handleGet(parameters: Map<String, List<String>>): LrclibQueryResponse {
        val trackName = firstParameter(parameters, "track_name")
        val artistName = firstParameter(parameters, "artist_name")
        val albumName = firstParameter(parameters, "album_name")
        val duration = firstParameter(parameters, "duration")?.toDoubleOrNull()
        if (trackName.isNullOrBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "missing-query", "Missing required query parameter: track_name")
        }
        return try {
            val record = querySingle(trackName, artistName, albumName, duration)
            val response = if (record == null) {
                notFoundResponse("subset-miss")
            } else {
                recordResponse(record)
            }
            logQueryCompleted("/api/get", response)
            response
        } catch (error: Throwable) {
            logger(
                "runtime-operator",
                "error",
                registration.addonId,
                registration.companionId,
                "lrclib.sqlite.query.failed",
                mapOf("route" to "/api/get", "error" to (error.message ?: error.toString()))
            )
            errorResponse(Response.Status.INTERNAL_ERROR, "sqlite-query-failure", error.message ?: error.toString())
        }
    }

    fun handleSearch(parameters: Map<String, List<String>>): LrclibQueryResponse {
        val q = firstParameter(parameters, "q")
        val trackName = firstParameter(parameters, "track_name")
        val artistName = firstParameter(parameters, "artist_name")
        val albumName = firstParameter(parameters, "album_name")
        if (q.isNullOrBlank() && trackName.isNullOrBlank() && artistName.isNullOrBlank() && albumName.isNullOrBlank()) {
            return errorResponse(Response.Status.BAD_REQUEST, "missing-query", "Missing at least one search query parameter.")
        }
        return try {
            val records = querySearch(q, trackName, artistName, albumName)
            val response = if (records.isEmpty()) {
                LrclibQueryResponse(Response.Status.OK, JSONArray().toString(), "subset-miss")
            } else {
                val classification = classifyRecord(records.first())
                LrclibQueryResponse(
                    Response.Status.OK,
                    JSONArray().apply { records.forEach { put(it) } }.toString(),
                    classification
                )
            }
            logQueryCompleted("/api/search", response)
            response
        } catch (error: Throwable) {
            logger(
                "runtime-operator",
                "error",
                registration.addonId,
                registration.companionId,
                "lrclib.sqlite.query.failed",
                mapOf("route" to "/api/search", "error" to (error.message ?: error.toString()))
            )
            errorResponse(Response.Status.INTERNAL_ERROR, "sqlite-query-failure", error.message ?: error.toString())
        }
    }

    private fun querySingle(trackName: String, artistName: String?, albumName: String?, duration: Double?): JSONObject? {
        val clauses = mutableListOf("t.name_lower = ?")
        val args = mutableListOf(normalizeQueryText(trackName))
        if (!artistName.isNullOrBlank()) {
            clauses += "t.artist_name_lower = ?"
            args += normalizeQueryText(artistName)
        }
        if (!albumName.isNullOrBlank()) {
            clauses += "t.album_name_lower = ?"
            args += normalizeQueryText(albumName)
        }
        val durationOrder = if (duration != null) "ABS(t.duration - ?) ASC," else ""
        if (duration != null) {
            args += duration.toString()
        }
        val sql = """
            SELECT t.id AS track_id, t.name, t.artist_name, t.album_name, t.duration,
                   l.id AS lyric_id, l.plain_lyrics, l.synced_lyrics, l.instrumental
            FROM tracks t
            LEFT JOIN lyrics l ON l.track_id = t.id
            WHERE ${clauses.joinToString(" AND ")}
            ORDER BY $durationOrder CASE WHEN l.id = t.last_lyrics_id THEN 0 ELSE 1 END, l.id ASC
            LIMIT 1
        """.trimIndent()
        return queryJsonRows(sql, args.toTypedArray(), 1).firstOrNull()
    }

    private fun querySearch(q: String?, trackName: String?, artistName: String?, albumName: String?): List<JSONObject> {
        val clauses = mutableListOf<String>()
        val args = mutableListOf<String>()
        if (!q.isNullOrBlank()) {
            clauses += "(t.name_lower LIKE ? OR t.artist_name_lower LIKE ? OR t.album_name_lower LIKE ?)"
            val like = "%${normalizeQueryText(q)}%"
            args += like
            args += like
            args += like
        }
        if (!trackName.isNullOrBlank()) {
            clauses += "t.name_lower LIKE ?"
            args += "%${normalizeQueryText(trackName)}%"
        }
        if (!artistName.isNullOrBlank()) {
            clauses += "t.artist_name_lower LIKE ?"
            args += "%${normalizeQueryText(artistName)}%"
        }
        if (!albumName.isNullOrBlank()) {
            clauses += "t.album_name_lower LIKE ?"
            args += "%${normalizeQueryText(albumName)}%"
        }
        val where = if (clauses.isEmpty()) "1 = 1" else clauses.joinToString(" AND ")
        val sql = """
            SELECT t.id AS track_id, t.name, t.artist_name, t.album_name, t.duration,
                   l.id AS lyric_id, l.plain_lyrics, l.synced_lyrics, l.instrumental
            FROM tracks t
            LEFT JOIN lyrics l ON l.track_id = t.id
            WHERE $where
            ORDER BY CASE WHEN l.id = t.last_lyrics_id THEN 0 ELSE 1 END, t.name ASC, t.artist_name ASC
            LIMIT 10
        """.trimIndent()
        return queryJsonRows(sql, args.toTypedArray(), 10)
    }

    private fun queryJsonRows(sql: String, args: Array<String>, limit: Int): List<JSONObject> {
        val db = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            logger(
                "runtime-operator",
                "info",
                registration.addonId,
                registration.companionId,
                "lrclib.sqlite.open.completed",
                mapOf("dbPath" to dbFile.absolutePath, "readOnly" to true)
            )
            db.rawQuery(sql, args).use { cursor ->
                buildList {
                    while (cursor.moveToNext() && size < limit) {
                        add(cursorToLrclibRecord(cursor))
                    }
                }
            }
        } finally {
            db.close()
        }
    }

    private fun logQueryCompleted(route: String, response: LrclibQueryResponse) {
        logger(
            "runtime-operator",
            if (response.status.requestStatus in 200..399) "info" else "warn",
            registration.addonId,
            registration.companionId,
            "lrclib.sqlite.query.completed",
            mapOf("route" to route, "classification" to response.classification, "status" to response.status.requestStatus)
        )
    }

    private fun cursorToLrclibRecord(cursor: Cursor): JSONObject =
        JSONObject()
            .put("id", cursor.getLongByName("track_id"))
            .put("trackName", cursor.getStringOrNullByName("name"))
            .put("name", cursor.getStringOrNullByName("name"))
            .put("artistName", cursor.getStringOrNullByName("artist_name"))
            .put("albumName", cursor.getStringOrNullByName("album_name"))
            .put("duration", cursor.getDoubleByName("duration"))
            .put("instrumental", cursor.getIntByName("instrumental") == 1)
            .putNullable("plainLyrics", cursor.getStringOrNullByName("plain_lyrics"))
            .putNullable("syncedLyrics", cursor.getStringOrNullByName("synced_lyrics"))

    private fun recordResponse(record: JSONObject): LrclibQueryResponse =
        LrclibQueryResponse(Response.Status.OK, record.toString(), classifyRecord(record))

    private fun notFoundResponse(classification: String): LrclibQueryResponse =
        LrclibQueryResponse(
            Response.Status.NOT_FOUND,
            JSONObject()
                .put("ok", false)
                .put("classification", classification)
                .put("error", "LRCLIB local subset has no matching lyrics.")
                .toString(),
            classification
        )

    private fun errorResponse(status: Response.Status, classification: String, message: String): LrclibQueryResponse =
        LrclibQueryResponse(
            status,
            JSONObject()
                .put("ok", false)
                .put("classification", classification)
                .put("error", message)
                .toString(),
            classification
        )
}

private fun firstParameter(parameters: Map<String, List<String>>, name: String): String? =
    parameters[name]?.firstOrNull()?.trim()

private fun normalizeQueryText(value: String): String =
    value.trim().lowercase()

private fun classifyRecord(record: JSONObject): String {
    val syncedLyrics = record.optString("syncedLyrics", "").trim()
    val plainLyrics = record.optString("plainLyrics", "").trim()
    return when {
        syncedLyrics.isNotEmpty() -> "local-hit"
        plainLyrics.isNotEmpty() -> "plain-only"
        else -> "subset-miss"
    }
}

private fun JSONObject.putNullable(name: String, value: String?): JSONObject =
    put(name, value ?: JSONObject.NULL)

private fun Cursor.getColumnIndexOrThrowCompat(name: String): Int =
    getColumnIndex(name).takeIf { it >= 0 } ?: throw IllegalStateException("Missing SQLite column \"$name\".")

private fun Cursor.getStringOrNullByName(name: String): String? {
    val index = getColumnIndexOrThrowCompat(name)
    return if (isNull(index)) null else getString(index)
}

private fun Cursor.getLongByName(name: String): Long =
    getLong(getColumnIndexOrThrowCompat(name))

private fun Cursor.getDoubleByName(name: String): Double =
    getDouble(getColumnIndexOrThrowCompat(name))

private fun Cursor.getIntByName(name: String): Int {
    val index = getColumnIndexOrThrowCompat(name)
    return if (isNull(index)) 0 else getInt(index)
}

private fun packagedNativeAssetPath(entrypoint: String, packagedAssetPath: String): String {
    val entrypointDir = normalizeAssetPath(entrypoint).substringBeforeLast("/", "")
    val normalizedPackagedAssetPath = normalizeAssetPath(packagedAssetPath)
    return listOf("public", "native-service-companions", entrypointDir, normalizedPackagedAssetPath)
        .filter { it.isNotBlank() }
        .joinToString("/")
}

private fun normalizeAssetPath(value: String): String =
    value.trim().replace('\\', '/').trimStart('/')

private fun sha256Asset(context: Context, assetPath: String): String {
    val digest = MessageDigest.getInstance("SHA-256")
    context.assets.open(assetPath).use { input ->
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

private fun hasSqliteHeader(file: File): Boolean {
    if (!file.isFile || file.length() < 16) return false
    val header = ByteArray(16)
    file.inputStream().use { input ->
        if (input.read(header) != header.size) return false
    }
    return String(header, StandardCharsets.US_ASCII) == "SQLite format 3\u0000"
}

private fun readJsonObject(file: File): JSONObject? {
    if (!file.isFile) return null
    return try {
        JSONObject(file.readText(StandardCharsets.UTF_8))
    } catch (_: Throwable) {
        null
    }
}

private fun safeFileToken(value: String): String =
    value.trim().replace(Regex("[^A-Za-z0-9._-]+"), "_").ifEmpty { "unknown" }

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
    private val diagnostics = mutableListOf<NativeBridgeDiagnosticEntry>()
    private val registrationByKey: MutableMap<String, NativeBridgeRegistration> by lazy { loadRegistrations().toMutableMap() }

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = addonId,
            companionId = companionId,
            event = "companion.status.requested",
            details = null
        )
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun getHostInfo(call: PluginCall) {
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = null,
            companionId = null,
            event = "host.info.requested",
            details = null
        )
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
            appendDiagnostic(
                source = "wrapper-host",
                level = "warn",
                addonId = addonId,
                companionId = companionId,
                event = "companion.enable.unsupported",
                details = null
            )
            call.resolve(buildUnsupportedStatusEnvelope(addonId, companionId))
            return
        }
        enabledState[key] = enabled
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = addonId,
            companionId = companionId,
            event = "companion.enabled.updated",
            details = mapOf("enabled" to enabled)
        )
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
            appendDiagnostic(
                source = "wrapper-host",
                level = "warn",
                addonId = addonId,
                companionId = companionId,
                event = "companion.start.unsupported",
                details = null
            )
            call.resolve(buildUnsupportedStatusEnvelope(addonId, companionId))
            return
        }
        val key = companionKey(addonId, companionId)
        enabledState[key] = true
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = addonId,
            companionId = companionId,
            event = "companion.start.requested",
            details = mapOf("entrypoint" to registration.entrypoint)
        )
        try {
            val runtime = runtimeOperators.getOrPut(key) {
                NativeBridgeRuntimeOperator(context, registration, ::appendDiagnostic)
            }
            runtime.start()
            lastErrorState.remove(key)
            appendDiagnostic(
                source = "wrapper-host",
                level = "info",
                addonId = addonId,
                companionId = companionId,
                event = "companion.start.completed",
                details = mapOf("baseUrl" to runtime.getConnection()?.getString("baseUrl"))
            )
        } catch (error: Throwable) {
            lastErrorState[key] = error.message ?: error.toString()
            appendDiagnostic(
                source = "wrapper-host",
                level = "error",
                addonId = addonId,
                companionId = companionId,
                event = "companion.start.failed",
                details = mapOf(
                    "error" to lastErrorState[key],
                    "failureSource" to "native-runtime-start"
                )
            )
        }
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val key = companionKey(addonId, companionId)
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = addonId,
            companionId = companionId,
            event = "companion.stop.requested",
            details = null
        )
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
            appendDiagnostic(
                source = "wrapper-host",
                level = "warn",
                addonId = addonId,
                companionId = companionId,
                event = "companion.request.unsupported",
                details = null
            )
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
            appendDiagnostic(
                source = "wrapper-host",
                level = "warn",
                addonId = addonId,
                companionId = companionId,
                event = "companion.request.not-running",
                details = null
            )
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
            appendDiagnostic(
                source = "wrapper-host",
                level = "info",
                addonId = addonId,
                companionId = companionId,
                event = "companion.request.completed",
                details = mapOf("operation" to operation)
            )
            call.resolve(buildResponseEnvelope(addonId, companionId, ok = true, payload = payload, error = null))
        } catch (error: Throwable) {
            lastErrorState[key] = error.message ?: error.toString()
            appendDiagnostic(
                source = "wrapper-host",
                level = "error",
                addonId = addonId,
                companionId = companionId,
                event = "companion.request.failed",
                details = mapOf(
                    "operation" to call.getObject("request")?.getString("operation"),
                    "error" to lastErrorState[key]
                )
            )
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
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = addonId,
            companionId = companionId,
            event = "companion.connection.requested",
            details = null
        )
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "connection",
            runtimeOperators[companionKey(addonId, companionId)]?.getConnection() ?: JSONObject.NULL
        )
        call.resolve(envelope)
    }

    @PluginMethod
    fun getDiagnostics(call: PluginCall) {
        call.resolve(buildDiagnosticsEnvelope())
    }

    @PluginMethod
    fun saveDebugArchiveBase64(call: PluginCall) {
        val requestedFileName = call.getString("fileName").orEmpty()
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = null,
            companionId = null,
            event = "debug-archive.save.requested",
            details = mapOf("fileName" to requestedFileName)
        )
        val result = persistDebugArchive(
            fileName = requestedFileName,
            base64Payload = call.getString("base64Payload").orEmpty()
        )
        if (result.optBoolean("success", false)) {
            appendDiagnostic(
                source = "wrapper-host",
                level = "info",
                addonId = null,
                companionId = null,
                event = "debug-archive.save.completed",
                details = mapOf(
                    "fileName" to result.optString("fileName"),
                    "uri" to result.optString("uri", "")
                )
            )
        } else {
            appendDiagnostic(
                source = "wrapper-host",
                level = "warn",
                addonId = null,
                companionId = null,
                event = "debug-archive.save.failed",
                details = mapOf(
                    "fileName" to result.optString("fileName", requestedFileName),
                    "error" to result.optString("error", "unknown")
                )
            )
        }
        call.resolve(result)
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

    private fun appendDiagnostic(
        source: String,
        level: String,
        addonId: String?,
        companionId: String?,
        event: String,
        details: Map<String, Any?>?
    ) {
        synchronized(diagnostics) {
            diagnostics += NativeBridgeDiagnosticEntry(
                atMs = System.currentTimeMillis(),
                source = source,
                level = level,
                addonId = addonId,
                companionId = companionId,
                event = event,
                details = details
            )
            if (diagnostics.size > MAX_NATIVE_BRIDGE_DIAGNOSTICS) {
                diagnostics.subList(0, diagnostics.size - MAX_NATIVE_BRIDGE_DIAGNOSTICS).clear()
            }
        }
        RuntimeNativeLogStore.record(
            source = source,
            level = level,
            message = event,
            details = details?.toJsonObject()?.toString()
        )
    }

    private fun buildDiagnosticsEnvelope(): JSObject {
        val logs = synchronized(diagnostics) { diagnostics.toList() }
        return JSObject().apply {
            put("hostFamily", CAPACITOR_HOST_FAMILY)
            put("hostPlatform", CAPACITOR_HOST_PLATFORM)
            put("hostAbi", detectHostAbi())
            put("generatedAtMs", System.currentTimeMillis())
            put("collectionMethod", "native-bridge-host")
            put(
                "bridgePublication",
                JSObject().apply {
                    put("publicationMode", "capacitor-plugin-registry")
                    put("pluginName", "AxolyncNativeServiceCompanionHost")
                    put("pluginRegistryAssetPath", "capacitor.plugins.json")
                    put("mirroredPluginRegistryAssetPath", "capacitor/capacitor.plugins.json")
                    put("packageName", "axolync-native-bridge-host")
                    put("classpath", "com.axolync.android.bridge.AxolyncNativeServiceCompanionHostPlugin")
                }
            )
            put(
                "logs",
                JSONArray().apply {
                    logs.forEach { entry ->
                        put(
                            JSObject().apply {
                                put("atMs", entry.atMs)
                                put("source", entry.source)
                                put("level", entry.level)
                                put("addonId", entry.addonId ?: JSONObject.NULL)
                                put("companionId", entry.companionId ?: JSONObject.NULL)
                                put("event", entry.event)
                                put("details", entry.details?.toJsonObject() ?: JSONObject.NULL)
                            }
                        )
                    }
                }
            )
        }
    }

    private fun persistDebugArchive(fileName: String, base64Payload: String): JSObject {
        return persistDebugArchive(
            context = context,
            fileName = fileName,
            base64Payload = base64Payload,
            logTag = "AxolyncNativeServiceCompanionHost"
        )
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
            val descriptorAssetPath = "public/native-service-companions/$entrypoint"
            val descriptorText = readAssetText(descriptorAssetPath)
            if (descriptorText == null) {
                appendDiagnostic(
                    source = "wrapper-host",
                    level = "warn",
                    addonId = addonId,
                    companionId = companionId,
                    event = "host.registration.descriptor-missing",
                    details = mapOf("entrypoint" to entrypoint, "assetPath" to descriptorAssetPath)
                )
                continue
            }
            val descriptor = parseOperatorDescriptor(JSONObject(descriptorText))
            resolved[companionKey(addonId, companionId)] = NativeBridgeRegistration(
                addonId = addonId,
                companionId = companionId,
                displayName = row.optString("displayName").trim(),
                wrapper = wrapper,
                entrypoint = entrypoint,
                operator = descriptor
            )
            appendDiagnostic(
                source = "wrapper-host",
                level = "info",
                addonId = addonId,
                companionId = companionId,
                event = "host.registration.loaded",
                details = mapOf(
                    "entrypoint" to entrypoint,
                    "runtimeOperatorKind" to descriptor.runtimeOperatorKind,
                    "hasDbConfig" to (descriptor.db != null)
                )
            )
        }
        appendDiagnostic(
            source = "wrapper-host",
            level = "info",
            addonId = null,
            companionId = null,
            event = "host.registrations.loaded",
            details = mapOf("count" to resolved.size)
        )
        return resolved
    }

    private fun parseOperatorDescriptor(parsed: JSONObject): NativeBridgeOperatorDescriptor {
        val geo = parsed.optJSONObject("geo") ?: JSONObject()
        val db = parsed.optJSONObject("db")
        val diagnostics = parsed.optJSONObject("diagnostics")
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
            timezones = parsed.optJSONArray("timezones").toStringList(),
            db = db?.let {
                NativeBridgeOperatorDbConfig(
                    compressedAssetPath = it.optString("compressedAssetPath").trim(),
                    packagedAssetPath = it.optString("packagedAssetPath").trim(),
                    packagedProvenancePath = it.optString("packagedProvenancePath").trim(),
                    deployedFileName = it.optString("deployedFileName").trim(),
                    deployPolicy = it.optString("deployPolicy").trim(),
                    sqliteHeaderRequired = it.optBoolean("sqliteHeaderRequired", true)
                )
            },
            localResultHeader = parsed.optString("local_result_header").trim()
                .ifEmpty { diagnostics?.optString("localResultHeader")?.trim().orEmpty() }
                .ifEmpty { "x-axolync-lrclib-local-result" }
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

    private fun Map<String, Any?>.toJsonObject(): JSObject {
        return JSObject().apply {
            this@toJsonObject.forEach { (key, value) ->
                put(key, value ?: JSONObject.NULL)
            }
        }
    }
}
