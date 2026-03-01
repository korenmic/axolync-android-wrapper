package com.axolync.android.utils

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * PluginManager handles installation, updates, and validation of plugin packages.
 * 
 * Storage structure: context.filesDir/plugins/{pluginId}/{version}/
 * Registry: SharedPreferences with plugin metadata
 * 
 * Requirements: 5.5, 5.6, 5.7, 5.8, 5.9, 5.10
 */
class PluginManager(private val context: Context) {

    companion object {
        private const val TAG = "PluginManager"
        private const val PREFS_NAME = "plugin_registry"
        private const val KEY_PLUGINS = "installed_plugins"
        private const val PLUGINS_DIR = "plugins"
    }

    data class PluginMetadata(
        val id: String,
        val version: String,
        val checksum: String,
        val signature: String? = null
    )

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val pluginsBaseDir = File(context.filesDir, PLUGINS_DIR)

    init {
        // Ensure plugins directory exists
        if (!pluginsBaseDir.exists()) {
            pluginsBaseDir.mkdirs()
        }
    }

    /**
     * Install plugin to app-private storage.
     * Requirements: 5.5, 5.6
     */
    fun installPlugin(packagePath: String, pluginId: String): Result<Unit> {
        return try {
            val packageFile = File(packagePath)
            if (!packageFile.exists()) {
                return Result.failure(IllegalArgumentException("Package file not found: $packagePath"))
            }

            // Validate plugin package
            val metadata = validatePlugin(packagePath).getOrElse { 
                return Result.failure(it)
            }

            // Check if plugin ID matches
            if (metadata.id != pluginId) {
                return Result.failure(IllegalArgumentException("Plugin ID mismatch: expected $pluginId, got ${metadata.id}"))
            }

            // Create plugin directory
            val pluginDir = File(pluginsBaseDir, "${pluginId}/${metadata.version}")
            if (pluginDir.exists()) {
                return Result.failure(IllegalStateException("Plugin version already installed: ${metadata.version}"))
            }
            pluginDir.mkdirs()

            // Copy plugin files
            packageFile.copyTo(File(pluginDir, packageFile.name), overwrite = false)

            // Register plugin in registry
            registerPlugin(metadata)

            Log.d(TAG, "Plugin installed: $pluginId version ${metadata.version}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install plugin: $pluginId", e)
            Result.failure(e)
        }
    }

    /**
     * Update plugin with backup and rollback support.
     * Requirements: 5.7, 5.9
     */
    fun updatePlugin(pluginId: String, newPackagePath: String): Result<Unit> {
        return try {
            // Get current plugin metadata
            val currentMetadata = getPluginMetadata(pluginId)
                ?: return Result.failure(IllegalStateException("Plugin not installed: $pluginId"))

            // Validate new package
            val newMetadata = validatePlugin(newPackagePath).getOrElse {
                return Result.failure(it)
            }

            if (newMetadata.id != pluginId) {
                return Result.failure(IllegalArgumentException("Plugin ID mismatch"))
            }

            // Backup current version
            val currentDir = File(pluginsBaseDir, "${pluginId}/${currentMetadata.version}")
            val backupDir = File(pluginsBaseDir, "${pluginId}/${currentMetadata.version}.backup")
            
            if (currentDir.exists()) {
                currentDir.renameTo(backupDir)
            }

            // Install new version
            val installResult = installPlugin(newPackagePath, pluginId)
            
            if (installResult.isFailure) {
                // Rollback on failure
                if (backupDir.exists()) {
                    backupDir.renameTo(currentDir)
                }
                return installResult
            }

            // Clean up backup on success
            if (backupDir.exists()) {
                backupDir.deleteRecursively()
            }

            Log.d(TAG, "Plugin updated: $pluginId from ${currentMetadata.version} to ${newMetadata.version}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update plugin: $pluginId", e)
            Result.failure(e)
        }
    }

    /**
     * Validate plugin with checksum verification.
     * Requirements: 5.8, 5.11
     */
    fun validatePlugin(packagePath: String): Result<PluginMetadata> {
        return try {
            val packageFile = File(packagePath)
            if (!packageFile.exists()) {
                return Result.failure(IllegalArgumentException("Package file not found"))
            }

            // Calculate checksum
            val checksum = calculateChecksum(packageFile)

            // For now, extract metadata from filename or manifest
            // Format: pluginId-version.zip
            val filename = packageFile.nameWithoutExtension
            val parts = filename.split("-")
            
            if (parts.size < 2) {
                return Result.failure(IllegalArgumentException("Invalid plugin package format"))
            }

            val metadata = PluginMetadata(
                id = parts[0],
                version = parts[1],
                checksum = checksum,
                signature = null // TODO: Implement signature verification
            )

            Log.d(TAG, "Plugin validated: ${metadata.id} version ${metadata.version}")
            Result.success(metadata)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to validate plugin", e)
            Result.failure(e)
        }
    }

    /**
     * Rollback plugin to previous version.
     * Requirements: 5.9
     */
    fun rollbackPlugin(pluginId: String): Result<Unit> {
        return try {
            val currentMetadata = getPluginMetadata(pluginId)
                ?: return Result.failure(IllegalStateException("Plugin not installed: $pluginId"))

            val currentDir = File(pluginsBaseDir, "${pluginId}/${currentMetadata.version}")
            val backupDir = File(pluginsBaseDir, "${pluginId}/${currentMetadata.version}.backup")

            if (!backupDir.exists()) {
                return Result.failure(IllegalStateException("No backup available for rollback"))
            }

            // Remove current version
            if (currentDir.exists()) {
                currentDir.deleteRecursively()
            }

            // Restore backup
            backupDir.renameTo(currentDir)

            Log.d(TAG, "Plugin rolled back: $pluginId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to rollback plugin: $pluginId", e)
            Result.failure(e)
        }
    }

    /**
     * List all installed plugins.
     * Requirements: 5.10
     */
    fun listInstalledPlugins(): List<PluginMetadata> {
        return try {
            val pluginsJson = prefs.getString(KEY_PLUGINS, "[]") ?: "[]"
            val pluginsArray = JSONArray(pluginsJson)
            
            (0 until pluginsArray.length()).mapNotNull { i ->
                try {
                    val obj = pluginsArray.getJSONObject(i)
                    PluginMetadata(
                        id = obj.getString("id"),
                        version = obj.getString("version"),
                        checksum = obj.getString("checksum"),
                        signature = obj.optString("signature", null)
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse plugin metadata", e)
                    null
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to list plugins", e)
            emptyList()
        }
    }

    /**
     * Get plugin installation path.
     * Requirements: 5.10
     */
    fun getPluginPath(pluginId: String): String? {
        val metadata = getPluginMetadata(pluginId) ?: return null
        val pluginDir = File(pluginsBaseDir, "${pluginId}/${metadata.version}")
        return if (pluginDir.exists()) pluginDir.absolutePath else null
    }

    private fun registerPlugin(metadata: PluginMetadata) {
        val plugins = listInstalledPlugins().toMutableList()
        
        // Remove existing entry for this plugin ID
        plugins.removeAll { it.id == metadata.id }
        
        // Add new entry
        plugins.add(metadata)
        
        // Save to preferences
        val pluginsArray = JSONArray()
        plugins.forEach { plugin ->
            pluginsArray.put(JSONObject().apply {
                put("id", plugin.id)
                put("version", plugin.version)
                put("checksum", plugin.checksum)
                plugin.signature?.let { put("signature", it) }
            })
        }
        
        prefs.edit().putString(KEY_PLUGINS, pluginsArray.toString()).apply()
    }

    private fun getPluginMetadata(pluginId: String): PluginMetadata? {
        return listInstalledPlugins().find { it.id == pluginId }
    }

    private fun calculateChecksum(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
