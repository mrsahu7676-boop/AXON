package com.axon.assistant

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.media.AudioManager
import android.os.Bundle
import java.util.Locale

/**
 * Wraps Android TextToSpeech.
 * Auto-detects Hindi vs English in the spoken text and sets locale accordingly.
 */
class TTSManager(
    private val context: Context,
    private val onSpeakStart: () -> Unit,
    private val onSpeakDone: () -> Unit
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isReady = false

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            isReady = true
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    onSpeakStart()
                }
                override fun onDone(utteranceId: String?) {
                    onSpeakDone()
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    onSpeakDone()
                }
            })
        }
    }

    /**
     * Speak the given text. Stops any current speech first.
     */
    fun speak(text: String) {
        if (!isReady) return

        // Ensure volume is loud enough
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (currentVolume < maxVolume * 0.7) {
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, (maxVolume * 0.8).toInt(), 0)
        }

        // Choose locale: Hindi script → hi_IN, otherwise English
        val locale = if (containsDevanagari(text)) {
            Locale("hi", "IN")
        } else {
            Locale.ENGLISH
        }

        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA ||
            result == TextToSpeech.LANG_NOT_SUPPORTED) {
            // Fallback to default
            tts?.setLanguage(Locale.ENGLISH)
        }

        tts?.stop()
        
        // Premium Voice Settings: Slightly higher pitch for a young female assistant
        tts?.setPitch(1.1f) 
        tts?.setSpeechRate(1.05f) 
        
        // Attempt to find a premium female voice
        try {
            val voices = tts?.voices
            val targetVoice = voices?.find { 
                it.locale.language == locale.language && 
                it.name.lowercase().contains("female") &&
                !it.isNetworkConnectionRequired // Try local voices first for speed, or network for quality
            } ?: voices?.find { 
                it.locale.language == locale.language && 
                it.name.lowercase().contains("female")
            }
            
            if (targetVoice != null) {
                tts?.voice = targetVoice
            }
        } catch (e: Exception) { /* Ignore */ }

        // Boost volume for "Loud and Clear"
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f) // Max TTS volume
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "AXON_UTTERANCE_${System.currentTimeMillis()}")
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }

    /** Returns true if text contains any Devanagari (Hindi) characters. */
    private fun containsDevanagari(text: String): Boolean =
        text.any { it.code in 0x0900..0x097F }
}
