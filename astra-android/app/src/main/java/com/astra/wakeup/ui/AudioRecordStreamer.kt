package com.astra.wakeup.ui

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64

class AudioRecordStreamer(
    private val sampleRateHz: Int = 16_000,
    private val onChunk: (pcm16Base64: String) -> Unit,
    private val onError: (String) -> Unit,
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (running) return
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            onError("AudioRecord buffer init failed")
            return
        }
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRateHz,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            minBuffer * 2,
        )
        recorder = audioRecord
        running = true
        thread = Thread {
            try {
                audioRecord.startRecording()
                val buffer = ByteArray(minBuffer)
                while (running) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        val b64 = Base64.encodeToString(buffer.copyOf(read), Base64.NO_WRAP)
                        onChunk(b64)
                    }
                }
            } catch (e: Throwable) {
                onError(e.message ?: "Audio stream failed")
            } finally {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
            }
        }.apply {
            name = "astra-audio-streamer"
            start()
        }
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        recorder?.let { rec ->
            runCatching { rec.stop() }
            runCatching { rec.release() }
        }
        recorder = null
    }
}
