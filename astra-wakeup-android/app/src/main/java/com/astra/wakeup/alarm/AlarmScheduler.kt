package com.astra.wakeup.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmScheduler {
    private const val REQ = 550

    fun scheduleDaily(context: Context, hour: Int = 5, minute: Int = 50) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = wakeIntent(context)

        val zone = ZoneId.of("America/New_York")
        var next = ZonedDateTime.now(zone)
            .withHour(hour)
            .withMinute(minute)
            .withSecond(0)
            .withNano(0)
        if (next.isBefore(ZonedDateTime.now(zone))) next = next.plusDays(1)

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            next.toInstant().toEpochMilli(),
            pending
        )
    }

    fun scheduleFromPrefs(context: Context) {
        val prefs = context.getSharedPreferences("astra", Context.MODE_PRIVATE)
        val hour = prefs.getInt("wake_hour", 5)
        val minute = prefs.getInt("wake_minute", 50)
        scheduleDaily(context, hour, minute)
    }

    fun scheduleSnooze(context: Context, minutes: Int = 10) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pending = wakeIntent(context)
        val at = System.currentTimeMillis() + minutes * 60_000L
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
    }

    private fun wakeIntent(context: Context): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQ,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
