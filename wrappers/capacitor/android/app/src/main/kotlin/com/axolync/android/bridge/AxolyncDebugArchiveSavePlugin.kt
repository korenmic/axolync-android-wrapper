package com.axolync.android.bridge

import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

@CapacitorPlugin(name = "AxolyncDebugArchiveSave")
class AxolyncDebugArchiveSavePlugin : Plugin() {

    @PluginMethod
    fun saveDebugArchiveBase64(call: PluginCall) {
        call.resolve(
            persistDebugArchive(
                context = context,
                fileName = call.getString("fileName").orEmpty(),
                base64Payload = call.getString("base64Payload").orEmpty(),
                logTag = "AxolyncDebugArchiveSave"
            )
        )
    }
}
