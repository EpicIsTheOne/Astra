package com.astra.wakeup.ui

import org.json.JSONObject
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object WakeMessageClient {
    fun fetchLine(apiUrl: String, punishment: Boolean): String? {
        if (apiUrl.isBlank()) return null
        return runCatching {
            val conn = URL(ApiEndpoints.line(apiUrl)).openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 7000
            conn.readTimeout = 7000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true

            val body = JSONObject().apply {
                put("punishment", punishment)
                put("user", "Epic")
            }

            OutputStreamWriter(conn.outputStream).use { it.write(body.toString()) }
            val text = BufferedReader(conn.inputStream.reader()).use { it.readText() }
            JSONObject(text).optString("line").takeIf { it.isNotBlank() }
        }.getOrNull()
    }
}
