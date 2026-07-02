package com.vibeflow.mobile.core

/**
 * Applies learned "heard → meant" corrections (from [CorrectionLearner]) to recognized
 * text. Whole-word, case-insensitive match; the replacement keeps its stored casing
 * (so "cubanet" → "Kubernetes"). Pure & offline.
 */
object Corrections {

    fun apply(text: String, map: Map<String, String>): String {
        if (text.isEmpty() || map.isEmpty()) return text
        // Build one regex of all the "from" words, longest first so multi-word keys win.
        val keys = map.keys.filter { it.isNotBlank() }.sortedByDescending { it.length }
        if (keys.isEmpty()) return text
        val alt = keys.joinToString("|") { Regex.escape(it) }
        val pattern = Regex("(?<![\\p{L}\\p{N}])($alt)(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
        return pattern.replace(text) { m ->
            map.entries.firstOrNull { it.key.equals(m.value, ignoreCase = true) }?.value ?: m.value
        }
    }
}
