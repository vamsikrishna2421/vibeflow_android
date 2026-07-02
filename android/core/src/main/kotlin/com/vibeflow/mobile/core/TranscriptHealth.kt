package com.vibeflow.mobile.core

/**
 * The REACTIVE detector in our defense-in-depth engine routing: after the system
 * recognizer returns, does its transcript look degraded (the low-voice failure
 * signature)? If so, we escalate to Whisper even when the proactive speech-level
 * check didn't fire.
 *
 * Signals (all cheap, offline):
 *  - unknown-word ratio   — gibberish like "qubar", "isquita" the dictionary rejects,
 *  - repeated phrases      — the "send the report send the report" duplication,
 *  - too short             — far fewer words than the utterance should contain.
 *
 * Pure & unit-tested. The dictionary check is injected so this stays I/O-free.
 */
object TranscriptHealth {

    private val WS = Regex("\\s+")
    private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    data class Health(
        val score: Float,            // 0..1, higher = healthier
        val looksDegraded: Boolean,
        val unknownRatio: Float,
        val repeatRatio: Float,
        val reasons: List<String>,
    )

    private const val UNKNOWN_LIMIT = 0.35f
    private const val REPEAT_LIMIT = 0.15f

    fun assess(text: String, knows: (String) -> Boolean, expectedMinWords: Int = 0): Health {
        val toks = text.trim().split(WS).filter { it.isNotEmpty() }
        if (toks.isEmpty()) return Health(0f, true, 1f, 0f, listOf("empty"))

        val words = toks.map { EDGE_PUNCT.replace(it, "") }
            .filter { it.isNotEmpty() && it.all { c -> c.isLetter() } }
        val unknownRatio = if (words.isEmpty()) 1f else words.count { !knows(it) }.toFloat() / words.size

        val collapsed = TextCuration.collapseRepeats(text).split(WS).filter { it.isNotEmpty() }.size
        val repeatRatio = if (toks.isEmpty()) 0f else (toks.size - collapsed).toFloat() / toks.size

        val reasons = ArrayList<String>()
        if (unknownRatio > UNKNOWN_LIMIT) reasons.add("many unknown words (${(unknownRatio * 100).toInt()}%)")
        if (repeatRatio > REPEAT_LIMIT) reasons.add("repeated phrases")
        val tooShort = expectedMinWords > 0 && toks.size < expectedMinWords * 0.6f
        if (tooShort) reasons.add("shorter than expected")

        val score = ((1f - unknownRatio) * (1f - repeatRatio)).coerceIn(0f, 1f)
        val degraded = unknownRatio > UNKNOWN_LIMIT || repeatRatio > REPEAT_LIMIT || tooShort
        return Health(score, degraded, unknownRatio, repeatRatio, reasons)
    }
}
