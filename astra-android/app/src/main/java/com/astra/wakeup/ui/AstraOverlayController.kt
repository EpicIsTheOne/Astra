package com.astra.wakeup.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

object AstraOverlayController {
    fun canDrawOverlays(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    fun overlayPermissionIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun startOverlay(context: Context) {
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
