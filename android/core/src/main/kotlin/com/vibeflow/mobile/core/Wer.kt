package com.vibeflow.mobile.core

/**
 * Word Error Rate — the standard ASR accuracy metric. Normalizes both strings
 * (lowercase, strip punctuation, collapse spaces), then computes the Levenshtein
 * edit distance over word tokens: WER = (substitutions + insertions + deletions)
 * / reference_word_count. Lower is better; 0 = perfect.
 */
object Wer {

    data class Result(val wer: Double, val refWords: Int, val edits: Int) {
        val accuracyPercent: Int get() = ((1.0 - wer).coerceIn(0.0, 1.0) * 100).toInt()
    }

    fun normalize(text: String): List<String> =
        text.lowercase()
            .replace(Regex("[^\\p{L}\\p{Nd}\\s']"), " ")
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

    fun compute(reference: String, hypothesis: String): Result {
        val ref = normalize(reference)
        val hyp = normalize(hypothesis)
        if (ref.isEmpty()) return Result(if (hyp.isEmpty()) 0.0 else 1.0, 0, hyp.size)
        val edits = levenshtein(ref, hyp)
        return Result(edits.toDouble() / ref.size, ref.size, edits)
    }

    private fun levenshtein(a: List<String>, b: List<String>): Int {
        val n = a.size
        val m = b.size
        var prev = IntArray(m + 1) { it }
        var cur = IntArray(m + 1)
        for (i in 1..n) {
            cur[0] = i
            for (j in 1..m) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[m]
    }
}
