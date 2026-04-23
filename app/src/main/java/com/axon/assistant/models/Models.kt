package com.axon.assistant.models

// ─── API Response Models ──────────────────────────────────────────────────────

data class AxonResponse(
    val spoken: String,
    val action: AxonAction?
)

data class AxonAction(
    val type: String,
    val params: Map<String, Any>?
)

// ─── Notification Model ───────────────────────────────────────────────────────

data class NotifItem(
    val app: String,
    val title: String,
    val text: String,
    val time: Long
)

// ─── Mood Context ─────────────────────────────────────────────────────────────

data class MoodContext(
    val time: String,
    val userSpeed: String,
    val moodHint: String
)
