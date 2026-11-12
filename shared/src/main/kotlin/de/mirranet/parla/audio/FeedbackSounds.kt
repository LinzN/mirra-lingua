package de.mirranet.parla.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * The short feedback sounds of both apps, generated in code (no sound files): a soft tick when a word or an
 * answer is picked, a two-note bell chime for a correct answer, a small chime for a found pair and a gentle low
 * tone for a wrong answer. Each is 16-bit signed little-endian mono PCM at [SAMPLE_RATE] Hz.
 */
object FeedbackSounds {
    const val SAMPLE_RATE = 44_100

    /** A single note: frequency, start and length in seconds, volume, decay speed and overtone mix. */
    private class Note(
        val freq: Double, val start: Double, val length: Double, val volume: Double,
        val decay: Double, val overtones: List<Pair<Double, Double>> = emptyList(), val glideTo: Double? = null,
    )

    private val bell = listOf(2.0 to 0.25, 3.0 to 0.08)
    private val soft = listOf(2.0 to 0.15)

    val tap: ByteArray by lazy { render(listOf(Note(1250.0, 0.0, 0.05, 0.10, 70.0, glideTo = 900.0))) }

    val correct: ByteArray by lazy {
        render(listOf(
            Note(784.0, 0.0, 0.45, 0.16, 7.0, bell),     // G5
            Note(1174.66, 0.09, 0.6, 0.17, 6.0, bell),   // D6
        ))
    }

    val pair: ByteArray by lazy { render(listOf(Note(1046.5, 0.0, 0.3, 0.10, 11.0, listOf(2.0 to 0.2)))) } // C6

    val wrong: ByteArray by lazy {
        render(listOf(
            Note(330.0, 0.0, 0.22, 0.15, 9.0, soft),     // E4
            Note(262.0, 0.12, 0.32, 0.15, 8.0, soft),    // C4
        ))
    }

    private fun render(notes: List<Note>): ByteArray {
        val rate = SAMPLE_RATE.toDouble()
        val n = notes.maxOf { (it.start * rate).toInt() + (it.length * rate).toInt() }
        val mix = DoubleArray(n)
        for (note in notes) {
            val from = (note.start * rate).toInt()
            val len = (note.length * rate).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val t = i / rate
                val f = note.glideTo?.let { note.freq + (it - note.freq) * (i.toDouble() / len) } ?: note.freq
                phase += 2 * PI * f / rate
                // Quick 5 ms attack, exponential decay and a short fade at the end avoid clicks.
                val env = min(1.0, t / 0.005) * exp(-note.decay * t) * min(1.0, (len - i) / (rate * 0.01))
                var v = sin(phase)
                for ((mult, gain) in note.overtones) v += gain * sin(phase * mult)
                mix[from + i] += v * env * note.volume
            }
        }
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            val s = (mix[i].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
            out[2 * i] = (s and 0xff).toByte()
            out[2 * i + 1] = ((s shr 8) and 0xff).toByte()
        }
        return out
    }
}
