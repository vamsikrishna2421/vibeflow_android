package com.vibeflow.mobile.core

import kotlin.math.min

/**
 * Learns the user's vocabulary from their EDITS — the highest-quality signal there is.
 *
 * When VibeFlow dictates [baseline] and the user changes a word before sending ([final]),
 * we infer a "heard → meant" correction (e.g. "cubanet" → "Kubernetes") and can apply it
 * to future dictations in the curation pipeline. The Android system recognizer can't take
 * custom vocabulary, so this mapping is how the app learns your names and jargon.
 *
 * Conservative by design — the #1 failure mode is over-learning garbage:
 *  - only single-word substitutions (no rewrites: if the texts diverge a lot, learn nothing),
 *  - the replacement must be a real, meaningful word (length ≥ 3, not a stopword),
 *  - the original must NOT already be a known word (else it wasn't a fix),
 *  - the pair must be phonetically/edit-distance close OR the replacement is a proper noun.
 *
 * Pure & offline; the dictionary check is injected so it stays I/O-free and testable.
 */
object CorrectionLearner {

    data class Correction(val from: String, val to: String)   // from is lowercased; to keeps its case

    private val WS = Regex("\\s+")
    private val EDGE = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    private val STOPWORDS = setOf(
        "the", "a", "an", "of", "to", "in", "on", "at", "is", "it", "we", "he", "she", "they",
        "you", "i", "me", "my", "this", "that", "and", "or", "as", "be", "for", "so", "but",
        "was", "were", "are", "have", "has", "had", "will", "would", "not", "with", "from",
    )

    fun learn(baseline: String, final: String, knows: (String) -> Boolean): List<Correction> {
        val b = baseline.trim().split(WS).filter { it.isNotEmpty() }
        val f = final.trim().split(WS).filter { it.isNotEmpty() }
        if (b.isEmpty() || f.isEmpty()) return emptyList()

        val pairs = align(b, f)
        // "Limited change" guard: learn only from a few word fixes, never from a rewrite —
        // at most 3 changed words, and never more than half the line.
        val edits = pairs.count { (x, y) -> x == null || y == null || !x.equals(y, ignoreCase = true) }
        if (edits == 0 || edits > 3 || edits * 2 > b.size) return emptyList()

        val out = ArrayList<Correction>()
        for ((x, y) in pairs) {
            if (x == null || y == null || x.equals(y, ignoreCase = true)) continue
            val from = bare(x)
            val to = bare(y)
            if (from.length < 2 || to.length < 3) continue
            if (from.equals(to, ignoreCase = true)) continue
            if (to.lowercase() in STOPWORDS) continue          // never "fix" into a filler word
            if (knows(from)) continue                          // original was already valid
            val proper = to[0].isUpperCase()
            if (!proper && !similar(from.lowercase(), to.lowercase())) continue
            out.add(Correction(from.lowercase(), to))
        }
        return out
    }

    private fun bare(token: String): String = EDGE.replace(token, "")

    /** True when the two words are within a small edit distance (a plausible mishearing). */
    private fun similar(a: String, b: String): Boolean {
        val d = levenshtein(a, b)
        return d <= when {
            a.length <= 4 -> 1
            a.length <= 8 -> 2
            else -> 3
        }
    }

    private fun levenshtein(a: String, b: String): Int {
        val m = a.length; val n = b.length
        if (m == 0) return n; if (n == 0) return m
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..m) {
            cur[0] = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(prev[j] + 1, cur[j - 1] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[n]
    }

    private fun align(p: List<String>, s: List<String>): List<Pair<String?, String?>> {
        val n = p.size; val m = s.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (p[i - 1].equals(s[j - 1], ignoreCase = true)) 0 else 1
            dp[i][j] = min(min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost)
        }
        val out = ArrayList<Pair<String?, String?>>()
        var i = n; var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val cost = if (p[i - 1].equals(s[j - 1], ignoreCase = true)) 0 else 1
                if (dp[i][j] == dp[i - 1][j - 1] + cost) { out.add(p[i - 1] to s[j - 1]); i--; j--; continue }
            }
            if (i > 0 && dp[i][j] == dp[i - 1][j] + 1) { out.add(p[i - 1] to null); i--; continue }
            out.add(null to s[j - 1]); j--
        }
        out.reverse()
        return out
    }
}
