package com.autodj.dj

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

/**
 * Deep DJ voice drops using Android TTS with low pitch and slow rate
 * to simulate a hyped DJ announcer voice.
 */
class VoiceDrop(context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val engine = tts ?: return@TextToSpeech
                engine.language = Locale.US
                // Deep, slow, dramatic voice: pitch 0.5 (half pitch = very deep), rate 0.85 (slightly slower)
                engine.setPitch(0.5f)
                engine.setSpeechRate(0.85f)
                ready = true
                Log.i("VoiceDrop", "TTS ready")
            } else {
                Log.w("VoiceDrop", "TTS init failed: $status")
            }
        }
    }

    fun speak(text: String) {
        if (!ready) return
        val engine = tts ?: return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "voice_drop_${text.hashCode()}")
    }

    fun areYouReady() = speak("Are you ready?!")
    fun fireTheDJ() = speak("FIRE... the DJ!")
    fun letsGo() = speak("Let's GO!")
    fun pullUp() = speak("Pull up! Rewind!")

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
