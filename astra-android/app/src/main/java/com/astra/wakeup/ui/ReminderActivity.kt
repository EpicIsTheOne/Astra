package com.astra.wakeup.ui

import android.Manifest
import android.app.KeyguardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.astra.wakeup.R
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

class ReminderActivity : AppCompatActivity() {
    private var recognizer: SpeechRecognizer? = null
    private var isListening = false
    private var resolutionHandled = false
    private lateinit var phoneControl: PhoneControlExecutor
    private var reminder: ReminderItem? = null
    private val sessionKey = "reminder-${UUID.randomUUID()}"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reminder)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        getSystemService(KeyguardManager::class.java)?.let { keyguard ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && keyguard.isKeyguardLocked) keyguard.requestDismissKeyguard(this, null)
        }
        phoneControl = PhoneControlExecutor(this)
        reminder = ReminderRepository.getReminder(this, intent.getStringExtra("reminder_id").orEmpty())
        renderReminder()
        speakReminderIntro()

        findViewById<Button>(R.id.btnReminderTalk).setOnClickListener { startListening() }
        findViewById<Button>(R.id.btnRemindLater).setOnClickListener { remindLater() }
        findViewById<Button>(R.id.btnDone).setOnClickListener { markDone() }
    }

    private fun renderReminder() {
        val item = reminder ?: return finish()
        findViewById<TextView>(R.id.tvReminderTitle).text = item.title
        findViewById<TextView>(R.id.tvReminderMeta).text = buildString {
            append("${importanceLabel(item.importance)} · ${annoyanceLabel(item.annoyanceLevel)}")
            append(" · ${if (item.followUpState.contains("verification")) "Verification check" else "Scheduled reminder"}")
            append("\nDue ${formatTimestamp(item.scheduledTimeMillis)}")
            item.linkedTaskId?.let { linked ->
                ReminderRepository.getTask(this@ReminderActivity, linked)?.let { append("\nTask: ${it.title}") }
            }
        }
        findViewById<Button>(R.id.btnRemindLater).text = "Remind me later"
    }

    private fun speakReminderIntro() {
        val item = reminder ?: return
        val linkedTask = item.linkedTaskId?.let { ReminderRepository.getTask(this, it) }
        val line = if (item.followUpState.contains("verification")) {
            "Alright, status check. Did you actually do ${linkedTask?.title ?: item.title}, or were you just running your mouth?"
        } else {
            "Hey. Reminder time. ${item.title}. If you're not doing it right now, explain yourself."
        }
        findViewById<TextView>(R.id.tvReminderLine).text = line
        phoneControl.execute("phone.tts.speak", JSONObject().put("text", line).put("volume", 0.85))
    }

    private fun remindLater() {
        val item = reminder ?: return
        val nextTime = ReminderScheduler.computeLaterTime(item)
        val updated = item.copy(scheduledTimeMillis = nextTime, snoozeCount = item.snoozeCount + 1, followUpState = "snoozed", enabled = true)
        ReminderRepository.upsertReminder(this, updated)
        resolutionHandled = true
        finishReminderSession()
        finish()
    }

    private fun markDone() {
        val item = reminder ?: return
        val followUp = ReminderScheduler.createVerificationFollowUp(item)
        ReminderRepository.upsertReminder(this, followUp)
        resolutionHandled = true
        findViewById<TextView>(R.id.tvReminderLine).text = "Fine. I'll check back in a few minutes and you'd better have actually done it."
        phoneControl.execute(
            "phone.tts.speak",
            JSONObject().put("text", "Fine. I'll check back in a few minutes and you'd better have actually done it.").put("volume", 0.88)
        )
        finishReminderSession()
        finish()
    }

    private fun finishReminderSession() {
        ReminderNotifier.clear(this)
        ReminderForegroundService.stop(this)
        runCatching { phoneControl.execute("phone.audio.stop", JSONObject()) }
    }

    private fun startListening() {
        if (isListening) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 441)
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        isListening = true
        findViewById<TextView>(R.id.tvReminderTranscript).text = "You: listening..."
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
            override fun onError(error: Int) { isListening = false }
            override fun onResults(results: Bundle?) {
                isListening = false
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                findViewById<TextView>(R.id.tvReminderTranscript).text = "You: $heard"
                replyToSpeech(heard)
            }
        })
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US.toLanguageTag())
        })
    }

    private fun replyToSpeech(userText: String) {
        val item = reminder ?: return
        val prompt = "You are Astra responding to a live Android reminder screen. The reminder is ${JSONObject.quote(item.title)}. User said ${JSONObject.quote(userText)}. Reply with one concise sentence only, no markdown."
        Thread {
            val reply = runCatching { WakeChatClient.wakeReply(this, getSharedPreferences("astra", MODE_PRIVATE).getString("api_url", "") ?: "", prompt, sessionKey) }.getOrDefault("Handle the thing, then tap the button, menace.")
            runOnUiThread {
                findViewById<TextView>(R.id.tvReminderLine).text = reply
                phoneControl.execute("phone.tts.speak", JSONObject().put("text", reply).put("volume", 0.88))
            }
        }.start()
    }

    override fun onDestroy() {
        recognizer?.destroy()
        if (resolutionHandled) {
            ReminderNotifier.clear(this)
        }
        phoneControl.release()
        super.onDestroy()
    }
}
