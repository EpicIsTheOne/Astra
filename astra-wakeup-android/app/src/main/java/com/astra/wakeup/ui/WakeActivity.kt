package com.astra.wakeup.ui

import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astra.wakeup.R
import com.astra.wakeup.alarm.AlarmScheduler
import kotlin.random.Random

class WakeActivity : AppCompatActivity() {
    private var ringtone: Ringtone? = null

    private val lines = listOf(
        "Wake up, Epic. The world won’t carry your lazy butt.",
        "Morning, chaos goblin. Move before punishment mode starts.",
        "Get up, sleepy legend. You asked for this."
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wake)

        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        val line = lines[Random.nextInt(lines.size)]
        findViewById<TextView>(R.id.tvLine).text = line

        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        ringtone = RingtoneManager.getRingtone(this, alarmUri)
        ringtone?.play()

        findViewById<Button>(R.id.btnAwake).setOnClickListener {
            stopAudio()
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            stopAudio()
            AlarmScheduler.scheduleSnooze(this, 10)
            finish()
        }
    }

    private fun stopAudio() {
        ringtone?.stop()
        ringtone = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
