package com.vibeflow.mobile.core

/**
 * Predictive text + autocorrect that **learns your vocabulary** — the engine that
 * lets VibeFlow feel like the everyday keyboard you're used to.
 *
 *  - **Completions:** as you type a prefix, the most likely words (ranked by a
 *    bundled frequency list + your own learned words, which always win).
 *  - **Corrections:** a Norvig-style edit-distance speller fixes small typos.
 *  - **Learning:** every word you commit boosts its rank, so your everyday words
 *    (in any language/script you type) rise to the top — no English lock-in.
 *
 * Pure, offline, and unit-tested. The base dictionary and the learned map are fed
 * in by the app layer (assets + persisted JSON).
 */
class SuggestionEngine {

    private val base = HashMap<String, Long>()        // word -> frequency
    private var sorted: List<Pair<String, Long>> = emptyList()  // base, freq desc
    private val learned = HashMap<String, Long>()     // word -> learned count
    private var maxBaseFreq = 1L

    val isLoaded: Boolean get() = sorted.isNotEmpty()

    /** Load the base dictionary from "word<space>frequency" lines. */
    fun loadBase(lines: Sequence<String>) {
        base.clear()
        for (line in lines) {
            val sp = line.indexOf(' ')
            if (sp <= 0) continue
            val w = line.substring(0, sp).lowercase()
            val f = line.substring(sp + 1).trim().toLongOrNull() ?: continue
            if (w.isEmpty() || !w.all { it.isLetter() || it == '\'' }) continue
            base[w] = f
        }
        maxBaseFreq = base.values.maxOrNull() ?: 1L
        sorted = base.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }

    fun setLearned(map: Map<String, Long>) {
        learned.clear(); learned.putAll(map)
    }

    /** A copy of the learned vocabulary (for persistence / export). */
    fun learnedSnapshot(): Map<String, Long> = HashMap(learned)

    /** Record a committed word; returns the updated learned map to persist. */
    fun learn(word: String): Map<String, Long> {
        val w = word.lowercase().trim()
        if (w.length < 2 || w.any { it.isDigit() }) return learned
        learned[w] = (learned[w] ?: 0L) + 1L
        return learned
    }

    fun knows(word: String): Boolean {
        val w = word.lowercase()
        return base.containsKey(w) || learned.containsKey(w)
    }

    private fun score(word: String): Long {
        val w = word.lowercase()
        val b = base[w] ?: 0L
        // Each personal use is weighted heavily so your words outrank the corpus.
        val l = (learned[w] ?: 0L) * (maxBaseFreq / 20 + 1000)
        return b + l
    }

    /**
     * Suggestions for the in-progress [raw] word. Returns up to [max] words,
     * re-cased to match the input (Title or UPPER preserved).
     */
    fun suggest(raw: String, max: Int = 3): List<String> {
        val word = raw.trim()
        if (word.isEmpty()) return emptyList()
        val lower = word.lowercase()
        val out = LinkedHashSet<String>()

        // 1) learned words that complete the prefix (personal first)
        learned.keys.asSequence()
            .filter { it.startsWith(lower) && it != lower }
            .sortedByDescending { score(it) }
            .take(max)
            .forEach { out.add(it) }

        // 2) base completions (freq-sorted scan with early exit)
        var scanned = 0
        for ((w, _) in sorted) {
            if (out.size >= max * 4) break
            if (scanned++ > 12000) break
            if (w.startsWith(lower) && w != lower) out.add(w)
        }

        // 3) if the prefix is itself a real word, keep it as a candidate
        if (knows(lower)) out.add(lower)

        // 4) corrections when there are few completions or it's an unknown word
        if (out.size < max || !knows(lower)) {
            corrections(lower).forEach { out.add(it) }
        }

        return out.asSequence()
            .distinct()
            .sortedByDescending { score(it) + if (it == lower) 1 else 0 }
            .take(max)
            .map { recase(word, it) }
            .toList()
    }

    /** Best autocorrection for a finished [word], or null to leave it as-is. */
    fun autocorrect(word: String): String? {
        val w = word.trim()
        if (w.length < 3 || w.length > 18) return null   // >18: pathological, skip (edits² explodes)
        if (w.any { it.isDigit() }) return null
        if (w.any { it.isUpperCase() } && w != w.lowercase()) return null  // names/acronyms
        val lower = w.lowercase()
        if (knows(lower)) return null                       // already a known word
        val best = corrections(lower).firstOrNull() ?: return null
        // Only correct with reasonable confidence (the candidate is a common word).
        if ((base[best] ?: 0L) < maxBaseFreq / 100000 && !learned.containsKey(best)) return null
        return recase(w, best)
    }

    // --- spelling corrections (Norvig) ---

    private fun corrections(word: String): List<String> {
        if (word.length > 18) return emptyList()             // pathological length — skip entirely
        val e1 = edits1(word)
        val known1 = e1.filter { knows(it) }
        // edits² is O(candidates²) — hundreds of thousands of lookups for long words.
        // Only worth it for short words; for longer ones, edits¹ alone (or nothing).
        val pool = when {
            known1.isNotEmpty() -> known1
            word.length <= 8 -> e1.asSequence().flatMap { edits1(it).asSequence() }.filter { knows(it) }.toList()
            else -> emptyList()
        }
        return pool.distinct().sortedByDescending { score(it) }.take(4)
    }

    private val alphabet = "abcdefghijklmnopqrstuvwxyz'".toCharArray()

    private fun edits1(word: String): Set<String> {
        val res = HashSet<String>()
        val n = word.length
        for (i in 0..n) {
            // deletes
            if (i < n) res.add(word.substring(0, i) + word.substring(i + 1))
            // transposes
            if (i < n - 1) res.add(word.substring(0, i) + word[i + 1] + word[i] + word.substring(i + 2))
            for (c in alphabet) {
                // replaces
                if (i < n) res.add(word.substring(0, i) + c + word.substring(i + 1))
                // inserts
                res.add(word.substring(0, i) + c + word.substring(i))
            }
        }
        // bound the set so edit-distance-2 expansion stays cheap
        return if (res.size > 4000) res.asSequence().take(4000).toSet() else res
    }

    /** Re-case [candidate] to follow the casing pattern of [input]. */
    private fun recase(input: String, candidate: String): String {
        if (input.isEmpty()) return candidate
        return when {
            input.all { it.isUpperCase() } && input.length > 1 -> candidate.uppercase()
            input[0].isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
            else -> candidate
        }
    }
}
