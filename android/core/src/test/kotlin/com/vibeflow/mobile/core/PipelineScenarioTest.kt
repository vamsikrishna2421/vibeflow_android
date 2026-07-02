package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Realistic end-to-end dictation scenarios — the kind of raw, lowercase,
 * unpunctuated text the offline engine actually emits — through the full
 * [Pipeline], using the same formatting defaults the shipping app uses
 * (auto-period ON, like [com.vibeflow.mobile.data.Settings]).
 */
class PipelineScenarioTest {

    /** Mirrors the app's default formatting (auto-period on). */
    private fun appOptions(stripFillers: Boolean = false) =
        TextCuration.Options(autoPeriod = true, stripFillers = stripFillers)

    private fun run(
        raw: String,
        vocabulary: List<String> = emptyList(),
        snippets: Map<String, String> = emptyMap(),
        stripFillers: Boolean = false,
    ) = Pipeline.process(
        raw,
        Pipeline.Config(vocabulary = vocabulary, snippets = snippets, curation = appOptions(stripFillers)),
    )

    @Test fun plainSentenceGetsCappedAndPunctuated() {
        assertEquals("Let's ship this today.", run("let's ship this today"))
    }

    @Test fun spokenPunctuationFormsCleanSentence() {
        assertEquals(
            "Hey, can you review the PR? Thanks.",
            run("hey comma can you review the pr question mark thanks period", vocabulary = listOf("PR")),
        )
    }

    @Test fun newParagraphProducesTwoSentences() {
        assertEquals("First thought\n\nSecond thought.", run("first thought new paragraph second thought"))
    }

    @Test fun vocabularyFixesCasingMidSentence() {
        assertEquals(
            "I pushed to GitHub and deployed to Kubernetes.",
            run("i pushed to github and deployed to kubernetes", vocabulary = listOf("GitHub", "Kubernetes")),
        )
    }

    @Test fun snippetExpandsThenSentenceIsTidy() {
        assertEquals(
            "Reach me at vamsy@example.com.",
            run("reach me at my email", snippets = mapOf("my email" to "vamsy@example.com")),
        )
    }

    @Test fun emailFromSnippetIsNotMangled() {
        // The periods inside the domain must survive curation's spacing rules.
        assertEquals(
            "Write to a.b@example.co.uk.",
            run("write to my email", snippets = mapOf("my email" to "a.b@example.co.uk")),
        )
    }

    @Test fun fillerRemovalOnByConfig() {
        assertEquals("So I think we should go.", run("um so i think uh we should go", stripFillers = true))
    }

    @Test fun pronounIVariantsFixed() {
        assertEquals("I'm sure I'll do it.", run("i'm sure i'll do it"))
    }

    @Test fun multipleSpacesAndStraySpacingNormalized() {
        assertEquals("Hello there friend.", run("hello   there    friend"))
    }

    @Test fun questionRetainsNoTrailingPeriod() {
        assertEquals("Are you coming?", run("are you coming question mark"))
    }

    @Test fun emptyAndWhitespaceStayEmpty() {
        assertEquals("", run("   "))
        assertEquals("", run(""))
    }
}
