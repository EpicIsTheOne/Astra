package com.astra.wakeup.alarm

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object AlarmDiagnostics {
    fun notificationsEnabled(context: Context): Boolean {
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun wakeChannelImportance(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.getNotificationChannel("astra_wake_alarm")?.importance
    }

    fun wakeSessionChannelImportance(context: Context): Int? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val nm = context.getSystemService(NotificationManager::class.java)
        return nm.getNotificationChannel("astra_wake_session")?.importance
    }

    fun fullScreenIntentLikelyAllowed(context: Context): Boolean {
        return notificationsEnabled(context) && (wakeChannelImportance(context)?.let { it >= NotificationManager.IMPORTANCE_HIGH } ?: true)
    }

    fun appNotificationSettingsIntent(context: Context): Intent {
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
