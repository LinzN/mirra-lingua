package de.mirranet.parla.ui

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.speech.tts.TextToSpeech
import de.mirranet.parla.audio.FeedbackSounds
import java.util.Locale
import java.util.concurrent.Executors

/** Text-to-speech for the target language, plus short feedback tones. */
class Audio(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var language: String? = null
    private val tones = Tones()

    /** True once the engine is up and has a voice for the current language. */
    var available = false
        private set

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        language?.let { setLanguage(it) }
    }

    fun setLanguage(tag: String) {
        language = tag
        if (!ready) return
        val result = tts.setLanguage(Locale.forLanguageTag(tag))
        available = result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED
    }

    fun speak(text: String, slow: Boolean = false) {
        if (!ready || !available) return
        tts.setSpeechRate(if (slow) 0.6f else 0.95f)
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "parla")
    }

    fun stop() {
        if (ready) tts.stop()
    }

    fun feedback(correct: Boolean, enabled: Boolean) {
        if (enabled) tones.play(if (correct) FeedbackSounds.correct else FeedbackSounds.wrong)
    }

    /** Soft tick when a word chip or an answer option is picked. */
    fun tap(enabled: Boolean) {
        if (enabled) tones.play(FeedbackSounds.tap)
    }

    /** Small chime when a matching pair was found. */
    fun pairFound(enabled: Boolean) {
        if (enabled) tones.play(FeedbackSounds.pair)
    }

    fun release() {
        tts.shutdown()
        tones.release()
    }
}

/** Plays the feedback sounds through one streaming AudioTrack that stays open, so the tick has no delay. */
private class Tones {
    private val player = Executors.newSingleThreadExecutor { Thread(it, "tones").apply { isDaemon = true } }
    private var track: AudioTrack? = null

    fun play(pcm: ByteArray) {
        player.execute {
            runCatching {
                val t = track ?: create().also { track = it }
                t.write(pcm, 0, pcm.size)
            }.onFailure { track = null }
        }
    }

    private fun create(): AudioTrack {
        val format = AudioFormat.Builder()
            .setSampleRate(FeedbackSounds.SAMPLE_RATE)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()
        val minBuffer = AudioTrack.getMinBufferSize(
            FeedbackSounds.SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(format)
            .setBufferSizeInBytes(minBuffer)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { it.play() }
    }

    fun release() {
        player.execute { track?.release(); track = null }
        player.shutdown()
    }
}
