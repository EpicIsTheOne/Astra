package com.astra.wakeup.ui

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.astra.wakeup.R
import com.astra.wakeup.alarm.AlarmScheduler
import java.util.Locale
import kotlin.random.Random

class WakeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var ringtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private val handler = Handler(Looper.getMainLooper())
    private var acknowledged = false

    private val fallbackLines = listOf(
        "Wake up, Epic. Move now.",
        "Wake up, dumbass. Right now.",
        "Wake up~ now, sleepy chaos goblin."
    )

    private val punishmentShots = listOf(
        "Wake up dumbass.",
        "Wake up~",
        "Now.",
        "Up. Now."
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

        tts = TextToSpeech(this, this)

        playRandomSfx()
        loadDynamicLineAndSpeak(false)

        val prefs = getSharedPreferences("astra", MODE_PRIVATE)
        if (prefs.getBoolean("punish", true)) {
            schedulePunishmentLoop()
        }

        findViewById<Button>(R.id.btnAwake).setOnClickListener {
            acknowledged = true
            stopAudio()
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            acknowledged = true
            stopAudio()
            AlarmScheduler.scheduleSnooze(this, 10)
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.2f)
            tts?.setSpeechRate(1.0f)
        }
    }

    private fun loadDynamicLineAndSpeak(punishment: Boolean) {
        val prefs = getSharedPreferences("astra", MODE_PRIVATE)
        val apiUrl = prefs.getString("api_url", "") ?: ""

        Thread {
            val line = WakeMessageClient.fetchLine(apiUrl, punishment)
                ?: if (punishment) punishmentShots.random() else fallbackLines.random()

            runOnUiThread {
                findViewById<TextView>(R.id.tvLine).text = line
                speak(line)
            }
        }.start()
    }

    private fun speak(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "wake-line")
    }

    private fun playRandomSfx() {
        val prefs = getSharedPreferences("astra", MODE_PRIVATE)
        if (!prefs.getBoolean("random_sfx", true)) return

        val type = listOf(
            RingtoneManager.TYPE_ALARM,
            RingtoneManager.TYPE_NOTIFICATION,
            RingtoneManager.TYPE_RINGTONE
        ).random()

        val soundUri = RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
        ringtone = RingtoneManager.getRingtone(this, soundUri)
        ringtone?.streamType = AudioManager.STREAM_ALARM
        ringtone?.play()

        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.vibrate(longArrayOf(0, 400, 150, 600), -1)
    }

    private fun schedulePunishmentLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (acknowledged) return
                playRandomSfx()
                loadDynamicLineAndSpeak(true)
                handler.postDelayed(this, 20_000)
            }
        }, 20_000)
    }

    private fun stopAudio() {
        handler.removeCallbacksAndMessages(null)
        ringtone?.stop()
        ringtone = null
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    override fun onDestroy() {
        stopAudio()
        super.onDestroy()
    }
}
