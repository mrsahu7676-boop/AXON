package com.axon.assistant

import java.util.LinkedList

/**
 * Stores last 20 conversation turns for Gemini API context.
 */
class ConversationHistory {

    private val maxSize = 40 // 20 user + 20 assistant turns
    private val messages = LinkedList<Map<String, String>>()

    fun addUserMessage(content: String) {
        trimIfNeeded()
        messages.add(mapOf("role" to "user", "content" to content))
    }

    fun addAssistantMessage(content: String) {
        trimIfNeeded()
        messages.add(mapOf("role" to "assistant", "content" to content))
    }

    fun getHistory(): List<Map<String, String>> = messages.toList()

    fun clear() = messages.clear()

    fun size() = messages.size

    private fun trimIfNeeded() {
        while (messages.size >= maxSize) {
            messages.removeFirst()
        }
    }
}
