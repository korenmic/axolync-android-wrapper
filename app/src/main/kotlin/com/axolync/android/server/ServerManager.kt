package com.axolync.android.server

import android.content.Context
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * ServerManager manages the embedded HTTP server lifecycle at application scope.
 * Singleton pattern ensures server survives Activity recreation.
 * 
 * ASYNC STARTUP: Server starts on background thread to avoid blocking Application.onCreate().
 * CONCURRENCY-SAFE: startServerAsync() is synchronized and idempotent.
 * 
 * Requirements: 6.1, 6.2, 11.3, 11.4, 11.9, 11.10
 */
class ServerManager private constructor(private val context: Context) {
    
    enum class ServerState {
        STARTING,
        READY,
        FAILED
    }
    
    data class ServerMetrics(
        val state: ServerState,
        val port: Int?,
        val startDurationMs: Long?,
        val failureCategory: String?
    )
    
    private val serverState = AtomicReference(ServerState.STARTING)
    private val startScheduled = AtomicBoolean(false)
    private val executor = Executors.newSingleThreadExecutor()
    private var localHttpServer: LocalHttpServer? = null
    private var baseUrl: String? = null
    private var failureReason: String? = null
    private var failureCategory: String? = null
    private var startDurationMs: Long? = null
    
    companion object {
        private const val TAG = "ServerManager"
        
        @Volatile
        private var instance: ServerManager? = null
        
        fun getInstance(context: Context): ServerManager {
            return instance ?: synchronized(this) {
                instance ?: ServerManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    /**
     * Start the embedded HTTP server ASYNCHRONOUSLY on background thread.
     * IDEMPOTENT: Safe to call multiple times.
     * CONCURRENCY-SAFE: Synchronized to prevent race conditions.
     * 
     * Does NOT block caller - returns immediately.
     * State transitions from STARTING -> READY or FAILED asynchronously.
     */
    @Synchronized
    fun startServerAsync() {
        // Idempotent: if already READY, do nothing
        if (serverState.get() == ServerState.READY) {
            Log.i(TAG, "Server already running at $baseUrl")
            return
        }
        
        // If FAILED, do nothing (no automatic retry)
        if (serverState.get() == ServerState.FAILED) {
            Log.w(TAG, "Server previously failed: $failureReason")
            return
        }
        
        // Atomic in-flight guard: prevent duplicate scheduling
        if (!startScheduled.compareAndSet(false, true)) {
            Log.i(TAG, "Server start already scheduled")
            return
        }
        
        // Start server on background thread
        serverState.set(ServerState.STARTING)
        executor.execute {
            startServerInternal()
        }
        
        Log.i(TAG, "Server start initiated on background thread")
    }
    
    /**
     * Internal server start logic (runs on background thread).
     * MUST NOT be called directly - use startServerAsync().
     */
    private fun startServerInternal() {
        val startTime = System.currentTimeMillis()
        
        try {
            Log.i(TAG, "Starting server on background thread...")
            
            val server = LocalHttpServer(context)
            server.start()
            
            localHttpServer = server
            // CANONICAL URL: Use 'localhost' hostname (not 127.0.0.1 IP literal)
            // for cleartext compatibility across API levels
            baseUrl = "http://localhost:${server.listeningPort}"
            startDurationMs = System.currentTimeMillis() - startTime
            serverState.set(ServerState.READY)
            
            Log.i(TAG, "Server started successfully at $baseUrl in ${startDurationMs}ms")
            
        } catch (e: Exception) {
            startDurationMs = System.currentTimeMillis() - startTime
            failureReason = e.message ?: "Unknown error"
            failureCategory = categorizeFailure(e)
            serverState.set(ServerState.FAILED)
            
            Log.e(TAG, "Server failed to start after ${startDurationMs}ms: $failureReason (category: $failureCategory)", e)
        }
    }
    
    /**
     * Categorize failure for observability.
     */
    private fun categorizeFailure(e: Exception): String {
        return when {
            e.message?.contains("bind", ignoreCase = true) == true -> "PORT_BIND_FAILED"
            e.message?.contains("permission", ignoreCase = true) == true -> "PERMISSION_DENIED"
            e.message?.contains("asset", ignoreCase = true) == true -> "ASSET_ERROR"
            else -> "UNKNOWN"
        }
    }
    
    /**
     * Get the base URL of the server (e.g., "http://localhost:8080").
     * Returns null if server is not ready.
     * 
     * CANONICAL: Always returns localhost hostname, never 127.0.0.1.
     */
    fun getBaseUrl(): String? = baseUrl
    
    /**
     * Get current server state.
     */
    fun getServerState(): ServerState = serverState.get()
    
    /**
     * Check if server is ready to serve requests.
     */
    fun isReady(): Boolean = serverState.get() == ServerState.READY
    
    /**
     * Get server metrics for observability.
     */
    fun getMetrics(): ServerMetrics {
        return ServerMetrics(
            state = serverState.get(),
            port = localHttpServer?.listeningPort,
            startDurationMs = startDurationMs,
            failureCategory = failureCategory
        )
    }
    
    /**
     * Get failure reason if server failed to start.
     */
    fun getFailureReason(): String? = failureReason
}
