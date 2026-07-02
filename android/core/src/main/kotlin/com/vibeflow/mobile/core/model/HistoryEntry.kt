package com.vibeflow.mobile.core.model

import kotlinx.serialization.Serializable

/**
 * One dictation, stored locally. `app` records where it went (package label or
 * "Clipboard"); `target` is "typed" or "clipboard". Mirrors the desktop history
 * schema so behaviour is consistent across platforms.
 *
 * Every capture keeps its pipeline STAGES so the user can go back and pick whichever
 * is best — and never has to re-speak:
 *  - [raw]      the engine's transcription (before curation),
 *  - [text]     the delivered "clean" text (L1/L2 curation),
 *  - [polished] the L3 LLM structuring (empty until Smart Formatting runs).
 * New fields default to empty, so older saved histories deserialize unchanged.
 */
@Serializable
data class HistoryEntry(
    val id: Long,
    val ts: Long,          // epoch seconds
    val text: String,      // the "Clean" stage (delivered text)
    val app: String = "",
    val pkg: String = "",      // destination app package id (for its launcher icon); blank when unknown
    val target: String = "",   // "typed" | "clipboard"
    val pinned: Boolean = false,
    val raw: String = "",        // the "Raw" stage (engine output before curation)
    val polished: String = "",   // the "Polished" stage (L3 LLM; empty until it runs)
    val durationSec: Float = 0f, // capture length, when known
    val promptTokens: Int = 0,   // L3 usage (shown only in History; TEMP cost readout)
    val completionTokens: Int = 0,
)
