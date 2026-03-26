package com.astra.wakeup.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.astra.wakeup.R
import com.astra.wakeup.alarm.AlarmScheduler
import java.util.Locale

class WakeActivity : AppCompatActivity(), TextToSpeech.OnInitListener {
    private var ringtone: Ringtone? = null
    private var tts: TextToSpeech? = null
    private var recognizer: SpeechRecognizer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var acknowledged = false
    private var ttsReady = false
    private var pendingSpeech: String? = null
    private var conversationMode = true
    private var pendingResumeListeningAfterTts = false
    private var isListening = false

    private val fallbackLines = listOf(
        "Epic. Wake up. I'm not doing this alone.",
        "Good morning, menace. Open your eyes and prove you're alive.",
        "Rise and shine, sleepy disaster. Yes, this is me being nice."
    )

    private val punishmentShots = listOf(
        "Nope. We're still doing this. Talk to me.",
        "You are not awake yet. Try again, gremlin.",
        "Cute attempt. Sit up and answer me."
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
        getSharedPreferences("astra", MODE_PRIVATE).edit().putLong("last_alarm_triggered_at", System.currentTimeMillis()).apply()

        playRandomSfx()
        loadDynamicLineAndSpeak(false)

        val prefs = getSharedPreferences("astra", MODE_PRIVATE)
        if (prefs.getBoolean("punish", true)) schedulePunishmentLoop()

        findViewById<Button>(R.id.btnTalk).setOnClickListener {
            conversationMode = true
            startListeningWithVAD(force = true)
        }

        findViewById<Button>(R.id.btnAwake).setOnClickListener {
            acknowledged = true
            getSharedPreferences("astra", MODE_PRIVATE).edit().putLong("last_alarm_dismissed_at", System.currentTimeMillis()).apply()
            stopAudio()
            finish()
        }

        findViewById<Button>(R.id.btnSnooze).setOnClickListener {
            acknowledged = true
            getSharedPreferences("astra", MODE_PRIVATE).edit().putLong("last_alarm_dismissed_at", System.currentTimeMillis()).apply()
            stopAudio()
            AlarmScheduler.scheduleSnooze(this, 10)
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(1.12f)
            tts?.setSpeechRate(1.0f)
            val femaleVoice = tts?.voices?.firstOrNull { v: Voice ->
                val n = v.name.lowercase(Locale.US)
                n.contains("female") || n.contains("fem") || n.contains("woman") || n.contains("girl")
            }
            if (femaleVoice != null) tts?.voice = femaleVoice
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {
                    maybeResumeListeningAfterTts()
                }
                override fun onDone(utteranceId: String?) {
                    maybeResumeListeningAfterTts()
                }
            })
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
        val astraFm = prefs.getBoolean("astra_fm", true)

        Thread {
            val fmScript = if (!punishment && astraFm) {
                WakeMessageClient.fetchFmResult(apiUrl)?.script
            } else null

            if (!fmScript.isNullOrBlank()) {
                runOnUiThread {
                    findViewById<TextView>(R.id.tvLine).text = fmScript
                    speak(fmScript, resumeConversation = true)
                }
                return@Thread
            }

            val result = WakeMessageClient.fetchLineResult(apiUrl, punishment)
            val line = result?.line ?: if (punishment) punishmentShots.random() else fallbackLines.random()
            val mission = result?.mission

            runOnUiThread {
                val display = if (!mission.isNullOrBlank() && !punishment) "$line\n\nMission: $mission" else line
                findViewById<TextView>(R.id.tvLine).text = display
                speak(line, resumeConversation = true)
            }
        }.start()
    }

    private fun startListeningWithVAD(force: Boolean = false) {
        if (acknowledged || (!force && (!conversationMode || isListening))) return

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 991)
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            speak("Speech recognition isn't available on this phone.", resumeConversation = false)
            return
        }

        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        val transcriptView = findViewById<TextView>(R.id.tvTranscript)
        transcriptView.text = "You: listening..."
        isListening = true

        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onError(error: Int) {
                isListening = false
                transcriptView.text = "You: (didn't catch that)"
                if (!acknowledged && conversationMode) {
                    handler.postDelayed({ startListeningWithVAD() }, 900)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onResults(results: Bundle?) {
                isListening = false
                val heard = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()

                if (heard.isBlank()) {
                    transcriptView.text = "You: ..."
                    if (!acknowledged && conversationMode) {
                        handler.postDelayed({ startListeningWithVAD() }, 700)
                    }
                    return
                }

                transcriptView.text = "You: $heard"
                respondToUserSpeech(heard)
            }
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 800L)
        }

        recognizer?.startListening(intent)
    }

    private fun respondToUserSpeech(userText: String) {
        val prefs = getSharedPreferences("astra", MODE_PRIVATE)
        val apiUrl = prefs.getString("api_url", "") ?: ""
        val lineView = findViewById<TextView>(R.id.tvLine)
        lineView.text = "Astra is thinking…"

        Thread {
            val reply = WakeChatClient.wakeReply(this, apiUrl, userText)
                ?: "Nice try. You're still waking up now. Sit up and answer me properly."

            runOnUiThread {
                lineView.text = reply
                speak(reply, resumeConversation = true)
            }
        }.start()
    }

    private fun speak(text: String, resumeConversation: Boolean) {
        pendingResumeListeningAfterTts = resumeConversation && conversationMode && !acknowledged
        if (!ttsReady) {
            pendingSpeech = text
            return
        }
        speakNow(text)
    }

    private fun speakNow(text: String) {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "wake-line")
    }

    private fun maybeResumeListeningAfterTts() {
        runOnUiThread {
            if (acknowledged || !pendingResumeListeningAfterTts) return@runOnUiThread
            pendingResumeListeningAfterTts = false
            handler.postDelayed({ startListeningWithVAD() }, 450)
        }
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

        val vibePattern = longArrayOf(0, 400, 150, 600)
        (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.let { v ->
            v.vibrate(VibrationEffect.createWaveform(vibePattern, -1))
        }
    }

    private fun schedulePunishmentLoop() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (acknowledged) return
                playRandomSfx()
                if (!isListening) {
                    loadDynamicLineAndSpeak(true)
                }
                handler.postDelayed(this, 20_000)
            }
        }, 20_000)
    }

    private fun stopAudio() {
        handler.removeCallbacksAndMessages(null)
        recognizer?.destroy()
        recognizer = null
        isListening = false
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
