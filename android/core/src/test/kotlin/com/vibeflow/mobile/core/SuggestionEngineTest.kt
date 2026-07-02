package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuggestionEngineTest {

    private lateinit var engine: SuggestionEngine

    @Before fun setup() {
        engine = SuggestionEngine()
        engine.loadBase(
            sequenceOf(
                "the 1000", "this 800", "that 700", "they 600", "there 500",
                "hello 400", "help 300", "world 250", "would 240", "work 230",
                "kubernetes 50", "kotlin 40",
            )
        )
    }

    @Test fun completesPrefixByFrequency() {
        val s = engine.suggest("th", max = 3)
        assertEquals(listOf("the", "this", "that"), s)
    }

    @Test fun preservesLeadingCapital() {
        val s = engine.suggest("Th", max = 1)
        assertEquals(listOf("The"), s)
    }

    @Test fun correctsSimpleTypo() {
        // "helo" -> "hello" (insert l)
        assertEquals("hello", engine.autocorrect("helo"))
    }

    @Test fun doesNotCorrectKnownWord() {
        assertNull(engine.autocorrect("world"))
    }

    @Test fun doesNotCorrectShortOrAcronym() {
        assertNull(engine.autocorrect("ok"))
        assertNull(engine.autocorrect("API"))
    }

    @Test fun learnedWordsRankAndComplete() {
        repeat(5) { engine.learn("kubernetes") }
        // even though base freq is low, learning lifts it for "kube" prefix
        val s = engine.suggest("kube", max = 1)
        assertEquals(listOf("kubernetes"), s)
        assertTrue(engine.knows("kubernetes"))
    }

    @Test fun learnsAndCorrectsToPersonalWord() {
        repeat(3) { engine.learn("vibeflow") }
        assertEquals("vibeflow", engine.autocorrect("vibeflo"))
    }
}
