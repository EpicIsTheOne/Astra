package com.astra.wakeup.ui

import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
    private var ttsReady = false
    private var pendingSpeech: String? = null

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
            tts?.setPitch(1.15f)
            tts?.setSpeechRate(1.02f)
            val femaleVoice = tts?.voices?.firstOrNull { v: Voice ->
                val n = v.name.lowercase(Locale.US)
                n.contains("female") || n.contains("fem") || n.contains("woman") || n.contains("girl")
            }
            if (femaleVoice != null) tts?.voice = femaleVoice
            ttsReady = true
            pendingSpeech?.let {
                pendingSpeech = null
                speakNow(it)
            }
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
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val params = android.os.Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "wake-line")
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

        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.let { v ->
            v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 400, 150, 600), -1))
        }
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
