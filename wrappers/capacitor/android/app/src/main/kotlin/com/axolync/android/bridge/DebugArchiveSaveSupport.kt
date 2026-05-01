package com.axolync.android.bridge

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import com.getcapacitor.JSObject
import org.json.JSONObject
import java.io.File

internal const val DEBUG_ARCHIVE_MIME_TYPE = "application/zip"
internal const val DEBUG_ARCHIVE_FALLBACK_NAME = "axolync-debug.zip"

internal fun persistDebugArchive(
    context: Context,
    fileName: String,
    base64Payload: String,
    logTag: String
): JSObject {
    return try {
        val safeName = fileName.trim().ifBlank { DEBUG_ARCHIVE_FALLBACK_NAME }
        val bytes = Base64.decode(base64Payload, Base64.DEFAULT)
        val savedUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                put(MediaStore.Downloads.MIME_TYPE, DEBUG_ARCHIVE_MIME_TYPE)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Axolync")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Failed to create MediaStore debug archive entry")
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
                output.flush()
            } ?: throw IllegalStateException("Failed to open MediaStore debug archive stream")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            context.contentResolver.update(uri, values, null, null)
            uri
        } else {
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: throw IllegalStateException("External downloads directory unavailable")
            val target = File(downloadsDir, safeName)
            target.outputStream().use { output ->
                output.write(bytes)
                output.flush()
            }
            Uri.fromFile(target)
        }

        JSObject().apply {
            put("success", true)
            put("fileName", safeName)
            put("uri", savedUri.toString())
            put("error", JSONObject.NULL)
        }
    } catch (error: Exception) {
        Log.e(logTag, "Failed to save debug archive", error)
        JSObject().apply {
            put("success", false)
            put("fileName", fileName.trim().ifBlank { DEBUG_ARCHIVE_FALLBACK_NAME })
            put("uri", JSONObject.NULL)
            put("error", error.message ?: error.toString())
        }
    }
}
