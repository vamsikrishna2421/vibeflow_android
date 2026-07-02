package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class RoutingAndVocabTest {

    @Test fun autoModeTypesWhenEditable() {
        assertEquals(OutputRouting.Target.TYPE,
            OutputRouting.decideTarget(OutputRouting.Mode.AUTO, hasEditableTarget = true))
    }

    @Test fun autoModeCopiesWhenNoField() {
        assertEquals(OutputRouting.Target.CLIPBOARD,
            OutputRouting.decideTarget(OutputRouting.Mode.AUTO, hasEditableTarget = false))
    }

    @Test fun clipboardModeAlwaysCopies() {
        assertEquals(OutputRouting.Target.CLIPBOARD,
            OutputRouting.decideTarget(OutputRouting.Mode.CLIPBOARD, hasEditableTarget = true))
    }

    @Test fun trailingSpaceAddedOnce() {
        assertEquals("hi ", OutputRouting.withTrailingSpace("hi", true))
        assertEquals("hi ", OutputRouting.withTrailingSpace("hi ", true))
        assertEquals("hi", OutputRouting.withTrailingSpace("hi", false))
    }

    @Test fun vocabularyRestoresCanonicalCasing() {
        assertEquals("I pushed to GitHub with kubectl",
            Vocabulary.apply("i pushed to github with kubectl", listOf("GitHub", "kubectl"))
                .let { TextCuration.curate(it) })
    }

    @Test fun vocabularyMultiWordTerm() {
        assertEquals("Open Visual Studio Code",
            Vocabulary.apply("open visual studio code", listOf("Visual Studio Code"))
                .let { TextCuration.curate(it) })
    }

    @Test fun snippetExpandsWholePhrase() {
        assertEquals("Reach me at you@example.com",
            Snippets.expand("reach me at my email", mapOf("my email" to "you@example.com"))
                .let { TextCuration.curate(it) })
    }

    @Test fun pipelineRunsAllStages() {
        val cfg = Pipeline.Config(
            vocabulary = listOf("VibeFlow"),
            snippets = mapOf("my tool" to "VibeFlow"),
            curation = TextCuration.Options(stripFillers = true),
        )
        assertEquals("VibeFlow is fast.", Pipeline.process("um my tool is fast period", cfg))
    }
}
