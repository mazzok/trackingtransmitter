package com.trackingtransmitter.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import com.trackingtransmitter.domain.BeaconSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

internal const val SAMPLE_RATE = 44_100

/** Sine tone as 16 bit mono PCM with linear fades at both ends against clicks. */
internal fun sineTone(frequencyHz: Double, durationMs: Int, fadeMs: Int = 5): ShortArray {
    val n = SAMPLE_RATE * durationMs / 1000
    val fade = (SAMPLE_RATE * fadeMs / 1000).coerceAtMost(n / 2).coerceAtLeast(1)
    return ShortArray(n) { i ->
        val env = minOf(1.0, i.toDouble() / fade, (n - 1 - i).toDouble() / fade)
        (sin(2 * PI * frequencyHz * i / SAMPLE_RATE) * env * Short.MAX_VALUE * 0.8).toInt().toShort()
    }
}

/** A streaming AudioTrack for [sineTone] output; caller must release it. */
internal fun newToneTrack(): AudioTrack = AudioTrack.Builder()
    .setAudioAttributes(
        AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build(),
    )
    .setAudioFormat(
        AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build(),
    )
    .setTransferMode(AudioTrack.MODE_STREAM)
    .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
    .setBufferSizeInBytes(
        AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            .coerceAtLeast(SAMPLE_RATE / 5 * 2),
    )
    .build()

/** Plays beeps at the pace of the latest [BeaconSignal]; the signal is re-read before every beep. */
class BeepPlayer {
    private var job: Job? = null

    @Synchronized
    fun start(signal: () -> BeaconSignal?) {
        stop()
        job = CoroutineScope(Dispatchers.Default).launch {
            var track: AudioTrack? = null
            try {
                track = newToneTrack()
                track.play()
                while (isActive) {
                    val s = readSignal(signal)
                    if (s == null) {
                        delay(IDLE_MS)
                        continue
                    }
                    val begin = System.nanoTime()
                    track.write(sineTone(s.frequencyHz, BEEP_MS), 0, SAMPLE_RATE * BEEP_MS / 1000)
                    // Re-read the signal while waiting so turning takes effect before the old interval ends.
                    while (isActive) {
                        val current = readSignal(signal) ?: break
                        if (beepDue((System.nanoTime() - begin) / 1_000_000, current.intervalMs)) break
                        delay(SLICE_MS)
                    }
                }
            } catch (e: UnsupportedOperationException) {
                Log.w(TAG, "Beep audio unavailable", e)
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Beep audio unavailable", e)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Beep audio unavailable", e)
            } finally {
                track?.release()
            }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val BEEP_MS = 60
        const val IDLE_MS = 100L
        const val SLICE_MS = 25L
        const val TAG = "BeepPlayer"
    }
}

/** The current signal, or null (skip this beep) when computing it fails, so a bad value cannot end the beep loop. */
internal fun readSignal(signal: () -> BeaconSignal?): BeaconSignal? = try {
    signal()
} catch (e: RuntimeException) {
    Log.w("BeepPlayer", "Beacon signal failed", e)
    null
}

/** True once the time since the beep began has reached the latest interval. */
internal fun beepDue(elapsedMs: Long, intervalMs: Int): Boolean = elapsedMs >= intervalMs
