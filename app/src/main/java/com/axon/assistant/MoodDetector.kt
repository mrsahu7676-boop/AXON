package com.axon.assistant

import com.axon.assistant.models.MoodContext
import java.time.LocalTime

/**
 * Detects user mood from transcript text and speaking speed.
 * Injects time-of-day, speaking speed, and keyword-based mood hint into Gemini context.
 */
class MoodDetector {

    private val urgentKeywords = setOf(
        "jaldi", "abhi", "turant", "emergency", "quickly", "fast", "urgent",
        "now", "immediately", "asap", "help me", "please hurry", "bahut jaldi"
    )

    private val angryKeywords = setOf(
        "kyu nahi", "kyun nahi", "useless", "stupid", "bakwaas", "worst",
        "hate", "terrible", "pathetic", "garbage", "bekar", "faltu"
    )

    private val casualKeywords = setOf(
        "yaar", "bhai", "buddy", "chill", "bolo", "suno", "kya hal",
        "kya haal", "sup", "hey", "hlo", "kya chal", "kaise ho", "arey"
    )

    private val questionKeywords = setOf(
        "kya", "kaise", "kab", "kyun", "what", "how", "when", "why",
        "where", "kaun", "kitna", "tell me", "bata"
    )

    /**
     * @param transcript   Raw STT result
     * @param durationMs   How long the user spoke (ms)
     * @return MoodContext to inject into Gemini prompt
     */
    fun detect(transcript: String, durationMs: Long): MoodContext {
        val lower = transcript.lowercase().trim()
        val wordCount = lower.split("\\s+".toRegex()).filter { it.isNotEmpty() }.size

        // Calculate speaking speed
        val wordsPerSecond = if (durationMs > 500) {
            wordCount / (durationMs / 1000.0)
        } else 2.0

        val userSpeed = when {
            wordsPerSecond > 3.5 -> "fast"
            wordsPerSecond < 1.2 -> "slow"
            else -> "normal"
        }

        // Detect mood hint from keywords
        val moodHint = when {
            urgentKeywords.any { lower.contains(it) } || userSpeed == "fast" -> "urgent"
            angryKeywords.any { lower.contains(it) }                         -> "frustrated"
            casualKeywords.any { lower.contains(it) }                        -> "casual"
            questionKeywords.any { lower.contains(it) } || lower.endsWith("?") -> "curious"
            else                                                              -> "neutral"
        }

        // Time of day
        val now = LocalTime.now()
        val timeStr = String.format("%02d:%02d", now.hour, now.minute)

        return MoodContext(
            time = timeStr,
            userSpeed = userSpeed,
            moodHint = moodHint
        )
    }
}
