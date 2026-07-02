package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the dynamic engine brain: dedupe (L1), hypothesis fusion (L2),
 * the reactive health detector, and the proactive router. Includes a demo that
 * runs the REAL low-voice transcripts captured on-device through the pipeline.
 */
class EngineBrainTest {

    // A small "dictionary" standing in for the real frequency list + learned vocab.
    private val realWords = setOf(
        "the", "quick", "brown", "fox", "jumps", "over", "lazy", "dog", "i", "scheduled",
        "meeting", "for", "march", "at", "in", "morning", "could", "you", "please", "send",
        "report", "to", "before", "noon", "we", "discussed", "github", "and", "new",
        "pricing", "model", "honestly", "think", "this", "approach", "is", "percent",
        "faster", "far", "more", "reliable", "would", "say", "known", "praising", "here",
    )
    private val knows: (String) -> Boolean = { it.lowercase() in realWords }

    // ---- L1: dedupe -------------------------------------------------------

    @Test fun collapsesRepeatedPhrase() {
        assertEquals(
            "please send the report before noon",
            TextCuration.collapseRepeats("please send the report please send the report before noon"),
        )
    }

    @Test fun keepsShortDeliberateRepeats() {
        // 1- and 2-word repeats are NOT collapsed (could be intentional).
        assertEquals("no no no", TextCuration.collapseRepeats("no no no"))
        assertEquals("very very good", TextCuration.collapseRepeats("very very good"))
    }

    // ---- L2: fusion -------------------------------------------------------

    @Test fun fusionRescuesGibberishWithRealWord() {
        // primary garbled "qubar"; secondary has the real word "github" → rescue it.
        val r = HypothesisFusion.fuse(
            primary = "we discussed qubar and the new model",
            secondary = "we discussed github and the new model",
            knows = knows,
        )
        assertEquals("we discussed github and the new model", r.text)
        assertEquals(1, r.rescued)
    }

    @Test fun fusionNeverOverwritesAGoodPrimaryWord() {
        // primary word is real → keep it even though secondary differs.
        val r = HypothesisFusion.fuse(
            primary = "send the report to noon",
            secondary = "send the report to moon",
            knows = knows,
        )
        assertEquals("send the report to noon", r.text)
        assertEquals(0, r.rescued)
    }

    @Test fun fusionPrefersCompletePrimary() {
        // secondary dropped words; primary is more complete → keep primary's extras.
        val r = HypothesisFusion.fuse(
            primary = "the quick brown fox jumps",
            secondary = "brown fox jumps",
            knows = knows,
        )
        assertEquals("the quick brown fox jumps", r.text)
    }

    // ---- Reactive health detector ----------------------------------------

    @Test fun healthyTranscriptIsNotDegraded() {
        val h = TranscriptHealth.assess("could you please send the report before noon", knows)
        assertFalse(h.looksDegraded)
    }

    @Test fun repeatedAndGibberishTranscriptIsDegraded() {
        val h = TranscriptHealth.assess(
            "send the report send the report xqz wbbl ndfp tkjs", knows,
        )
        assertTrue(h.looksDegraded)
    }

    // ---- Proactive router -------------------------------------------------

    @Test fun quietVoiceRoutesToWhisper() {
        assertEquals(EngineRouter.Engine.WHISPER, EngineRouter.routeByLevel(-34f).engine)
    }

    @Test fun normalVoiceRoutesToSystem() {
        assertEquals(EngineRouter.Engine.SYSTEM, EngineRouter.routeByLevel(-18f).engine)
    }

    // ---- Demo on the REAL on-device low-voice transcripts ----------------

    @Test fun demoRealLowVoicePipeline() {
        // Captured on the F22 at low voice (the 59% system output, with its duplication):
        val systemRaw =
            "fox jim fox jumps over the lazy dog i should do the meeting for march 3rd at 9.5 " +
            "in the morning would you please send the report would you please send the report " +
            "say before known the new praising model honest here i think this approached 20 " +
            "faster and far more reliable"

        // 1) dedupe + curate the system output alone (L1):
        val curated = TextCuration.curate(systemRaw)
        println("\n--- L1 (dedupe + curation) on the raw system low-voice output ---")
        println("BEFORE: $systemRaw")
        println("AFTER : $curated")

        // The duplicated clause must be gone, and it must be capitalised/punctuated.
        val dupCount = Regex("would you please send the report").findAll(curated.lowercase()).count()
        assertEquals(1, dupCount)
        assertTrue(curated.startsWith("Fox"))

        // 2) fuse with the (better) Whisper low-voice transcript as primary (L2):
        val whisper =
            "The quick brown fox jumps over the lazy dog. I scheduled the meeting for March 3rd " +
            "at 9:45 in the morning. Could you please send the report to Vamsi before noon. We " +
            "discussed Kubernetes GitHub and the new pricing model. Honestly I think this " +
            "approach is 20 percent faster and far more reliable."
        val fused = HypothesisFusion.fuse(primary = whisper, secondary = systemRaw, knows = knows)
        println("\n--- L2 (Whisper-primary, system-rescue) ---")
        println("FUSED : ${fused.text}  [rescued=${fused.rescued}]")
        // Fusion must keep Whisper's complete, correct sentence structure.
        assertTrue(fused.text.contains("quick brown fox"))
    }
}
