package com.astra.wakeup.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Base64
import java.util.concurrent.LinkedBlockingQueue

data class AudioChunk(
    val bytes: ByteArray,
    val sampleRateHz: Int,
)

class AudioPlaybackQueue(
    private val defaultSampleRateHz: Int = 24_000,
    private val onError: (String) -> Unit,
) {
    private val queue = LinkedBlockingQueue<AudioChunk>()
    @Volatile private var running = false
    private var worker: Thread? = null
    private var audioTrack: AudioTrack? = null
    @Volatile private var activeSampleRateHz: Int = defaultSampleRateHz

    fun start() {
        if (running) return
        running = true
        worker = Thread {
            try {
                ensureAudioTrack(activeSampleRateHz)
                audioTrack?.play()
                while (running) {
                    val chunk = queue.take()
                    if (chunk.sampleRateHz != activeSampleRateHz) {
                        ensureAudioTrack(chunk.sampleRateHz)
                        audioTrack?.play()
                    }
                    if (chunk.bytes.isNotEmpty()) {
                        audioTrack?.write(chunk.bytes, 0, chunk.bytes.size)
                    }
                }
            } catch (e: Throwable) {
                if (running) onError(e.message ?: "Audio playback failed")
            } finally {
                releaseTrack()
            }
        }.apply {
            name = "astra-audio-playback"
            start()
        }
    }

    fun enqueuePcm16Base64(base64: String, mimeType: String? = null) {
        if (!running) return
        val decoded = runCatching { Base64.decode(base64, Base64.DEFAULT) }.getOrNull() ?: return
        val sampleRate = parseSampleRateFromMimeType(mimeType) ?: defaultSampleRateHz
        queue.offer(AudioChunk(decoded, sampleRate))
    }

    fun stop() {
        running = false
        worker?.interrupt()
        worker = null
        queue.clear()
        releaseTrack()
    }

    private fun ensureAudioTrack(sampleRateHz: Int) {
        if (audioTrack != null && sampleRateHz == activeSampleRateHz) return
        releaseTrack()
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRateHz,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioTrack init failed for $sampleRateHz Hz")
        }
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRateHz)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        activeSampleRateHz = sampleRateHz
    }

    private fun releaseTrack() {
        runCatching { audioTrack?.stop() }
        runCatching { audioTrack?.release() }
        audioTrack = null
    }

    private fun parseSampleRateFromMimeType(mimeType: String?): Int? {
        val value = mimeType?.lowercase()?.trim() ?: return null
        val marker = "rate="
        val index = value.indexOf(marker)
        if (index == -1) return null
        val tail = value.substring(index + marker.length)
        val raw = tail.takeWhile { it.isDigit() }
        return raw.toIntOrNull()?.coerceIn(8000, 48000)
    }
}
