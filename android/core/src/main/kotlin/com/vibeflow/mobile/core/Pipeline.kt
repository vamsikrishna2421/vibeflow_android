package com.vibeflow.mobile.core

/**
 * The single source of truth for turning raw engine output into delivered text.
 * Both the keyboard (IME) and the app/tile capture paths run text through here,
 * so behaviour is identical everywhere.
 *
 * Order matters:
 *   1. vocabulary  — restore canonical casing/spelling of known terms,
 *   2. snippets    — expand trigger phrases,
 *   3. curation    — spoken commands/punctuation, spacing, capitalisation, fillers.
 */
object Pipeline {

    data class Config(
        val vocabulary: List<String> = emptyList(),
        val snippets: Map<String, String> = emptyMap(),
        val corrections: Map<String, String> = emptyMap(),   // learned "heard → meant" fixes
        val curation: TextCuration.Options = TextCuration.Options(),
    )

    fun process(raw: String, config: Config): String {
        if (raw.isBlank()) return ""
        var text = raw
        text = Corrections.apply(text, config.corrections)    // learned fixes first (operate on raw words)
        text = Vocabulary.apply(text, config.vocabulary)
        text = Snippets.expand(text, config.snippets)
        text = TextCuration.curate(text, config.curation)
        return text
    }
}
