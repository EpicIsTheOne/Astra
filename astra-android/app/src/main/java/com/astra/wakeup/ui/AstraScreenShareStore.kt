package com.astra.wakeup.ui

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

object AstraScreenShareStore {
    private const val PREFS = "astra"
    private const val PREF_OVERLAY_CALL_SCREEN_SHARE = "overlay_call_screen_share_enabled"

    @Volatile
    private var projectionResultCode: Int? = null

    @Volatile
    private var projectionData: Intent? = null

    fun isOverlayCallScreenShareEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(PREF_OVERLAY_CALL_SCREEN_SHARE, false)

    fun setOverlayCallScreenShareEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_OVERLAY_CALL_SCREEN_SHARE, enabled)
            .apply()
        if (!enabled) {
            clearProjectionPermission()
        }
    }

    fun saveProjectionPermission(resultCode: Int, data: Intent?) {
        projectionResultCode = resultCode
        projectionData = data?.let { Intent(it) }
    }

    fun clearProjectionPermission() {
        projectionResultCode = null
        projectionData = null
    }

    fun hasProjectionPermission(): Boolean = projectionResultCode != null && projectionData != null

    fun createProjection(context: Context): MediaProjection? {
        val resultCode = projectionResultCode ?: return null
        val data = projectionData ?: return null
        val manager = context.getSystemService(MediaProjectionManager::class.java) ?: return null
        return runCatching { manager.getMediaProjection(resultCode, Intent(data)) }.getOrNull()
    }
}
