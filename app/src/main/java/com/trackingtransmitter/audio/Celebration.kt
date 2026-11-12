package com.trackingtransmitter.audio

import android.content.Context
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlin.concurrent.thread

private val FANFARE = listOf(1046.5 to 150, 1318.5 to 150, 1568.0 to 150, 2093.0 to 500) // C6 E6 G6 C7

/** Vibration pattern plus a synthesized rising arpeggio, played once. Audio failure is logged, vibration still happens. */
fun celebrate(context: Context) {
    context.getSystemService(VibratorManager::class.java).defaultVibrator
        .vibrate(VibrationEffect.createWaveform(longArrayOf(0, 300, 150, 300, 150, 600), -1))
    val pcm = FANFARE.flatMap { (hz, ms) -> sineTone(hz, ms).asList() }.toShortArray()
    thread(name = "celebration") {
        var track: AudioTrack? = null
        try {
            track = newToneTrack()
            track.play()
            track.write(pcm, 0, pcm.size)
            // write returns once the data is buffered; let the tail play out before releasing.
            Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 100)
        } catch (e: UnsupportedOperationException) {
            Log.w("Celebration", "Fanfare audio unavailable", e)
        } catch (e: IllegalStateException) {
            Log.w("Celebration", "Fanfare audio unavailable", e)
        } catch (e: IllegalArgumentException) {
            Log.w("Celebration", "Fanfare audio unavailable", e)
        } finally {
            track?.release()
        }
    }
}
