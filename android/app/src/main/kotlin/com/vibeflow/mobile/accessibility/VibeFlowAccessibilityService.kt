package com.vibeflow.mobile.accessibility

import android.accessibilityservice.AccessibilityService
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/**
 * Opt-in Accessibility service powering the "auto-insert" experience (off by default).
 * It does two narrow things, ONLY when the user has explicitly enabled it:
 *   1. Reports when an editable text field is focused, so the floating mic can appear
 *      with the keyboard and hide when it closes.
 *   2. Inserts dictated text straight at the cursor of the focused field (and can swap
 *      the instantly-inserted "cleaned" text for the AI-"polished" version when it lands).
 *
 * It never reads or transmits screen content beyond the single focused input field it's
 * writing into. All coordination goes through [AutoInsert]; the floating mic talks to that.
 */
class VibeFlowAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        AutoInsert.service = this
        AutoInsert.onReadyChanged?.invoke()
        AutoInsert.notifyFocus(isKeyboardOpen())
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Track the SOFT-KEYBOARD window's visibility, not just field focus — a field stays focused
        // when the keyboard is dismissed, so focus alone would leave the bubble lingering.
        AutoInsert.notifyFocus(isKeyboardOpen())
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (AutoInsert.service === this) {
            AutoInsert.service = null
            AutoInsert.notifyFocus(false)
            AutoInsert.onReadyChanged?.invoke()
        }
        super.onDestroy()
    }

    // --- internals ---------------------------------------------------------

    private fun focusedInput(): AccessibilityNodeInfo? =
        runCatching { findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()?.takeIf { it.isEditable }

    /** True while a soft-keyboard (IME) window is showing — drives the bubble's visibility. */
    private fun isKeyboardOpen(): Boolean =
        runCatching { windows?.any { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD } == true }.getOrDefault(false)

    /** Insert [text] at the cursor of the focused field. Returns a handle for a later [replaceInserted], or null. */
    fun insertAtCursor(text: String): InsertHandle? {
        val node = focusedInput() ?: return null
        // An EMPTY field reports its placeholder (e.g. "Text message") via node.text — that's the
        // HINT, not real content. Treat a hint-showing field as empty so we don't prepend it.
        val showingHint = android.os.Build.VERSION.SDK_INT >= 26 && node.isShowingHintText
        val existing = if (showingHint) "" else (node.text?.toString() ?: "")
        val n = existing.length
        val selEnd = node.textSelectionEnd.let { if (it in 0..n) it else n }
        val selStart = node.textSelectionStart.let { if (it in 0..n) it else selEnd }
        val before = existing.substring(0, minOf(selStart, n))
        val after = existing.substring(minOf(selEnd, n))
        // Add a separating space when joining onto existing non-space text.
        val sep = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val chunk = sep + text
        val ok = setText(node, before + chunk + after)
        val cursor = before.length + chunk.length
        if (ok) setSelection(node, cursor)
        return if (ok) InsertHandle(before.length, chunk) else null
    }

    /** Swap the previously-inserted chunk for [newText] — but only if the field still has it
     *  untouched at the same spot (so we never clobber edits the user made in the meantime). */
    fun replaceInserted(handle: InsertHandle, newText: String): Boolean {
        val node = focusedInput() ?: return false
        val existing = node.text?.toString() ?: ""
        val end = handle.start + handle.chunk.length
        if (end > existing.length || existing.substring(handle.start, end) != handle.chunk) return false
        val sep = handle.chunk.firstOrNull()?.takeIf { it.isWhitespace() }?.toString() ?: ""
        val replacement = sep + newText.trimStart()
        val ok = setText(node, existing.substring(0, handle.start) + replacement + existing.substring(end))
        if (ok) setSelection(node, handle.start + replacement.length)
        return ok
    }

    private fun setText(node: AccessibilityNodeInfo, text: String): Boolean {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun setSelection(node: AccessibilityNodeInfo, pos: Int) {
        val args = Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, pos)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, pos)
        }
        runCatching { node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, args) }
    }
}

/** Where the inserted chunk sits, so it can be swapped for the polished version later. */
data class InsertHandle(val start: Int, val chunk: String)

/**
 * Process-wide bridge between the floating mic and the (optional) Accessibility service.
 * The service registers itself here; the floating mic reads focus + drives insertion through it.
 */
object AutoInsert {

    @Volatile var service: VibeFlowAccessibilityService? = null

    /** True once the user has enabled the Accessibility service (it's connected). */
    val isReady: Boolean get() = service != null

    @Volatile var fieldFocused: Boolean = false
        private set

    /** The floating mic sets this to show/hide its bubble as text fields gain/lose focus. */
    @Volatile var onEditableFocus: ((Boolean) -> Unit)? = null

    /** Fired when the service connects/disconnects, so the floating mic can re-evaluate its mode live. */
    @Volatile var onReadyChanged: (() -> Unit)? = null

    fun notifyFocus(focused: Boolean) {
        if (focused == fieldFocused) return
        fieldFocused = focused
        onEditableFocus?.invoke(focused)
    }

    fun insert(text: String): InsertHandle? = service?.insertAtCursor(text)

    fun swap(handle: InsertHandle, newText: String): Boolean = service?.replaceInserted(handle, newText) ?: false
}
