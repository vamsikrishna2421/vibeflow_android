package com.vibeflow.mobile.core

/**
 * Text snippets / macros: say the trigger phrase, insert the expansion.
 * e.g. "my email" -> "you@example.com". Matching is whole-phrase and
 * case-insensitive. Pure and offline.
 */
object Snippets {

    /**
     * Expand any trigger phrases found in [text]. Longer triggers are matched
     * first so "my work email" wins over "my email".
     */
    fun expand(text: String, snippets: Map<String, String>): String {
        if (text.isEmpty() || snippets.isEmpty()) return text
        var out = text
        for ((trigger, expansion) in snippets.entries.sortedByDescending { it.key.length }) {
            if (trigger.isBlank()) continue
            val pattern = Regex("\\b" + Regex.escape(trigger.trim()) + "\\b", RegexOption.IGNORE_CASE)
            // Lambda form: the returned string is inserted literally (no $-group expansion).
            out = pattern.replace(out) { expansion }
        }
        return out
    }
}
