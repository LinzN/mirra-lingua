package de.mirranet.parla.desktop

import java.io.BufferedWriter
import java.util.Locale
import java.util.concurrent.Executors
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.sin

/**
 * Text-to-speech using what the operating system already has:
 * Windows: the built-in speech API (System.Speech via a long-running PowerShell process);
 * Linux: speech-dispatcher (spd-say), espeak-ng or espeak.
 * [available] is true once a voice for the current language was found.
 */
class Speech {

    @Volatile var available = false
        private set

    private var language = "en-GB"
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "speech").apply { isDaemon = true } }

    // Windows: one PowerShell process that reads "culture|rate|text" lines from stdin.
    private var psProcess: Process? = null
    private var psInput: BufferedWriter? = null
    private var windowsCultures: List<String> = emptyList()

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
        }
    }

    fun setLanguage(tag: String) {
        language = tag
        worker.execute { updateAvailability() }
    }

    private fun updateAvailability() {
        val base = language.substringBefore('-').lowercase(Locale.ROOT)
        available = when {
            DesktopPlatform.isWindows -> windowsCultures.any { it.lowercase(Locale.ROOT).startsWith(base) }
            else -> linuxTool != null
        }
    }

    fun speak(text: String, slow: Boolean = false) {
        if (!available || text.isBlank()) return
        val clean = text.replace('\n', ' ').replace('|', ' ')
        worker.execute {
            try {
                if (DesktopPlatform.isWindows) speakWindows(clean, slow) else speakLinux(clean, slow)
            } catch (e: Exception) {
                StderrLogger.warn("Speaking failed", e)
            }
        }
    }

    fun stop() {
        worker.execute {
            current?.destroy()
            psInput?.let { runCatching { it.write("STOP\n"); it.flush() } }
        }
    }

    fun release() {
        runCatching { psInput?.write("EXIT\n"); psInput?.flush() }
        psProcess?.destroy()
        current?.destroy()
        worker.shutdownNow()
    }

    // ------------------------------------------------------------------ Windows

    private val psScript = """
        Add-Type -AssemblyName System.Speech
        ${'$'}s = New-Object System.Speech.Synthesis.SpeechSynthesizer
        ${'$'}s.SetOutputToDefaultAudioDevice()
        [Console]::Out.WriteLine('VOICES ' + ((${'$'}s.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled } | ForEach-Object { ${'$'}_.VoiceInfo.Culture.Name }) -join ','))
        [Console]::Out.Flush()
        while ((${'$'}line = [Console]::In.ReadLine()) -ne ${'$'}null) {
          if (${'$'}line -eq 'EXIT') { break }
          ${'$'}s.SpeakAsyncCancelAll()
          if (${'$'}line -eq 'STOP') { continue }
          ${'$'}p = ${'$'}line.Split('|', 3)
          ${'$'}v = ${'$'}s.GetInstalledVoices() | Where-Object { ${'$'}_.Enabled -and ${'$'}_.VoiceInfo.Culture.Name -like (${'$'}p[0] + '*') } | Select-Object -First 1
          if (${'$'}v) { ${'$'}s.SelectVoice(${'$'}v.VoiceInfo.Name) }
          ${'$'}s.Rate = [int]${'$'}p[1]
          ${'$'}null = ${'$'}s.SpeakAsync(${'$'}p[2])
        }
    """.trimIndent()

    private fun startWindows() {
        val encoded = java.util.Base64.getEncoder().encodeToString(psScript.toByteArray(Charsets.UTF_16LE))
        val p = ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-EncodedCommand", encoded)
            .redirectErrorStream(true).start()
        psProcess = p
        psInput = p.outputStream.bufferedWriter(Charsets.UTF_8)
        val first = p.inputStream.bufferedReader().readLine().orEmpty()
        windowsCultures = first.removePrefix("VOICES ").split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    private fun speakWindows(text: String, slow: Boolean) {
        val base = language.substringBefore('-')
        val w = psInput ?: return
        w.write("$base|${if (slow) -4 else 0}|$text\n")
        w.flush()
    }

    // ------------------------------------------------------------------ Linux

    private fun findLinuxTool(): String? =
        listOf("spd-say", "espeak-ng", "espeak").firstOrNull { tool ->
            System.getenv("PATH").orEmpty().split(':').any { java.io.File(it, tool).canExecute() }
        }

    private fun speakLinux(text: String, slow: Boolean) {
        val tool = linuxTool ?: return
        val base = language.substringBefore('-')
        current?.destroy()
        val cmd = when (tool) {
            "spd-say" -> listOf("spd-say", "-w", "-C", "-l", base, "-r", if (slow) "-50" else "-10", text)
            else -> listOf(tool, "-v", base, "-s", if (slow) "110" else "150", text)
        }
        current = ProcessBuilder(cmd).redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD).start()
    }
}

/** Short feedback tones generated on the fly (no sound files needed). */
object Tones {
    private val player = Executors.newSingleThreadExecutor { Thread(it, "tones").apply { isDaemon = true } }

    fun play(correct: Boolean) {
        player.execute {
            runCatching {
                val notes = if (correct) listOf(660.0, 990.0) else listOf(196.0, 156.0)
                val rate = 44_100f
                val pcm = notes.flatMap { tone(it, 0.13, rate).toList() }.toByteArray()
                val format = AudioFormat(rate, 16, 1, true, false)
                AudioSystem.getSourceDataLine(format).use { line ->
                    line.open(format)
                    line.start()
                    line.write(pcm, 0, pcm.size)
                    line.drain()
                }
            }
        }
    }

    private fun tone(freq: Double, seconds: Double, rate: Float): ByteArray {
        val n = (seconds * rate).toInt()
        val out = ByteArray(n * 2)
        for (i in 0 until n) {
            // Short fade in/out avoids clicks.
            val env = min(1.0, min(i / (rate * 0.01), (n - i) / (rate * 0.04)))
            val v = (sin(2 * PI * freq * i / rate) * env * 0.25 * Short.MAX_VALUE).toInt()
            out[2 * i] = (v and 0xff).toByte()
            out[2 * i + 1] = ((v shr 8) and 0xff).toByte()
        }
        return out
    }
}

/** Same surface as the Android Audio class, so the shared screens can use either. */
class Audio {
    private val speech = Speech()
    val available: Boolean get() = speech.available
    fun setLanguage(tag: String) = speech.setLanguage(tag)
    fun speak(text: String, slow: Boolean = false) = speech.speak(text, slow)
    fun stop() = speech.stop()
    fun feedback(correct: Boolean, enabled: Boolean) {
        if (enabled) Tones.play(correct)
    }
    fun release() = speech.release()
}
