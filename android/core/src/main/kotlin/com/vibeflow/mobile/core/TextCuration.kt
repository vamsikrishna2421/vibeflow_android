package com.vibeflow.mobile.core

/**
 * Deterministic, fully-offline curation of raw speech-to-text output.
 *
 * Ported and extended from the VibeFlow desktop `core/curate.py`. On mobile the
 * offline engine (Vosk small model) emits **lowercase, unpunctuated** text, so
 * this layer does more heavy lifting than on desktop:
 *
 *  - applies spoken layout commands ("new line", "new paragraph"),
 *  - applies spoken punctuation ("period", "comma", "question mark", ...),
 *  - normalises spacing around punctuation,
 *  - fixes the lone pronoun "i" -> "I" (and its contractions),
 *  - capitalises the start of sentences and the first letter,
 *  - optionally removes vocalised fillers ("um", "uh") — conservative & offline.
 *
 * Every function here is pure (no I/O), so it is fast, offline, and unit-tested.
 */
object TextCuration {

    /** Conservative, unambiguous vocalised fillers. Mirrors the desktop default set. */
    val DEFAULT_FILLERS = listOf("um", "uh", "umm", "uhh", "uhm", "mm", "hmm", "er", "ah")

    /**
     * Spoken punctuation map: phrase a user can say -> the literal it becomes.
     * Order matters only for display; matching is whole-word and case-insensitive.
     * Multi-word phrases are matched before single words by [applySpokenPunctuation].
     */
    val SPOKEN_PUNCTUATION: List<Pair<String, String>> = listOf(
        "exclamation point" to "!",
        "exclamation mark" to "!",
        "question mark" to "?",
        "open parenthesis" to "(",
        "close parenthesis" to ")",
        "open paren" to "(",
        "close paren" to ")",
        "open quote" to "\"",
        "close quote" to "\"",
        "ellipsis" to "…",
        "semicolon" to ";",
        "colon" to ":",
        "comma" to ",",
        "period" to ".",
        "full stop" to ".",
        "hyphen" to "-",
        "dash" to " — ",
        "ampersand" to "&",
        "asterisk" to "*",
        "percent sign" to "%",
        "dollar sign" to "$",
        "at sign" to "@",
    )

    private val PARAGRAPH = Regex("(?i)\\bnew\\s+paragraph\\b")
    private val NEWLINE = Regex("(?i)\\bnew\\s+line\\b|\\bnext\\s+line\\b")
    private val SPACE_BEFORE_PUNCT = Regex("[ \\t]+([,.!?;:…])")
    // Add a space after clause/sentence punctuation only when it is NOT glued to
    // an alphanumeric on the left — this protects emails, URLs, decimals and
    // versions ("you@example.com", "v1.2") from being split. Spoken-punctuation
    // already produces correct spacing, so this only fixes genuinely glued cases.
    private val SPACE_AFTER_PUNCT = Regex("(?<![A-Za-z0-9])([,.!?;:])(?=[A-Za-z])")
    private val MULTISPACE = Regex("[ \\t]{2,}")
    private val SPACE_AROUND_NL = Regex("[ \\t]*\\n[ \\t]*")
    private val LONE_I = Regex("\\bi\\b")
    private val I_CONTRACTION = Regex("\\bi('(?:m|ve|ll|d|s|re))\\b", RegexOption.IGNORE_CASE)

    // Only matches a lowercase letter that begins an all-lowercase word, so
    // case-bearing tokens (iOS, eBay, pH) are never force-capitalised.
    private val AFTER_SENTENCE = Regex("([.!?]\\s+)([a-z])(?=[a-z]*\\b)")
    private val AFTER_NEWLINE = Regex("(\\n[ \\t]*)([a-z])(?=[a-z]*\\b)")
    private val FIRST_ALPHA = Regex("^(\\s*)([a-z])(?=[a-z]*\\b)")

    data class Options(
        val spokenCommands: Boolean = true,
        val spokenPunctuation: Boolean = true,
        val capitalizeSentences: Boolean = true,
        val capitalizeFirst: Boolean = true,
        val fixPronounI: Boolean = true,
        val stripFillers: Boolean = false,
        val autoPeriod: Boolean = false,
        val dedupeRepeats: Boolean = true,
        val fillers: List<String> = DEFAULT_FILLERS,
    )

    private val WHITESPACE = Regex("\\s+")

    /**
     * Collapse an immediately-repeated phrase to a single copy — e.g. a recogniser
     * that emits "send the report send the report" becomes "send the report". Only
     * phrases of [minPhrase] words or more are collapsed, so deliberate short repeats
     * ("no no", "very very", "had had") are left untouched. Whitespace is normalised.
     */
    fun collapseRepeats(text: String, minPhrase: Int = 3, maxPhrase: Int = 8): String {
        if (text.isBlank()) return text
        val toks = text.split(WHITESPACE).filter { it.isNotEmpty() }
        if (toks.size < minPhrase * 2) return text
        val out = ArrayList<String>(toks.size)
        var i = 0
        while (i < toks.size) {
            val maxL = minOf(maxPhrase, (toks.size - i) / 2)
            var matched = 0
            for (l in maxL downTo minPhrase) {
                var equal = true
                for (j in 0 until l) if (!toks[i + j].equals(toks[i + l + j], ignoreCase = true)) { equal = false; break }
                if (equal) { matched = l; break }
            }
            if (matched > 0) { for (j in 0 until matched) out.add(toks[i + j]); i += matched * 2 }
            else { out.add(toks[i]); i += 1 }
        }
        return out.joinToString(" ")
    }

    /** Strip standalone vocalised fillers (um, uh, ...) — conservative & offline. */
    fun removeFillers(text: String, fillers: List<String> = DEFAULT_FILLERS): String {
        if (text.isEmpty()) return text
        val sorted = fillers.sortedByDescending { it.length }
        val alt = sorted.joinToString("|") { Regex.escape(it) }
        val normalized = text.replace("\r\n", "\n").replace("\r", "\n")

        val onlyLine = Regex("[ \\t,]*(?:$alt)(?:[ \\t,]+(?:$alt))*[ \\t,]*", RegexOption.IGNORE_CASE)
        val leading = Regex("^[ \\t]*(?:$alt)(?:[ \\t]*,)?(?=[ \\t]|$)", RegexOption.IGNORE_CASE)
        val internal = Regex("[ \\t]+(?:$alt)(?=[ \\t])", RegexOption.IGNORE_CASE)
        val trailing = Regex("[ \\t]*,?[ \\t]+(?:$alt)[ \\t]*$", RegexOption.IGNORE_CASE)
        val collapse = Regex("[ \\t]{2,}")

        val out = ArrayList<String>()
        for (line in normalized.split("\n")) {
            val stripped = line.trim()
            if (stripped.isNotEmpty() && onlyLine.matchEntire(stripped) != null) continue
            var l = leading.replace(line, "")
            var prev: String? = null
            while (prev != l) { // fixpoint: clear consecutive fillers ("um uh")
                prev = l
                l = internal.replace(l, "")
            }
            l = trailing.replace(l, "")
            out.add(collapse.replace(l, " ").trim(' ', '\t'))
        }
        return out.joinToString("\n")
    }

    /** Replace spoken punctuation phrases ("comma", "question mark") with literals. */
    fun applySpokenPunctuation(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        // Longest phrases first so "question mark" isn't half-matched by "mark".
        for ((phrase, mark) in SPOKEN_PUNCTUATION.sortedByDescending { it.first.length }) {
            val pattern = Regex("\\b" + Regex.escape(phrase) + "\\b", RegexOption.IGNORE_CASE)
            // Attach sentence-final marks to the previous word (drop the leading space).
            out = pattern.replace(out) { mark }
        }
        return out
    }

    /** Main pipeline: turn raw engine text into tidy written text. */
    fun curate(text: String, options: Options = Options()): String {
        if (text.isEmpty()) return ""
        var out = text

        if (options.dedupeRepeats) out = collapseRepeats(out)
        if (options.stripFillers) out = removeFillers(out, options.fillers)
        if (options.spokenCommands) {
            out = PARAGRAPH.replace(out, "\n\n")
            out = NEWLINE.replace(out, "\n")
        }
        if (options.spokenPunctuation) out = applySpokenPunctuation(out)

        out = SPACE_BEFORE_PUNCT.replace(out, "$1")
        out = SPACE_AFTER_PUNCT.replace(out) { it.groupValues[1] + " " }
        out = MULTISPACE.replace(out, " ")
        out = SPACE_AROUND_NL.replace(out, "\n")

        if (options.fixPronounI) {
            out = I_CONTRACTION.replace(out) { "I" + it.groupValues[1] }
            out = LONE_I.replace(out, "I")
        }
        if (options.autoPeriod) out = addTrailingPeriod(out)
        if (options.capitalizeSentences || options.capitalizeFirst) {
            out = capitalize(out, options.capitalizeSentences, options.capitalizeFirst)
        }
        return out.trim()
    }

    private fun addTrailingPeriod(text: String): String {
        val t = text.trimEnd()
        if (t.isEmpty()) return text
        val last = t.last()
        return if (last.isLetterOrDigit()) "$t." else text
    }

    private fun capitalize(text: String, sentences: Boolean, first: Boolean): String {
        var out = text
        if (first) {
            out = FIRST_ALPHA.replace(out) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        if (sentences) {
            out = AFTER_SENTENCE.replace(out) { it.groupValues[1] + it.groupValues[2].uppercase() }
            out = AFTER_NEWLINE.replace(out) { it.groupValues[1] + it.groupValues[2].uppercase() }
        }
        return out
    }
}
