package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionLearnerTest {

    private val dict = setOf(
        "we", "discussed", "and", "the", "new", "pricing", "model", "send", "report",
        "to", "before", "noon", "please", "the", "meeting",
    )
    private val knows: (String) -> Boolean = { it.lowercase() in dict }

    // --- learning ----------------------------------------------------------

    @Test fun learnsProperNounFix() {
        val c = CorrectionLearner.learn(
            baseline = "we discussed cubanet and the new model",
            final = "we discussed Kubernetes and the new model",
            knows = knows,
        )
        assertEquals(listOf(CorrectionLearner.Correction("cubanet", "Kubernetes")), c)
    }

    @Test fun learnsCloseMishearing() {
        val c = CorrectionLearner.learn("send the repot", "send the report", knows)
        assertEquals(listOf(CorrectionLearner.Correction("repot", "report")), c)
    }

    @Test fun ignoresFullRewrite() {
        // Many edits → a rewrite, not corrections → learn nothing.
        val c = CorrectionLearner.learn(
            baseline = "the cat sat on the mat",
            final = "please send me the quarterly budget report tomorrow",
            knows = knows,
        )
        assertTrue(c.isEmpty())
    }

    @Test fun doesNotLearnWhenOriginalIsAKnownWord() {
        val c = CorrectionLearner.learn("send the report", "send the message", knows)
        assertTrue(c.isEmpty())   // "report" is known → user just changed wording, not a fix
    }

    @Test fun doesNotLearnIntoStopword() {
        val c = CorrectionLearner.learn("discussed xyzzy stuff", "discussed the stuff", knows)
        assertTrue(c.isEmpty())   // "the" is a stopword → never a learned target
    }

    // --- applying ----------------------------------------------------------

    @Test fun appliesCorrectionWholeWordCaseInsensitive() {
        val map = mapOf("cubanet" to "Kubernetes")
        assertEquals("We use Kubernetes daily", Corrections.apply("We use Cubanet daily", map))
    }

    @Test fun doesNotApplyInsideWords() {
        val map = mapOf("ana" to "Anna")
        assertEquals("banana", Corrections.apply("banana", map))   // 'ana' inside 'banana' untouched
    }

    @Test fun appliesViaPipeline() {
        val cfg = Pipeline.Config(corrections = mapOf("cubanet" to "Kubernetes"))
        val out = Pipeline.process("we discussed cubanet today", cfg)
        assertTrue(out.contains("Kubernetes"))
    }
}
