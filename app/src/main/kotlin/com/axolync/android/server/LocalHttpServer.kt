package com.axolync.android.server

import android.content.Context
import android.content.res.AssetManager
import android.util.Log
import fi.iki.elonen.NanoHTTPD
import java.io.IOException

/**
 * LocalHttpServer serves bundled web application assets via HTTP on localhost.
 * HARDENED with traversal protection, method restrictions, SPA fallback, and HEAD support.
 * 
 * Security: Server binds to 127.0.0.1 only (localhost), preventing external access.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4, 11.9
 */
class LocalHttpServer(
    private val context: Context,
    private val port: Int = 0  // 0 = auto-assign
) : NanoHTTPD("127.0.0.1", port) {
    
    private val assetManager: AssetManager = context.assets
    private val assetBasePath = "axolync-browser"
    
    companion object {
        private const val TAG = "LocalHttpServer"
        
        // Supported MIME types (explicit mapping)
        private val MIME_TYPES = mapOf(
            ".html" to "text/html",
            ".js" to "application/javascript",
            ".css" to "text/css",
            ".json" to "application/json",
            ".map" to "application/json",
            ".svg" to "image/svg+xml",
            ".png" to "image/png",
            ".jpg" to "image/jpeg",
            ".jpeg" to "image/jpeg",
            ".woff" to "font/woff",
            ".woff2" to "font/woff2",
            ".ttf" to "font/ttf",
            ".wasm" to "application/wasm"
        )
    }
    
    override fun serve(session: IHTTPSession): Response {
        // Only allow GET and HEAD methods
        if (session.method != Method.GET && session.method != Method.HEAD) {
            Log.w(TAG, "Method not allowed: ${session.method}")
            return newFixedLengthResponse(
                Response.Status.METHOD_NOT_ALLOWED,
                "text/plain",
                "405 Method Not Allowed"
            )
        }
        
        var uri = session.uri
        
        // Health check endpoint
        if (uri == "/health") {
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json",
                "{\"status\":\"ok\",\"server\":\"LocalHttpServer\",\"version\":\"1.0\"}"
            )
        }
        
        // Normalize URI FIRST, then check for traversal
        uri = normalizeUri(uri)
        if (uri == null) {
            Log.w(TAG, "Path traversal attempt blocked: ${session.uri}")
            return newFixedLengthResponse(
                Response.Status.FORBIDDEN,
                "text/plain",
                "403 Forbidden"
            )
        }
        
        // Default to index.html for root
        if (uri == "/" || uri.isEmpty()) {
            uri = "/index.html"
        }
        
        // Construct asset path (remove leading slash)
        val assetPath = "$assetBasePath${uri}"
        
        return try {
            // For HEAD requests, check existence and get MIME type without opening stream
            if (session.method == Method.HEAD) {
                // Check if asset exists by attempting to list (doesn't open stream)
                try {
                    assetManager.open(assetPath).use { /* Just check existence */ }
                    val mimeType = getMimeType(uri)
                    Log.d(TAG, "HEAD: $assetPath exists ($mimeType)")
                    val response = newFixedLengthResponse(Response.Status.OK, mimeType, "")
                    response.addHeader("Content-Type", mimeType)
                    return response
                } catch (e: IOException) {
                    // Asset not found - check SPA fallback
                    if (shouldFallbackToIndex(uri)) {
                        try {
                            assetManager.open("$assetBasePath/index.html").use { /* Just check existence */ }
                            Log.d(TAG, "HEAD: SPA fallback for $uri")
                            val response = newFixedLengthResponse(Response.Status.OK, "text/html", "")
                            response.addHeader("Content-Type", "text/html")
                            return response
                        } catch (indexError: IOException) {
                            Log.w(TAG, "HEAD: Asset not found: $assetPath")
                            return newFixedLengthResponse(
                                Response.Status.NOT_FOUND,
                                "text/plain",
                                ""
                            )
                        }
                    } else {
                        Log.w(TAG, "HEAD: Asset not found: $assetPath")
                        return newFixedLengthResponse(
                            Response.Status.NOT_FOUND,
                            "text/plain",
                            ""
                        )
                    }
                }
            }
            
            // GET request: open stream and return full content
            val inputStream = assetManager.open(assetPath)
            val mimeType = getMimeType(uri)
            
            Log.d(TAG, "Serving: $assetPath ($mimeType)")
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: IOException) {
            // SPA FALLBACK: If asset not found and not a file extension, serve index.html
            // NOTE: API-like paths (e.g., /api/*) should NOT fallback if introduced later
            if (shouldFallbackToIndex(uri)) {
                Log.d(TAG, "SPA fallback: serving index.html for $uri")
                return try {
                    val indexStream = assetManager.open("$assetBasePath/index.html")
                    newChunkedResponse(Response.Status.OK, "text/html", indexStream)
                } catch (indexError: IOException) {
                    Log.e(TAG, "Failed to serve index.html fallback", indexError)
                    newFixedLengthResponse(
                        Response.Status.NOT_FOUND,
                        "text/plain",
                        "404 Not Found: $uri"
                    )
                }
            } else {
                Log.w(TAG, "Asset not found: $assetPath")
                newFixedLengthResponse(
                    Response.Status.NOT_FOUND,
                    "text/plain",
                    "404 Not Found: $uri"
                )
            }
        }
    }
    
    /**
     * Normalize URI and prevent path traversal attacks.
     * Returns null if traversal attempt detected.
     * 
     * TIGHTENED: Normalize THEN reject suspicious paths.
     */
    private fun normalizeUri(uri: String): String? {
        // Decode URI
        val decoded = try {
            java.net.URLDecoder.decode(uri, "UTF-8")
        } catch (e: Exception) {
            return null
        }
        
        // Normalize multiple slashes to single slash
        val normalized = decoded.replace(Regex("/+"), "/")
        
        // Reject if contains ".." (after normalization)
        if (normalized.contains("..")) {
            return null
        }
        
        // Reject if contains encoded traversal patterns
        if (normalized.contains("%2e%2e", ignoreCase = true) ||
            normalized.contains("%2f%2e%2e", ignoreCase = true)) {
            return null
        }
        
        // Reject if contains backslash (Windows-style path)
        if (normalized.contains("\\")) {
            return null
        }
        
        // Reject if contains null bytes
        if (normalized.contains("\u0000")) {
            return null
        }
        
        return normalized
    }
    
    /**
     * Determine if request should fallback to index.html for SPA routing.
     * Fallback if:
     * - URI has no file extension (likely a route)
     * - URI is not /health
     * - URI does not start with /api/ (if API paths are introduced later)
     */
    private fun shouldFallbackToIndex(uri: String): Boolean {
        // Don't fallback for health check
        if (uri == "/health") return false
        
        // Don't fallback for API paths (if introduced later)
        if (uri.startsWith("/api/")) return false
        
        // Don't fallback if URI has a file extension
        val lastSegment = uri.substringAfterLast('/')
        if (lastSegment.contains('.')) return false
        
        // Fallback for extensionless paths (SPA routes)
        return true
    }
    
    /**
     * Get MIME type for file extension.
     * Returns application/octet-stream for unknown types.
     */
    private fun getMimeType(uri: String): String {
        val extension = uri.substringAfterLast('.', "")
        return MIME_TYPES[".$extension"] ?: "application/octet-stream"
    }
    
    fun getServerUrl(): String {
        // CANONICAL: Return localhost hostname (not 127.0.0.1)
        return "http://localhost:${listeningPort}"
    }
    
    fun isRunning(): Boolean {
        return isAlive
    }
}
