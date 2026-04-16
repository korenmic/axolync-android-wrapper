package com.axolync.android.bridge

import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import org.json.JSONObject

@CapacitorPlugin(name = "AxolyncNativeServiceCompanionHost")
class AxolyncNativeServiceCompanionHostPlugin : Plugin() {

    private val enabledState = mutableMapOf<String, Boolean>()

    @PluginMethod
    fun getStatus(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun setEnabled(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val enabled = call.getBoolean("enabled", false) ?: false
        enabledState["${addonId.trim()}::${companionId.trim()}"] = enabled
        call.resolve(
            buildStatusEnvelope(
                addonId,
                companionId,
                "No native service companion is registered on this Capacitor host."
            )
        )
    }

    @PluginMethod
    fun start(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        call.resolve(
            buildStatusEnvelope(
                addonId,
                companionId,
                "No native service companion is registered on this Capacitor host."
            )
        )
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        call.resolve(buildStatusEnvelope(addonId, companionId, null))
    }

    @PluginMethod
    fun request(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "response",
            JSObject().apply {
                put("ok", false)
                put("payload", JSONObject.NULL)
                put("error", "No native service companion is registered on this Capacitor host.")
            }
        )
        call.resolve(envelope)
    }

    @PluginMethod
    fun getConnection(call: PluginCall) {
        val addonId = call.getString("addonId").orEmpty()
        val companionId = call.getString("companionId").orEmpty()
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put("connection", JSONObject.NULL)
        call.resolve(envelope)
    }

    private fun buildStatusEnvelope(addonId: String, companionId: String, lastError: String?): JSObject {
        val envelope = JSObject()
        envelope.put("addonId", addonId)
        envelope.put("companionId", companionId)
        envelope.put(
            "status",
            JSObject().apply {
                put("state", "unsupported")
                put("available", false)
                put("enabled", false)
                put("lastError", lastError ?: JSONObject.NULL)
            }
        )
        return envelope
    }
}
