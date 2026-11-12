package de.mirranet.parla.desktop

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.ByteArrayInputStream
import java.util.Base64
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.Clip
import javax.sound.sampled.LineEvent
import javax.sound.sampled.SourceDataLine
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Text-to-speech using what the operating system already has:
 * Windows: a long-running PowerShell helper (resource speech.ps1) that uses the modern Windows voices
 * (Settings > Time & Language > Speech) and falls back to the older SAPI voices; it returns the audio as
 * WAV and the app plays it, so it can be stopped at any time.
 * Linux: speech-dispatcher (spd-say), espeak-ng or espeak.
 * [available] is true once a voice for the current language was found.
 */
class Speech {

    @Volatile var available = false
        private set

    /** True once the voices have been looked up (starting the Windows helper takes a moment). */
    @Volatile var ready = false
        private set

    @Volatile var language = "en-GB"
        private set
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "speech").apply { isDaemon = true } }

    /** Bumped by every speak/stop; queued requests that are no longer the latest are dropped. */
    private val generation = AtomicInteger()

    // Windows: the helper process and the voices it reported.
    private var psProcess: Process? = null
    private var psInput: BufferedWriter? = null
    private var psOutput: BufferedReader? = null
    private var windowsLanguages: List<String> = emptyList()
    private var restarts = 0
    private val cache = object : LinkedHashMap<String, ByteArray>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > 200
    }
    @Volatile private var clip: Clip? = null

    // Linux: the command to use and the process currently speaking.
    private var linuxTool: String? = null
    private var current: Process? = null

    init {
        worker.execute {
            try {
                if (DesktopPlatform.isWindows) startWindows() else linuxTool = findLinuxTool()
            } catch (e: Exception) {
                StderrLogger.warn("Text-to-speech unavailable", e)
            }
            updateAvailability()
            ready = true
        }
    }

    fun setLanguage(tag: String) {
        language = tag
        worker.execute { updateAvailability() }
    }

    private fun updateAvailability() {
        val base = language.substringBefore('-').lowercase(Locale.ROOT)
        available = when {
            DesktopPlatform.isWindows -> windowsLanguages.any { it.lowercase(Locale.ROOT).substringBefore('-') == base }
            else -> linuxTool != null
        }
    }

    fun speak(text: String, slow: Boolean = false) {
        if (!available || text.isBlank()) return
        val clean = text.replace('\n', ' ').trim()
        val gen = generation.incrementAndGet()
        val tag = language
        worker.execute {
            if (gen != generation.get()) return@execute
            try {
                if (DesktopPlatform.isWindows) speakWindows(clean, tag, slow, gen) else speakLinux(clean, tag, slow)
            } catch (e: Exception) {
                StderrLogger.warn("Speaking failed", e)
            }
        }
    }

    fun stop() {
        generation.incrementAndGet()
        stopClip()
        worker.execute { current?.destroy() }
    }

    fun release() {
        generation.incrementAndGet()
        stopClip()
        runCatching { psInput?.write("EXIT\n"); psInput?.flush() }
        psProcess?.destroy()
        current?.destroy()
        worker.shutdownNow()
    }

    // ------------------------------------------------------------------ Windows

    private fun startWindows() {
        val script = Speech::class.java.getResourceAsStream("/speech.ps1")!!.use { it.readBytes().toString(Charsets.UTF_8) }
        val encoded = Base64.getEncoder().encodeToString(script.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder(
            "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden", "-EncodedCommand", encoded,
        ).redirectErrorStream(true).start()
        psProcess = p
        psInput = p.outputStream.bufferedWriter(Charsets.US_ASCII)
        psOutput = p.inputStream.bufferedReader(Charsets.UTF_8)
        // Skip anything PowerShell prints before the helper reports its voices.
        var line: String?
        do { line = psOutput!!.readLine() } while (line != null && !line.startsWith("VOICES"))
        windowsLanguages = line.orEmpty().removePrefix("VOICES").split(',').map { it.trim() }.filter { it.isNotEmpty() }
        System.err.println("[Parla] Windows voices: ${windowsLanguages.joinToString().ifEmpty { "none" }}")
    }

    private fun speakWindows(text: String, tag: String, slow: Boolean, gen: Int) {
        val key = "$tag|$slow|$text"
        val wav = synchronized(cache) { cache[key] } ?: requestWav(tag, slow, text)?.also { synchronized(cache) { cache[key] = it } }
        if (wav == null || gen != generation.get()) return
        play(wav)
    }

    /** Asks the helper for the audio of [text]; restarts the helper once if it died. */
    private fun requestWav(tag: String, slow: Boolean, text: String): ByteArray? {
        if (psProcess?.isAlive != true) {
            if (restarts++ >= 2) return null
            startWindows()
        }
        val w = psInput ?: return null
        val r = psOutput ?: return null
        val b64 = Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))
        w.write("$tag|${if (slow) 1 else 0}|$b64\n")
        w.flush()
        while (true) {
            val line = r.readLine() ?: return null
            when {
                line.startsWith("WAV ") -> return Base64.getDecoder().decode(line.substring(4).trim())
                line == "NOVOICE" -> return null
                line.startsWith("ERR") -> { StderrLogger.warn("Windows speech: ${line.removePrefix("ERR").trim()}", null); return null }
            }
        }
    }

    private fun play(wav: ByteArray) {
        stopClip()
        val stream = AudioSystem.getAudioInputStream(ByteArrayInputStream(wav))
        val c = AudioSystem.getClip()
        c.addLineListener { if (it.type == LineEvent.Type.STOP) c.close() }
        c.open(stream)
        clip = c
        c.start()
    }

    private fun stopClip() {
        clip?.let { runCatching { it.stop(); it.close() } }
        clip = null
    }

    // ------------------------------------------------------------------ Linux

    private fun findLinuxTool(): String? =
        listOf("spd-say", "espeak-ng", "espeak").firstOrNull { tool ->
            System.getenv("PATH").orEmpty().split(':').any { java.io.File(it, tool).canExecute() }
        }

    private fun speakLinux(text: String, tag: String, slow: Boolean) {
        val tool = linuxTool ?: return
        val base = tag.substringBefore('-')
        current?.destroy()
        val cmd = when (tool) {
            "spd-say" -> listOf("spd-say", "-w", "-C", "-l", base, "-r", if (slow) "-50" else "-10", text)
            else -> listOf(tool, "-v", base, "-s", if (slow) "110" else "150", text)
        }
        current = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    }
}

/**
 * Short, soft feedback sounds generated on the fly (no sound files needed): a gentle tick when a word or option
 * is picked, a bright two-note chime for a correct answer, a small chime for a found pair and a soft, low
 * "hm" for a wrong answer. Played through one line that stays open, so the tick has no noticeable delay.
 */
object Tones {
    private const val RATE = 44_100f
    private val player = Executors.newSingleThreadExecutor { Thread(it, "tones").apply { isDaemon = true } }
    private val FORMAT = AudioFormat(RATE, 16, 1, true, false)
    private var line: SourceDataLine? = null

    /** A single note: frequency, start and length in seconds, volume, decay speed and overtone mix. */
    private class Note(
        val freq: Double, val start: Double, val length: Double, val volume: Double,
        val decay: Double, val overtones: List<Pair<Double, Double>> = emptyList(), val glideTo: Double? = null,
    )

    // Pre-rendered, so playing is just a write.
    private val tapSound by lazy {
        render(listOf(Note(1250.0, 0.0, 0.05, 0.10, 70.0, glideTo = 900.0)))
    }
    private val correctSound by lazy {
        val bell = listOf(2.0 to 0.25, 3.0 to 0.08)
        render(listOf(
            Note(784.0, 0.0, 0.45, 0.16, 7.0, bell),     // G5
            Note(1174.66, 0.09, 0.6, 0.17, 6.0, bell),   // D6
        ))
    }
    private val pairSound by lazy {
        render(listOf(Note(1046.5, 0.0, 0.3, 0.10, 11.0, listOf(2.0 to 0.2))))  // C6
    }
    private val wrongSound by lazy {
        val soft = listOf(2.0 to 0.15)
        render(listOf(
            Note(330.0, 0.0, 0.22, 0.15, 9.0, soft),     // E4
            Note(262.0, 0.12, 0.32, 0.15, 8.0, soft),    // C4
        ))
    }

    fun tap() = play(tapSound)
    fun correct() = play(correctSound)
    fun pair() = play(pairSound)
    fun wrong() = play(wrongSound)

    private fun play(pcm: ByteArray) {
        player.execute {
            runCatching {
                val l = line ?: AudioSystem.getSourceDataLine(FORMAT).also {
                    it.open(FORMAT, (RATE * 0.05).toInt() * 2)  // small buffer = low latency
                    it.start()
                    line = it
                }
                l.write(pcm, 0, pcm.size)
            }.onFailure { line = null }
        }
    }

    private fun render(notes: List<Note>): ByteArray {
        val n = notes.maxOf { (it.start * RATE).toInt() + (it.length * RATE).toInt() }
        val mix = DoubleArray(n)
        for (note in notes) {
            val from = (note.start * RATE).toInt()
            val len = (note.length * RATE).toInt()
            var phase = 0.0
            for (i in 0 until len) {
                val t = i / RATE.toDouble()
                val f = note.glideTo?.let { note.freq + (it - note.freq) * (i.toDouble() / len) } ?: note.freq
                phase += 2 * PI * f / RATE
                // Quick 5 ms attack, exponential decay and a short fade at the end avoid clicks.
                val env = min(1.0, t / 0.005) * exp(-note.decay * t) * min(1.0, (len - i) / (RATE * 0.01))
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

/** Same surface as the Android Audio class, so the shared screens can use either. */
class Audio {
    private val speech = Speech()
    val available: Boolean get() = speech.available
    fun setLanguage(tag: String) = speech.setLanguage(tag)

    /** Called once per language when something should be spoken but no voice is installed for it. */
    var onMissingVoice: (() -> Unit)? = null
    private val noticeShown = HashSet<String>()

    fun speak(text: String, slow: Boolean = false) {
        if (!speech.available) {
            if (speech.ready && noticeShown.add(speech.language)) onMissingVoice?.invoke()
            return
        }
        speech.speak(text, slow)
    }
    fun stop() = speech.stop()
    fun feedback(correct: Boolean, enabled: Boolean) {
        if (enabled) if (correct) Tones.correct() else Tones.wrong()
    }
    /** Soft tick when a word chip or an answer option is picked. */
    fun tap(enabled: Boolean) {
        if (enabled) Tones.tap()
    }
    /** Small chime when a matching pair was found. */
    fun pairFound(enabled: Boolean) {
        if (enabled) Tones.pair()
    }
    fun release() = speech.release()
}
