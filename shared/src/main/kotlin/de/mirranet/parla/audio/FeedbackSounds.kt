package de.mirranet.parla.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The short feedback sounds of both apps, generated in code (no sound files): a soft pop when a word or an
 * answer is picked, a warm two-note chime for a correct answer, a small chime for a found pair, a gentle low
 * tone for a wrong answer and a rising four-note chime when a lesson is finished.
 *
 * To keep them soft: pure sine tones with only a hint of the octave, slow-ish attacks, a gentle low-pass filter,
 * a faint room echo and silence before/after each sound, so the audio line never stops in the middle of a wave
 * (that is what causes small clicks or crackles). Each sound is 16-bit signed little-endian mono PCM at
 * [SAMPLE_RATE] Hz.
 */
object FeedbackSounds {
    const val SAMPLE_RATE = 44_100

    /** A single note: frequency, start and length in seconds, volume, attack and decay speed, octave mix, glide. */
    private class Note(
        val freq: Double, val start: Double, val length: Double, val volume: Double,
        val decay: Double, val attack: Double = 0.008, val octave: Double = 0.0, val glideTo: Double? = null,
    )

    val tap: ByteArray by lazy {
        render(listOf(Note(700.0, 0.0, 0.08, 0.07, 45.0, attack = 0.003, glideTo = 560.0)), echo = false)
    }

    val correct: ByteArray by lazy {
        render(listOf(
            Note(659.25, 0.0, 0.5, 0.11, 6.0, octave = 0.12),     // E5
            Note(880.0, 0.085, 0.7, 0.12, 5.0, octave = 0.12),    // A5
        ))
    }

    val pair: ByteArray by lazy {
        render(listOf(Note(880.0, 0.0, 0.35, 0.08, 10.0, octave = 0.1)))  // A5
    }

    val wrong: ByteArray by lazy {
        render(listOf(
            Note(392.0, 0.0, 0.25, 0.09, 9.0, attack = 0.012, octave = 0.06),    // G4
            Note(329.63, 0.11, 0.35, 0.09, 8.0, attack = 0.012, octave = 0.06),  // E4
        ))
    }

    val lessonComplete: ByteArray by lazy {
        render(listOf(
            Note(523.25, 0.0, 0.45, 0.10, 6.0, octave = 0.12),    // C5
            Note(659.25, 0.11, 0.45, 0.10, 6.0, octave = 0.12),   // E5
            Note(783.99, 0.22, 0.5, 0.10, 5.5, octave = 0.12),    // G5
            Note(1046.5, 0.33, 1.0, 0.11, 3.5, octave = 0.10),    // C6
            // Soft chord underneath the last note
            Note(523.25, 0.33, 1.0, 0.035, 2.5, attack = 0.04),
            Note(659.25, 0.33, 1.0, 0.035, 2.5, attack = 0.04),
            Note(783.99, 0.33, 1.0, 0.035, 2.5, attack = 0.04),
        ))
    }

    private const val LEAD = 0.005   // seconds of silence before the sound
    private const val TAIL = 0.12    // seconds of silence after it (plus the echo)

    private fun render(notes: List<Note>, echo: Boolean = true): ByteArray {
        val rate = SAMPLE_RATE.toDouble()
        val lead = (LEAD * rate).toInt()
        val echo1 = (0.075 * rate).toInt()
        val echo2 = (0.15 * rate).toInt()
        val body = notes.maxOf { (it.start * rate).toInt() + (it.length * rate).toInt() }
        val n = lead + body + (if (echo) echo2 else 0) + (TAIL * rate).toInt()
        val dry = DoubleArray(n)
        for (note in notes) {
            val from = lead + (note.start * rate).toInt()
            val len = (note.length * rate).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val t = i / rate
                val f = note.glideTo?.let { note.freq + (it - note.freq) * (i.toDouble() / len) } ?: note.freq
                phase += 2 * PI * f / rate
                // Smooth attack, exponential decay and a 20 ms fade at the end, so no note ends abruptly.
                val attack = min(1.0, t / note.attack)
                val env = attack * attack * exp(-note.decay * t) * min(1.0, (len - i) / (rate * 0.02))
                val v = sin(phase) + note.octave * sin(2 * phase)
                dry[from + i] += v * env * note.volume
            }
        }
        // Faint room echo makes the chimes rounder.
        val wet = if (!echo) dry else DoubleArray(n) { i ->
            dry[i] + (if (i >= echo1) 0.18 * dry[i - echo1] else 0.0) + (if (i >= echo2) 0.07 * dry[i - echo2] else 0.0)
        }
        // Gentle one-pole low-pass (about 3.5 kHz) takes the edge off.
        val alpha = 1 - exp(-2 * PI * 3500 / rate)
        var y = 0.0
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            y += alpha * (wet[i] - y)
            val s = (y.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            out[2 * i] = (s and 0xff).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xff).toByte()
        }
        return out
    }
}
