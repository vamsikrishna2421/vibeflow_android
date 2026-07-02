package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Test

class TextCurationTest {

    private fun curate(s: String, o: TextCuration.Options = TextCuration.Options()) =
        TextCuration.curate(s, o)

    @Test fun capitalizesFirstLetterAndSentences() {
        assertEquals("Hello world. How are you?", curate("hello world. how are you?"))
    }

    @Test fun fixesLonePronounI() {
        assertEquals("I think I'm right", curate("i think i'm right"))
    }

    @Test fun doesNotForceCapitalizeCaseBearingTokens() {
        // "iOS" begins a sentence-after token but must not become "IOS".
        assertEquals("iOS is great", curate("iOS is great", TextCuration.Options(capitalizeFirst = false)))
    }

    @Test fun spokenNewLineBecomesBreak() {
        assertEquals("line one\nline two", curate("line one new line line two",
            TextCuration.Options(capitalizeFirst = false, capitalizeSentences = false)))
    }

    @Test fun spokenParagraphBecomesDoubleBreak() {
        val out = curate("first new paragraph second",
            TextCuration.Options(capitalizeFirst = false, capitalizeSentences = false))
        assertEquals("first\n\nsecond", out)
    }

    @Test fun spokenPunctuationPeriodAndComma() {
        assertEquals("Hello, world.", curate("hello comma world period"))
    }

    @Test fun spokenQuestionMarkIsWholePhrase() {
        assertEquals("Really?", curate("really question mark"))
    }

    @Test fun removesStandaloneFillers() {
        val out = curate("um i think uh we should ship",
            TextCuration.Options(stripFillers = true))
        assertEquals("I think we should ship", out)
    }

    @Test fun keepsFillerInsideWord() {
        // "scrum" contains "um" but must be untouched.
        val out = curate("the scrum meeting",
            TextCuration.Options(stripFillers = true, capitalizeFirst = false, capitalizeSentences = false))
        assertEquals("the scrum meeting", out)
    }

    @Test fun autoPeriodAddsTerminalPunctuation() {
        assertEquals("Send it now.", curate("send it now", TextCuration.Options(autoPeriod = true)))
    }

    @Test fun autoPeriodSkipsWhenAlreadyPunctuated() {
        assertEquals("Done!", curate("done!", TextCuration.Options(autoPeriod = true)))
    }

    @Test fun normalizesSpacingAroundPunctuation() {
        assertEquals("Hi, there.", curate("hi , there ."))
    }

    @Test fun emptyStaysEmpty() {
        assertEquals("", curate(""))
    }
}
