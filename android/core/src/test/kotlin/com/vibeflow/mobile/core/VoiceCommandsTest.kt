package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandsTest {

    @Test fun recognizesScratchThat() {
        assertEquals(VoiceCommands.Command.DELETE_LAST, VoiceCommands.parse("scratch that"))
        assertEquals(VoiceCommands.Command.DELETE_LAST, VoiceCommands.parse("Scratch that."))
        assertEquals(VoiceCommands.Command.DELETE_LAST, VoiceCommands.parse("  delete that  "))
    }

    @Test fun recognizesDeleteLastWord() {
        assertEquals(VoiceCommands.Command.DELETE_WORD, VoiceCommands.parse("delete last word"))
        assertEquals(VoiceCommands.Command.DELETE_WORD, VoiceCommands.parse("delete word"))
    }

    @Test fun recognizesLayoutCommands() {
        assertEquals(VoiceCommands.Command.NEW_LINE, VoiceCommands.parse("new line"))
        assertEquals(VoiceCommands.Command.NEW_PARAGRAPH, VoiceCommands.parse("new paragraph"))
    }

    @Test fun doesNotTriggerMidSentence() {
        // The phrase must be the WHOLE utterance, not a substring.
        assertNull(VoiceCommands.parse("please scratch that itch"))
        assertNull(VoiceCommands.parse("add a new line item to the list"))
        assertNull(VoiceCommands.parse("hello world"))
    }

    @Test fun blankIsNull() {
        assertNull(VoiceCommands.parse(""))
        assertNull(VoiceCommands.parse("   "))
    }
}
