package com.vibeflow.mobile.core

/**
 * Combines two transcriptions of the same audio into one, **safely**.
 *
 * The two engines are NOT equals — one is the trusted primary (e.g. Whisper, which
 * is far more accurate in the low-voice case) and the other is the secondary (the
 * system recognizer). Naive word-voting between an accurate and an inaccurate
 * hypothesis can drag the good one down, so the rule is asymmetric:
 *
 *   keep the PRIMARY word everywhere, EXCEPT where the primary word is gibberish
 *   (not a known word) and the secondary's aligned word is a real word — then let
 *   the secondary "rescue" that single spot.
 *
 * This captures the upside (the secondary fixes a primary garble) without the
 * downside (it can never overwrite a good primary word). Pure, offline, unit-tested.
 */
object HypothesisFusion {

    data class Result(val text: String, val rescued: Int)

    private val WS = Regex("\\s+")
    private val EDGE_PUNCT = Regex("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$")

    // A garbled proper noun ("Kubernetes", "Vamsi") looks "unknown" to a dictionary,
    // so without this guard the fusion would happily replace it with a common word
    // ("say", "you") from the weaker transcript — a downgrade. We therefore refuse to
    // rescue WITH a function word; real corrections are almost always content words.
    private val STOPWORDS = setOf(
        "the", "a", "an", "of", "to", "in", "on", "at", "is", "it", "we", "he", "she",
        "they", "you", "i", "me", "my", "your", "this", "that", "and", "or", "as", "be",
        "by", "for", "so", "but", "if", "then", "than", "there", "here", "was", "were",
        "are", "am", "do", "does", "did", "have", "has", "had", "will", "would", "could",
        "should", "not", "no", "yes", "say", "said", "with", "from", "what", "who", "how",
    )

    private fun tokenize(s: String): List<String> = s.trim().split(WS).filter { it.isNotEmpty() }
    private fun bare(token: String): String = EDGE_PUNCT.replace(token, "")

    /**
     * @param primary   the trusted transcript (kept by default)
     * @param secondary the helper transcript (used only to rescue gibberish)
     * @param knows     predicate: is this a real/known word? (dictionary + learned vocab)
     */
    fun fuse(primary: String, secondary: String, knows: (String) -> Boolean): Result {
        val p = tokenize(primary)
        val s = tokenize(secondary)
        if (p.isEmpty()) return Result(secondary.trim(), 0)
        if (s.isEmpty()) return Result(primary.trim(), 0)

        val out = ArrayList<String>(p.size)
        var rescued = 0
        for ((pt, st) in align(p, s)) {
            when {
                pt != null && st != null -> {
                    val bs = bare(st)
                    val canRescue = !knows(bare(pt)) && bs.length >= 3 &&
                        bs.lowercase() !in STOPWORDS && knows(bs)
                    if (pt.equals(st, ignoreCase = true)) out.add(pt)
                    else if (canRescue) { out.add(st); rescued++ }
                    else out.add(pt)
                }
                pt != null -> out.add(pt)   // primary has a word secondary lacks → keep it
                else -> { /* secondary-only insertion → drop (primary is more complete) */ }
            }
        }
        return Result(out.joinToString(" "), rescued)
    }

    /** Levenshtein backtrace → aligned (primaryToken?, secondaryToken?) pairs. */
    private fun align(p: List<String>, s: List<String>): List<Pair<String?, String?>> {
        val n = p.size; val m = s.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i
        for (j in 0..m) dp[0][j] = j
        for (i in 1..n) for (j in 1..m) {
            val cost = if (p[i - 1].equals(s[j - 1], ignoreCase = true)) 0 else 1
            dp[i][j] = minOf(dp[i - 1][j - 1] + cost, dp[i - 1][j] + 1, dp[i][j - 1] + 1)
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
