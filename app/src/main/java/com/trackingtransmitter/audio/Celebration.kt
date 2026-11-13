package com.trackingtransmitter.audio

import android.content.Context
import android.media.AudioTrack
import android.os.VibrationEffect
import android.os.VibratorManager
import android.util.Log
import kotlin.concurrent.thread

private val FANFARE = listOf(1046.5 to 150, 1318.5 to 150, 1568.0 to 150, 2093.0 to 500) // C6 E6 G6 C7

/** How long [celebrate] sounds, so the beeper can wait for it after a treasure that is not the last stop. */
val FANFARE_MS: Long = FANFARE.sumOf { it.second }.toLong()

/** "Ding-dong-ding": clearly different from the beeps and shorter than the fanfare. */
internal val WAYPOINT_CHIME = listOf(1568.0 to 120, 1174.7 to 120, 1568.0 to 260) // G6 D6 G6

/** How long [reachWaypoint] sounds, so the beeper can wait for it. */
val WAYPOINT_CHIME_MS: Long = WAYPOINT_CHIME.sumOf { it.second }.toLong()

/** Vibration pattern plus a synthesized rising arpeggio, played once. Audio failure is logged, vibration still happens. */
fun celebrate(context: Context) {
    vibrate(context, longArrayOf(0, 300, 150, 300, 150, 600))
    playOnce(FANFARE, "celebration")
}

/** A waypoint was reached: two short buzzes and a chime, played once. */
fun reachWaypoint(context: Context) {
    vibrate(context, longArrayOf(0, 150, 100, 150))
    playOnce(WAYPOINT_CHIME, "waypoint")
}

private fun vibrate(context: Context, pattern: LongArray) {
    context.getSystemService(VibratorManager::class.java).defaultVibrator
        .vibrate(VibrationEffect.createWaveform(pattern, -1))
}

private fun playOnce(notes: List<Pair<Double, Int>>, name: String) {
    val pcm = notes.flatMap { (hz, ms) -> sineTone(hz, ms).asList() }.toShortArray()
    thread(name = name) {
        var track: AudioTrack? = null
        try {
            track = newToneTrack()
            track.play()
            track.write(pcm, 0, pcm.size)
            // write returns once the data is buffered; let the tail play out before releasing.
            Thread.sleep(pcm.size * 1000L / SAMPLE_RATE + 100)
        } catch (e: UnsupportedOperationException) {
            Log.w("Celebration", "$name audio unavailable", e)
        } catch (e: IllegalStateException) {
            Log.w("Celebration", "$name audio unavailable", e)
        } catch (e: IllegalArgumentException) {
            Log.w("Celebration", "$name audio unavailable", e)
        } finally {
            track?.release()
        }
    }
}
