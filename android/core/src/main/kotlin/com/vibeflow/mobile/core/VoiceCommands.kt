package com.vibeflow.mobile.core

/**
 * Offline voice-editing commands. When the *entire* utterance is a known command
 * phrase ("scratch that", "delete last word", "new line"), it is treated as an
 * edit action instead of text to insert. Matching the whole utterance (not a
 * substring) keeps it predictable — you never trigger an edit mid-sentence.
 *
 * This is the deterministic, fully-offline counterpart to the natural-language
 * editing that cloud apps (Aqua, superwhisper, Willow) sell as a premium feature.
 */
object VoiceCommands {

    enum class Command { DELETE_LAST, DELETE_WORD, NEW_LINE, NEW_PARAGRAPH }

    private val PHRASES: Map<String, Command> = mapOf(
        "scratch that" to Command.DELETE_LAST,
        "scratch all that" to Command.DELETE_LAST,
        "delete that" to Command.DELETE_LAST,
        "delete last" to Command.DELETE_LAST,
        "undo that" to Command.DELETE_LAST,
        "delete last word" to Command.DELETE_WORD,
        "delete a word" to Command.DELETE_WORD,
        "delete word" to Command.DELETE_WORD,
        "backspace word" to Command.DELETE_WORD,
        "new line" to Command.NEW_LINE,
        "next line" to Command.NEW_LINE,
        "new paragraph" to Command.NEW_PARAGRAPH,
    )

    private val EDGE_PUNCT = charArrayOf(' ', '\t', '\n', '.', ',', '!', '?', ';', ':')
    private val WS = Regex("\\s+")

    /** Returns the command if the whole utterance is one, else null. */
    fun parse(raw: String): Command? {
        if (raw.isBlank()) return null
        val key = WS.replace(raw.trim().lowercase().trim(*EDGE_PUNCT), " ")
        return PHRASES[key]
    }
}
