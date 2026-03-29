package com.astra.wakeup.ui

import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class OpenClawBridgeStatus(
    val ok: Boolean,
    val connected: Boolean,
    val mode: String,
    val error: String? = null
)

data class OpenClawBridgeChatResult(
    val ok: Boolean,
    val reply: String? = null,
    val error: String? = null,
    val agent: String? = null
)

object OpenClawBridgeClient {
    private fun commandCenterBase(apiUrl: String): String {
        val trimmed = apiUrl.trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.contains("/commandcenter") -> trimmed.substringBefore("/commandcenter") + "/commandcenter"
            trimmed.contains("/api/") -> trimmed.substringBefore("/api/") + "/commandcenter"
            else -> "$trimmed/commandcenter"
        }
    }

    fun status(apiUrl: String): OpenClawBridgeStatus {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank()) return OpenClawBridgeStatus(false, false, "offline", "Missing API URL")
        return runCatching {
            val conn = URL("$base/api/status").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                return OpenClawBridgeStatus(false, false, "offline", "HTTP $code")
            }
            val json = JSONObject(body)
            val bridge = json.optJSONObject("bridge") ?: JSONObject()
            OpenClawBridgeStatus(
                ok = true,
                connected = bridge.optBoolean("connected", false),
                mode = bridge.optString("mode").ifBlank { "unknown" }
            )
        }.getOrElse {
            OpenClawBridgeStatus(false, false, "offline", it.message ?: "network error")
        }
    }

    fun directChat(apiUrl: String, text: String, agent: String? = null): OpenClawBridgeChatResult {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank() || text.isBlank()) return OpenClawBridgeChatResult(false, error = "Missing API URL or text")
        return runCatching {
            val conn = URL("$base/api/chat/direct").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Accept", "application/json")
            conn.doOutput = true
            val body = JSONObject().apply {
                put("text", text)
                if (!agent.isNullOrBlank()) put("agent", agent)
            }
            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val code = conn.responseCode
            val responseBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(responseBody) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                return OpenClawBridgeChatResult(false, error = json.optString("error").ifBlank { "HTTP $code" })
            }
            OpenClawBridgeChatResult(
                ok = json.optBoolean("ok", true),
                reply = null,
                error = json.optString("error").takeIf { it.isNotBlank() },
                agent = json.optString("agent").takeIf { it.isNotBlank() }
            )
        }.getOrElse {
            OpenClawBridgeChatResult(false, error = it.message ?: "network error")
        }
    }
}
