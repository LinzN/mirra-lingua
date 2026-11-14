package de.mirranet.parla.desktop

import de.mirranet.parla.audio.FeedbackSounds
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
            "spd-say" -> listOf("spd-say", "-w", "-C", "-l", base, "-r", if (slow) "-60" else "-10", text)
            else -> listOf(tool, "-v", base, "-s", if (slow) "90" else "150", text)
        }
        current = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    }
}

/** Plays the feedback sounds (see [FeedbackSounds]) through one line that stays open, so the tick has no delay. */
object Tones {
    private val player = Executors.newSingleThreadExecutor { Thread(it, "tones").apply { isDaemon = true } }
    private val format = AudioFormat(FeedbackSounds.SAMPLE_RATE.toFloat(), 16, 1, true, false)
    private var line: SourceDataLine? = null

    fun tap() = play(FeedbackSounds.tap)
    fun correct() = play(FeedbackSounds.correct)
    fun pair() = play(FeedbackSounds.pair)
    fun wrong() = play(FeedbackSounds.wrong)
    fun lessonComplete() = play(FeedbackSounds.lessonComplete)

    private fun play(pcm: ByteArray) {
        player.execute {
            runCatching {
                val l = line ?: AudioSystem.getSourceDataLine(format).also {
                    it.open(format, FeedbackSounds.SAMPLE_RATE / 20 * 2)  // 50 ms buffer = low latency
                    it.start()
                    line = it
                }
                l.write(pcm, 0, pcm.size)
            }.onFailure { line = null }
        }
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

    // Speaker button: pressing it again for the same text reads it much slower, then alternates.
    private var lastButtonText: String? = null
    private var lastButtonSlow = false

    /** For the speaker buttons: normal speed, and noticeably slower when pressed again for the same text. */
    fun speakButton(text: String) {
        val slow = text == lastButtonText && !lastButtonSlow
        lastButtonText = text
        lastButtonSlow = slow
        speak(text, slow)
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
    /** Rising chime when a lesson is finished. */
    fun lessonComplete(enabled: Boolean) {
        if (enabled) Tones.lessonComplete()
    }
    fun release() = speech.release()
}
