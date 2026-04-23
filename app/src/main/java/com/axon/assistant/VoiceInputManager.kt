package com.axon.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Wraps Android SpeechRecognizer with a clean callback interface.
 * Supports Hindi + English (Hinglish) via hi-IN locale.
 */
class VoiceInputManager(
    private val context: Context,
    private val onTranscript: (text: String, durationMs: Long) -> Unit,
    private val onPartialResult: (text: String) -> Unit,
    private val onListeningStarted: () -> Unit,
    private val onListeningEnded: () -> Unit,
    private val onError: (message: String) -> Unit
) {

    private var speechRecognizer: SpeechRecognizer? = null
    private var listenStartTime: Long = 0L

    private val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN")
        putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
    }

    fun initialize() {
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            onError("Speech recognition is not available on this device.")
            return
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {

                override fun onReadyForSpeech(params: Bundle?) {
                    listenStartTime = System.currentTimeMillis()
                    onListeningStarted()
                }

                override fun onBeginningOfSpeech() {}

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    onListeningEnded()
                }

                override fun onError(error: Int) {
                    val msg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO              -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT             -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission denied"
                        SpeechRecognizer.ERROR_NETWORK            -> "Network error — check your connection"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT    -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH           -> "Kuch sun nahi aaya, phir bolo"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY    -> "Recognizer busy, please wait"
                        SpeechRecognizer.ERROR_SERVER             -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT     -> "Bol na yaar, main sun raha hoon!"
                        else                                      -> "Unknown error ($error)"
                    }
                    onListeningEnded()
                    onError(msg)
                }

                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val transcript = matches?.firstOrNull()?.trim() ?: return
                    val duration = System.currentTimeMillis() - listenStartTime
                    onTranscript(transcript, duration)
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull() ?: return
                    onPartialResult(partial)
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
        }
    }

    fun startListening() {
        try {
            speechRecognizer?.startListening(recognizerIntent)
        } catch (e: Exception) {
            onError("Could not start listening: ${e.message}")
        }
    }

    fun stopListening() {
        try {
            speechRecognizer?.stopListening()
        } catch (e: Exception) { /* ignore */ }
    }

    fun cancel() {
        try {
            speechRecognizer?.cancel()
        } catch (e: Exception) { /* ignore */ }
    }

    fun destroy() {
        try {
            speechRecognizer?.destroy()
        } catch (e: Exception) { /* ignore */ }
        speechRecognizer = null
    }
}
