package com.vibeflow.mobile.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * L3 "Smart Formatting" — the AI polish layer that turns raw dictation into clean,
 * structured writing (the Wispr-style "wow"). Sends text to an OpenAI-compatible chat
 * model using the user's OWN key (BYOK). Network happens ONLY when the user explicitly
 * polishes, and never without a key. A local/on-device variant comes later for the
 * privacy/free tier; this is the remote path.
 */
class SmartFormatter(
    private val endpoint: String = "https://api.openai.com/v1/chat/completions",
) {

    /** Formatted text plus token usage (usage is kept for the managed-tier metering later). */
    data class PolishResult(val text: String, val promptTokens: Int = 0, val completionTokens: Int = 0)

    /** Returns the formatted text + usage, or a failure (network/auth/parse). Runs off-main. */
    suspend fun format(
        text: String, apiKey: String, model: String, style: String,
        userName: String = "", userTitle: String = "", appName: String = "",
    ): Result<PolishResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(apiKey.isNotBlank()) { "No API key set" }
                require(text.isNotBlank()) { "Nothing to format" }
                val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    setRequestProperty("Authorization", "Bearer $apiKey")
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 15_000
                    readTimeout = 45_000
                }
                val payload = JSONObject().apply {
                    put("model", model)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", systemPrompt(style, userName, userTitle, appName)))
                        put(JSONObject().put("role", "user").put("content", text))
                    })
                }
                conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()
                if (code !in 200..299) error("API error $code: ${body.take(180)}")
                val obj = JSONObject(body)
                val content = obj.getJSONArray("choices").getJSONObject(0)
                    .getJSONObject("message").getString("content").trim()
                val usage = obj.optJSONObject("usage")
                PolishResult(content, usage?.optInt("prompt_tokens") ?: 0, usage?.optInt("completion_tokens") ?: 0)
            }
        }

    companion object {
        val STYLES = listOf("cleanup", "structured", "email", "message", "notes")

        /** Max characters we'll ever send to polish — a cost guard against pasting huge
         *  text into a capture and hitting Polish. ~20 min of fast expert speech (~160 wpm);
         *  covers any real dictation, blocks abuse. Kept in sync with the server's MAX_TEXT. */
        const val MAX_INPUT_CHARS = 20000

        // ── L3 polish prompt ────────────────────────────────────────────────────────────
        // Core normalization + preservation rules, validated by the on-device eval harness
        // (104 real-dictation cases): lifted the cleanup pass rate from 38% → 92%, with money,
        // dates/quarters and proper-noun preservation all at 100%. Spoken numbers, quarters,
        // money, time and "number one" → "#1" now normalize, while meaning and real names stay put.
        private val NORMALIZE = """
            NORMALIZE spoken forms to their standard WRITTEN form:
            - Numbers: spelled-out to digits, INCLUDING small numbers, whenever they are quantities, counts, measurements, durations, money or list counts ("twenty five" to "25", "two loaves" to "2 loaves", "ten days" to "10 days", "three hundred forty" to "340").
            - List markers: when the speaker enumerates items with "number one / number two / number three" (or "first / second / third" as list markers), ALWAYS convert them to "#1", "#2", "#3" — even when the items continue inline.
            - Quarters & fiscal: "quarter three" to "Q3"; "fiscal year twenty four" to "FY24". Years: "twenty twenty five" to "2025".
            - Money: "fifty dollars" to "${'$'}50". Percent: "ten percent" to "10%".
            - Time: "three thirty pm" to "3:30 PM". Dates: "june twenty fifth" to "June 25".
            - Units (abbreviate with the digit): km, m, kg, g, ml, L, GB, MB ("five kilometers" to "5 km", "two point three gigabytes" to "2.3 GB").
            - Spoken acronyms: "a p i" to "API"; "u s a" to "USA".
            - Spoken punctuation — replace the SPOKEN WORD with the exact mark, never a different one and never the word itself: "comma" -> ",", "period"/"full stop" -> ".", "question mark" -> "?", "exclamation point" -> "!", "new line" -> a line break, "new paragraph" -> a blank line.
            - Obvious homophone slips, ONLY when context is unambiguous: by->buy, to->too, there->their, its->it's, your->you're, then->than. A trailing "to"/"two" that means "also/as well" becomes "too" and stays in the sentence.
            - Remove fillers ("um", "uh", "you know", "i mean", filler "like", "so basically") and collapse stutters/repeats ("the the" -> "the").
        """.trimIndent()

        private val PRESERVE = """
            PRESERVE — never change these:
            - The speaker's meaning, intent, facts, numbers and order. Do NOT paraphrase, summarize, shorten, expand or invent anything beyond the formatting requested above.
            - Real names, brands and technical terms exactly as said (e.g. ColorOS, OnePlus, Kubernetes, VibeFlow, WhatsApp, Docker). Never swap a real proper noun for a common word.
        """.trimIndent()

        private val INTRO =
            "You convert raw phone dictation (speech-to-text output) into clean, correctly written text — " +
            "exactly what the speaker meant to type.\n\nFIX: grammar, spelling, punctuation, capitalization, " +
            "and sentence/paragraph breaks. Standalone \"i\" becomes \"I\". Capitalize sentence starts and obvious " +
            "names/places. Prefer commas and periods over dashes; never output an em-dash."

        private const val OUTPUT_LINE = "Output ONLY the cleaned text — no preamble, quotes or explanation."

        /** The instruction that defines each formatting style, personalized with the sender. */
        fun systemPrompt(style: String, userName: String = "", userTitle: String = "", appName: String = ""): String = when (style) {
            // Default. Byte-for-byte the prompt the eval harness validated at 92%.
            "cleanup" -> "$INTRO\n\n$NORMALIZE\n\n$PRESERVE\n\n$OUTPUT_LINE"
            "message" ->
                "Clean up the dictated text into a clear, natural chat message — keep it casual but correctly written.\n\n" +
                    "$NORMALIZE\n\n$PRESERVE\n\nReturn ONLY the message."
            "structured" ->
                "Clean up and structure the dictated text: fix grammar and punctuation, add sentence/paragraph " +
                    "breaks, and use bullets or numbering only if the speaker clearly listed items.\n\n" +
                    "$NORMALIZE\n\n$PRESERVE\n\nReturn ONLY the formatted text, with no preamble."
            "auto" -> buildString {
                append("The user is dictating into ")
                append(if (appName.isNotBlank()) "the app \"$appName\"" else "an app")
                append(". Lightly shape it to suit that context — an email client → an email shape, a chat app → ")
                append("a clean casual message, a notes app → tidy notes — without changing what was said.\n\n")
                append(NORMALIZE); append("\n\n"); append(PRESERVE)
                if (userName.isNotBlank()) append("\n\nIf a sign-off is appropriate, sign as \"$userName\"${if (userTitle.isNotBlank()) ", $userTitle" else ""}.")
                append("\n\nReturn ONLY the formatted text, with no preamble.")
            }
            "email" -> buildString {
                append("Rewrite the dictated text as a clear, professional email. ")
                append("ALWAYS begin with a brief greeting on its own line (e.g. \"Hi,\"). Write a well-structured body. ")
                append("ALWAYS end with a courteous sign-off (e.g. \"Best regards,\")")
                if (userName.isNotBlank()) {
                    append(" on its own line, then the sender's name \"$userName\"")
                    if (userTitle.isNotBlank()) append(" and on the next line the title \"$userTitle\"")
                    append(".")
                } else append(". If a sender name is unknown, leave a \"[Your name]\" placeholder.")
                append(" Normalize numbers, dates, times, money, percentages and units to standard written form ")
                append("(e.g. #1, Q3, ${'$'}50, 10%, 3:30 PM, June 25). Preserve every fact, name and brand. Return ONLY the email.")
            }
            "notes" ->
                "Reformat the dictated text into concise, well-organized notes: short bullet points and small " +
                    "headings where helpful. Normalize numbers, dates, times, money and units to standard written " +
                    "form (e.g. #1, Q3, ${'$'}50, 3:30 PM). Preserve all information, names and brands. Return ONLY the notes."
            else -> "$INTRO\n\n$NORMALIZE\n\n$PRESERVE\n\n$OUTPUT_LINE"
        }
    }
}
