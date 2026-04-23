package com.axon.assistant

import android.content.Context
import android.content.SharedPreferences
import com.axon.assistant.models.AxonAction
import com.axon.assistant.models.AxonResponse
import com.axon.assistant.models.MoodContext
import com.google.gson.JsonParser
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles all communication with the Google Gemini 2.5 Flash API.
 * Sends user transcript + mood context, returns parsed AxonResponse.
 */
class GeminiAPIClient(
    private val context: Context,
    private val conversationHistory: ConversationHistory
) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val mediaType = "application/json; charset=utf-8".toMediaType()

    private val systemPrompt = """
You are AXON, a personal AI assistant living inside the user's Android phone.

PERSONALITY:
- You are a friendly female assistant named AXON.
- You speak in NATURAL HINDI (Hinglish mix is okay). 
- Your tone is polite, helpful, and like a young girl.
- You adapt to the user's mood. If casual, be friendly. If urgent, be sharp.
- Keep responses SHORT and sweet (1-3 sentences).

DEVICE CONTROL:
When the user asks to perform a phone action, always respond with:
{
  "spoken": "Okay, calling Rahul now",
  "action": {
    "type": "MAKE_CALL",
    "params": { "contact": "Rahul" }
  }
}

Supported action types:
- MAKE_CALL: { contact: string }
- SEND_SMS: { contact: string, message: string }
- OPEN_APP: { appName: string }
- SET_ALARM: { hour: int, minute: int, label: string }
- PLAY_MUSIC: { query: string }
- TAKE_PHOTO: {}
- TOGGLE_WIFI: { state: "on"|"off" }
- TOGGLE_BLUETOOTH: { state: "on"|"off" }
- SET_VOLUME: { level: int }
- TOGGLE_FLASHLIGHT: { state: "on"|"off" }
- SEARCH_WEB: { query: string }
- SEND_WHATSAPP: { contact: string, message: string }
- READ_NOTIFICATIONS: {}
- SET_REMINDER: { text: string, time: string }
- OPEN_SETTINGS: { section: string }
- INCREASE_BRIGHTNESS: { level: int }
- GET_BATTERY: {}
- SPEAK_TIME: {}
- SPEAK_DATE: {}

If the user is just chatting (no phone action needed), respond with:
{ "spoken": "Your response here", "action": null }

Always return valid JSON. Never add extra text outside the JSON.
    """.trimIndent()

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Send a message to Gemini with mood context.
     * @param userText     Raw transcript from STT
     * @param moodContext  Detected mood context
     * @param onSuccess    Called with parsed AxonResponse
     * @param onError      Called with error message
     */
    fun sendMessage(
        userText: String,
        moodContext: MoodContext,
        onSuccess: (AxonResponse) -> Unit,
        onError: (String) -> Unit
    ) {
        val prefs: SharedPreferences =
            context.getSharedPreferences("axon_prefs", Context.MODE_PRIVATE)
        val apiKey = prefs.getString("gemini_api_key", "")?.takeIf { it.isNotBlank() } 
            ?: "AIzaSyAJiu2lu_lQxnRAcZ9sv_1DsXwwHOmbR7I"

        if (apiKey.isBlank()) {
            onError("Gemini API key not set. Please go to Settings and enter your API key.")
            return
        }

        // Inject mood context into the user message
        val enrichedText = buildString {
            append("[Context: time=${moodContext.time}, ")
            append("speed=${moodContext.userSpeed}, ")
            append("mood=${moodContext.moodHint}]\n")
            append(userText)
        }

        conversationHistory.addUserMessage(enrichedText)

        val requestBodyJson = buildGeminiRequestBody()
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3-flash-preview:generateContent?key=$apiKey"

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .post(requestBodyJson.toRequestBody(mediaType))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onError("Network error: ${e.message}")
            }

            override fun onResponse(call: Call, response: Response) {
                val bodyStr = response.body?.string() ?: run {
                    onError("Empty response from Gemini.")
                    return
                }

                if (!response.isSuccessful) {
                    onError("Gemini API error ${response.code}: $bodyStr")
                    return
                }

                try {
                    // Parse: response.candidates[0].content.parts[0].text
                    val root = JsonParser.parseString(bodyStr).asJsonObject
                    val text = root
                        .getAsJsonArray("candidates")
                        .get(0).asJsonObject
                        .getAsJsonObject("content")
                        .getAsJsonArray("parts")
                        .get(0).asJsonObject
                        .get("text").asString

                    // Note: Gemini role is "model", not "assistant"
                    conversationHistory.addAssistantMessage(text)

                    val axonResponse = parseAxonResponse(text)
                    onSuccess(axonResponse)

                } catch (e: Exception) {
                    onError("Parse error: ${e.message}")
                }
            }
        })
    }

    // ─── Request Builder ──────────────────────────────────────────────────────

    /**
     * Builds the Gemini API request JSON with conversation history.
     * Format:
     * {
     *   "systemInstruction": { "parts": [{ "text": "..." }] },
     *   "contents": [
     *     { "role": "user",  "parts": [{ "text": "..." }] },
     *     { "role": "model", "parts": [{ "text": "..." }] },
     *     ...
     *   ]
     * }
     */
    private fun buildGeminiRequestBody(): String {
        val history = conversationHistory.getHistory()

        val contentsBuilder = StringBuilder("[")
        history.forEachIndexed { index, msg ->
            if (index > 0) contentsBuilder.append(",")
            // Gemini uses "model" instead of "assistant"
            val geminiRole = if (msg["role"] == "assistant") "model" else "user"
            val content = escapeJson(msg["content"] ?: "")
            contentsBuilder.append("""{"role":"$geminiRole","parts":[{"text":"$content"}]}""")
        }
        contentsBuilder.append("]")

        val systemEscaped = escapeJson(systemPrompt)

        return """
            {
              "systemInstruction": {
                "parts": [{ "text": "$systemEscaped" }]
              },
              "contents": $contentsBuilder,
              "generationConfig": {
                "temperature": 0.7,
                "maxOutputTokens": 1024
              }
            }
        """.trimIndent()
    }

    // ─── Response Parser ──────────────────────────────────────────────────────

    private fun parseAxonResponse(rawText: String): AxonResponse {
        return try {
            // Strip markdown code fences if Gemini wrapped the JSON
            val clean = rawText
                .trim()
                .removePrefix("```json").removePrefix("```")
                .removeSuffix("```")
                .trim()

            val root   = JsonParser.parseString(clean).asJsonObject
            val spoken = root.get("spoken")?.asString ?: "Kuch samajh nahi aaya."

            val actionElement = root.get("action")
            val action = if (actionElement != null && !actionElement.isJsonNull) {
                val actionObj = actionElement.asJsonObject
                val type      = actionObj.get("type")?.asString ?: ""
                val params    = if (actionObj.has("params") && !actionObj.get("params").isJsonNull) {
                    parseParams(actionObj.getAsJsonObject("params"))
                } else null
                AxonAction(type, params)
            } else null

            AxonResponse(spoken, action)

        } catch (e: Exception) {
            AxonResponse("Response samajh nahi aaya. Phir try karo.", null)
        }
    }

    private fun parseParams(paramsObj: com.google.gson.JsonObject): Map<String, Any> {
        return paramsObj.entrySet().associate { (key, value) ->
            val parsed: Any = when {
                value.isJsonNull      -> ""
                value.isJsonPrimitive -> {
                    val prim = value.asJsonPrimitive
                    when {
                        prim.isNumber  -> prim.asNumber
                        prim.isBoolean -> prim.asBoolean
                        else           -> prim.asString
                    }
                }
                else -> value.toString()
            }
            key to parsed
        }
    }

    private fun escapeJson(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
