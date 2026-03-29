package com.astra.wakeup.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object AstraOverlayController {
    private const val PREFS_NAME = "astra"
    private const val PREF_OVERLAY_ENABLED = "overlay_enabled"
    private const val PREF_ORB_SIZE_PERCENT = "overlay_orb_size_percent"

    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun isOverlayEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(PREF_OVERLAY_ENABLED, true)

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_OVERLAY_ENABLED, enabled)
            .apply()
        if (!enabled) {
            stopOverlay(context)
        }
    }

    fun orbSizePercent(context: Context): Int =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getInt(PREF_ORB_SIZE_PERCENT, 50)
            .coerceIn(0, 100)

    fun setOrbSizePercent(context: Context, percent: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putInt(PREF_ORB_SIZE_PERCENT, percent.coerceIn(0, 100))
            .apply()
    }

    fun orbSizeDp(context: Context): Int {
        val percent = orbSizePercent(context)
        return (48 + ((88 - 48) * (percent / 100f))).toInt()
    }

    fun overlayPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun startOverlay(context: Context) {
        if (!isOverlayEnabled(context)) return
        ContextCompat.startForegroundService(context, Intent(context, AstraOverlayService::class.java))
    }

    fun stopOverlay(context: Context) {
        val stopIntent = Intent(context, AstraOverlayService::class.java).apply {
            action = AstraOverlayService.ACTION_STOP
        }
        runCatching { context.startService(stopIntent) }
        context.stopService(Intent(context, AstraOverlayService::class.java))
    }
}
