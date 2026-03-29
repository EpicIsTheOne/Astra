package com.astra.wakeup.ui

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class AstraCallSession(
    val id: String,
    val state: String,
    val agent: String,
)

data class AstraCallStartResult(
    val ok: Boolean,
    val session: AstraCallSession? = null,
    val error: String? = null,
)

object AstraCallSessionClient {
    private val httpClient = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private fun commandCenterBase(apiUrl: String): String {
        val trimmed = apiUrl.trim().trimEnd('/')
        return when {
            trimmed.isBlank() -> ""
            trimmed.contains("/commandcenter") -> trimmed.substringBefore("/commandcenter") + "/commandcenter"
            trimmed.contains("/api/") -> trimmed.substringBefore("/api/") + "/commandcenter"
            else -> "$trimmed/commandcenter"
        }
    }

    private fun wsUrl(apiUrl: String): String {
        val base = commandCenterBase(apiUrl)
        return when {
            base.startsWith("https://") -> base.replaceFirst("https://", "wss://") + "/ws"
            base.startsWith("http://") -> base.replaceFirst("http://", "ws://") + "/ws"
            else -> "$base/ws"
        }
    }

    fun startCall(apiUrl: String, agent: String? = null): AstraCallStartResult {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank()) return AstraCallStartResult(false, error = "Missing API URL")
        return runCatching {
            val conn = URL("$base/api/call/start").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.outputStream.bufferedWriter().use { writer ->
                writer.write(JSONObject().apply {
                    if (!agent.isNullOrBlank()) put("agent", agent)
                }.toString())
            }
            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            val json = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (code !in 200..299) {
                return AstraCallStartResult(false, error = json.optString("error").ifBlank { "HTTP $code" })
            }
            val session = json.optJSONObject("session") ?: JSONObject()
            AstraCallStartResult(
                ok = true,
                session = AstraCallSession(
                    id = session.optString("id"),
                    state = session.optString("state").ifBlank { "ready" },
                    agent = session.optString("agent").ifBlank { agent ?: "orchestrator" },
                )
            )
        }.getOrElse {
            AstraCallStartResult(false, error = it.message ?: "network error")
        }
    }

    fun sendSessionEvent(apiUrl: String, sessionId: String, type: String, text: String? = null) {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank() || sessionId.isBlank()) return
        val body = JSONObject().apply {
            put("type", type)
            if (!text.isNullOrBlank()) put("text", text)
        }
        val request = Request.Builder()
            .url("$base/api/call/$sessionId/event")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
    }

    fun sendAudioChunk(apiUrl: String, sessionId: String, pcm16Base64: String, mimeType: String = "audio/pcm;rate=16000") {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank() || sessionId.isBlank() || pcm16Base64.isBlank()) return
        val body = JSONObject().apply {
            put("pcm16Base64", pcm16Base64)
            put("mimeType", mimeType)
        }
        val request = Request.Builder()
            .url("$base/api/call/$sessionId/audio")
            .post(body.toString().toRequestBody(jsonMediaType))
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
    }

    fun endCall(apiUrl: String, sessionId: String) {
        val base = commandCenterBase(apiUrl)
        if (base.isBlank() || sessionId.isBlank()) return
        val request = Request.Builder()
            .url("$base/api/call/$sessionId/end")
            .post("{}".toRequestBody(jsonMediaType))
            .build()
        runCatching { httpClient.newCall(request).execute().close() }
    }

    fun websocketUrl(apiUrl: String): String = wsUrl(apiUrl)
}
