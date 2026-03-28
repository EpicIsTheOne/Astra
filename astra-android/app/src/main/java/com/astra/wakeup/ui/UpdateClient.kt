package com.astra.wakeup.ui

import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

object UpdateClient {
    private const val RELEASES_URL = "https://api.github.com/repos/EpicIsTheOne/Astra/releases?per_page=20"

    data class ReleaseAsset(
        val tagName: String,
        val releaseName: String,
        val prerelease: Boolean,
        val htmlUrl: String,
        val downloadUrl: String,
        val publishedAt: String,
        val assetName: String
    )

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun fetchLatestSignedRelease(): Result<ReleaseAsset> = runCatching {
        val request = Request.Builder()
            .url(RELEASES_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("GitHub release lookup failed (${response.code})")
            val body = response.body?.string().orEmpty()
            val releases = JSONArray(body)
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val assets = release.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val asset = assets.getJSONObject(j)
                    if (asset.optString("name") == "app-release.apk") {
                        return@runCatching ReleaseAsset(
                            tagName = release.optString("tag_name"),
                            releaseName = release.optString("name"),
                            prerelease = release.optBoolean("prerelease", false),
                            htmlUrl = release.optString("html_url"),
                            downloadUrl = asset.optString("browser_download_url"),
                            publishedAt = release.optString("published_at"),
                            assetName = asset.optString("name")
                        )
                    }
                }
            }
            error("No signed APK release found")
        }
    }

    fun isNewerRelease(currentVersionName: String, releaseTag: String): Boolean {
        val current = normalizeVersion(currentVersionName)
        val remote = normalizeVersion(releaseTag)
        if (current != null && remote != null) {
            return remote > current
        }
        return releaseTag.trim() != currentVersionName.trim()
    }

    private fun normalizeVersion(raw: String): List<Int>? {
        val cleaned = raw.trim().removePrefix("v")
        val match = Regex("^(\\d+(?:\\.\\d+)*)").find(cleaned)?.groupValues?.get(1) ?: return null
        return match.split('.').mapNotNull { it.toIntOrNull() }
    }

    private operator fun List<Int>.compareTo(other: List<Int>): Int {
        val max = maxOf(size, other.size)
        for (i in 0 until max) {
            val a = getOrElse(i) { 0 }
            val b = other.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}
