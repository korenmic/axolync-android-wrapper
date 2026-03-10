package com.axolync.android.logging

import java.util.ArrayDeque
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import org.json.JSONArray
import org.json.JSONObject

data class RuntimeNativeLogEntry(
    val atMs: Long,
    val source: String,
    val level: String,
    val message: String,
    val details: String? = null,
)

object RuntimeNativeLogStore {
    private const val MAX_ENTRIES = 400
    private val lock = ReentrantLock()
    private val entries = ArrayDeque<RuntimeNativeLogEntry>()

    fun record(
        source: String,
        level: String,
        message: String,
        details: String? = null,
        atMs: Long = System.currentTimeMillis(),
    ) {
        lock.withLock {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(
                RuntimeNativeLogEntry(
                    atMs = atMs,
                    source = source,
                    level = level,
                    message = message,
                    details = details,
                )
            )
        }
    }

    fun clear() {
        lock.withLock {
            entries.clear()
        }
    }

    fun toJsonArray(): JSONArray {
        val snapshot = lock.withLock { entries.toList() }
        return JSONArray().apply {
            snapshot.forEach { entry ->
                put(
                    JSONObject()
                        .put("atMs", entry.atMs)
                        .put("source", entry.source)
                        .put("level", entry.level)
                        .put("message", entry.message)
                        .put("details", entry.details ?: JSONObject.NULL)
                )
            }
        }
    }
}
