package com.astra.wakeup.ui

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApiStatusClient {
    fun check(apiUrl: String): Pair<Boolean, String> {
        if (apiUrl.isBlank()) return false to "No API URL"
        val base = apiUrl.substringBefore("/api/wakeup/line")
        val healthUrl = if (base == apiUrl) "$apiUrl" else "$base/api/health"

        return runCatching {
            val conn = URL(healthUrl).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.setRequestProperty("Accept", "application/json")
            val code = conn.responseCode
            if (code !in 200..299) return false to "HTTP $code"
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val ok = runCatching { JSONObject(body).optBoolean("ok", false) }.getOrDefault(true)
            if (ok) true to "Connected" else false to "Health check failed"
        }.getOrElse { false to (it.message ?: "Network error") }
    }
}
