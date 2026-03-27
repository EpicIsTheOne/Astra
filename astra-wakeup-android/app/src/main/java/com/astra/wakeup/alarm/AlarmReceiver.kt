package com.astra.wakeup.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.astra.wakeup.ui.WakeActivity

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.getSharedPreferences("astra", Context.MODE_PRIVATE)
            .edit()
            .putLong("last_alarm_receiver_fired_at", System.currentTimeMillis())
            .apply()

        val wakeIntent = Intent(context, WakeActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(wakeIntent)

        AlarmScheduler.scheduleFromPrefs(context)
    }
}
