package com.vibeflow.mobile.core

/**
 * Pure routing decision: should finished text be *inserted* into a focused text
 * field, or *copied* to the clipboard? Ported from the desktop `output.py`.
 *
 * On mobile the "focus" signal is concrete: when dictation is driven from the
 * VibeFlow keyboard (IME) there is always an editable [InputConnection], so the
 * target is TYPE. When driven from the Quick Settings tile, floating mic, or the
 * app itself there is no connected field, so the target is CLIPBOARD.
 */
object OutputRouting {

    enum class Target { TYPE, CLIPBOARD }

    enum class Mode { AUTO, TYPE, CLIPBOARD }

    /**
     * @param mode user preference: AUTO (decide from focus), TYPE (always insert),
     *   or CLIPBOARD (always copy).
     * @param hasEditableTarget true when there is a live editable field to insert into.
     */
    fun decideTarget(mode: Mode, hasEditableTarget: Boolean): Target = when (mode) {
        Mode.TYPE -> Target.TYPE
        Mode.CLIPBOARD -> Target.CLIPBOARD
        Mode.AUTO -> if (hasEditableTarget) Target.TYPE else Target.CLIPBOARD
    }

    fun modeFromString(value: String?): Mode = when (value?.lowercase()?.trim()) {
        "type" -> Mode.TYPE
        "clipboard" -> Mode.CLIPBOARD
        else -> Mode.AUTO
    }

    /** Add a single trailing space so back-to-back dictations don't run together. */
    fun withTrailingSpace(text: String, enabled: Boolean): String {
        if (!enabled || text.isEmpty()) return text
        return if (text.last().isWhitespace()) text else "$text "
    }
}
