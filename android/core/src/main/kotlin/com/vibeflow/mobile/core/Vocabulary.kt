package com.vibeflow.mobile.core

/**
 * Adaptive vocabulary: fix the casing/spelling of terms the user cares about.
 *
 * The offline engine emits lowercase tokens ("github", "kubernetes", "vibeflow").
 * When the user teaches VibeFlow the canonical form ("GitHub", "Kubernetes",
 * "VibeFlow"), this restores it on every dictation — a real win over raw Vosk
 * output. Whole-word, case-insensitive, longest-first. Pure and offline.
 */
object Vocabulary {

    /** Replace case-insensitive whole-word matches of each known term with its canonical form. */
    fun apply(text: String, terms: Collection<String>): String {
        if (text.isEmpty() || terms.isEmpty()) return text
        var out = text
        for (term in terms.distinct().sortedByDescending { it.length }) {
            val canonical = term.trim()
            if (canonical.isEmpty()) continue
            // Multi-word canonical terms ("Visual Studio Code") match their phrase.
            val pattern = Regex("\\b" + Regex.escape(canonical) + "\\b", RegexOption.IGNORE_CASE)
            out = pattern.replace(out) { canonical }
        }
        return out
    }
}
