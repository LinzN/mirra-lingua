package de.mirranet.parla.ui

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Text-to-speech for the target language, plus short feedback tones. */
class Audio(context: Context) : TextToSpeech.OnInitListener {

    private val tts = TextToSpeech(context.applicationContext, this)
    private var ready = false
    private var language: String? = null
    private val tones = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 60) }.getOrNull()

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
        if (!enabled) return
        tones?.startTone(if (correct) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_NACK, 180)
    }

    fun release() {
        tts.shutdown()
        tones?.release()
    }
}
