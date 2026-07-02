# VibeFlow Mobile — Competitive Analysis & Feature Backlog

_Snapshot: June 2026. Built from a multi-agent research sweep across 21 mobile
voice-to-text products (cloud-AI dictation, on-device/offline keyboards, and iOS
on-device apps), with each decision-relevant claim ("is it really offline?",
"how does text actually reach other apps?") adversarially re-verified. Treat
prices and gated-rollout claims as fast-moving._

## VibeFlow Mobile in one line

A **free-to-run, 100%-offline Android voice keyboard** (a real IME with an
in-keyboard mic) that **types into any app**, **falls back to the clipboard**,
keeps a **local searchable history**, and turns raw on-device speech into clean,
punctuated, formatted text — **with no cloud, no account, and no internet
permission at all**.

## Why that positioning is strong

The market splits cleanly, and VibeFlow sits in the rare overlap:

- The **best-known AI dictation apps are cloud-only.** Wispr Flow, Aqua Voice,
  Willow, Otter, SwiftKey voice — all confirmed to send audio to servers.
- On **Android specifically**, the strongest cloud player **doesn't even ship a
  keyboard**: Wispr Flow's Android product is an **accessibility-service floating
  bubble**, not an IME. VibeFlow uses the canonical `InputConnection` text path —
  no accessibility hacks, no overlay.
- The **truly-offline Android apps are bare**: Sayboard (the closest match — a
  Vosk IME) emits **plain lowercase with no punctuation**; the Whisper-based ones
  (FUTO, Whisper IME, WhisperInput, Outspoke) are accurate but **heavy** (435 MB –
  1.8 GB models, multi-GB RAM) and mostly **lack history and formatting**.

So VibeFlow occupies an uncontested square: **offline + a real keyboard +
readable formatted output + history + lightweight (~50 MB)** — all at once.

---

## Comparison matrix (decision-relevant features)

Legend: ✅ yes · ⚠️ partial / mixed · ❌ no

| Product | Platform | Fully offline | Type-anywhere mechanism | History | Punctuation/format | Footprint |
|---|---|---|---|---|---|---|
| **VibeFlow Mobile** | **Android** (iOS planned) | ✅ on-device Vosk | ✅ **true IME** mic → `commitText`; clipboard fallback | ✅ local, searchable | ✅ spoken punctuation + deterministic + vocab/snippets | **~50 MB** |
| Wispr Flow | iOS/Android/desktop | ❌ cloud | ⚠️ iOS keyboard; **Android = accessibility bubble** | ✅ cloud | ✅ cloud AI | n/a |
| Aqua Voice | iOS/Mac/Win | ❌ cloud | ⚠️ iOS keyboard (app-bounce 1st use) | ⚠️ | ✅ cloud AI + NL edits | n/a |
| Willow Voice | iOS/desktop | ❌ cloud (opt. fallback) | ⚠️ iOS keyboard | ⚠️ | ✅ cloud AI | n/a |
| Otter.ai | iOS/Android/web | ❌ cloud | ❌ **not a keyboard** (export/copy) | ✅ cloud | ✅ summaries | n/a |
| Gboard voice | Android/iOS | ⚠️ offline packs / Pixel only | ✅ native IME (Android) | ❌ | ✅ auto + spoken | n/a |
| Apple Dictation | iOS/Mac | ⚠️ on-device subset, server fallback | ✅ system mic key | ❌ | ✅ auto + spoken | n/a |
| SwiftKey | Android/iOS | ❌ cloud (Azure) | ✅ native IME | ❌ | ⚠️ basic | n/a |
| **Sayboard** (FOSS) | Android | ✅ Vosk | ✅ true IME | ✅ | ❌ **plain lowercase** | ~50 MB |
| FUTO Voice Input | Android | ✅ Whisper | ✅ voice IME + intent | ✅ | ✅ Whisper | ~half-GB |
| Whisper IME (FOSS) | Android | ✅ Whisper | ✅ IME + RecognitionService | ❌ | ✅ Whisper | ~435 MB |
| WhisperInput (FOSS) | Android | ✅ (no INTERNET perm) | ✅ true IME | ❌ | ✅ Whisper | large |
| Outspoke (FOSS) | Android | ✅ Parakeet-TDT | ✅ true IME, live partials | ❌ | ✅ 8-step heuristic | ~700 MB + ~4 GB RAM |
| superwhisper | iOS/Mac/Win | ⚠️ on-device + opt-in cloud | ⚠️ iOS keyboard (host-app mic broker) | ✅ | ✅ auto + spoken | large |
| VoiceInk | iOS/Mac | ✅ default (opt-in cloud) | ⚠️ app-switch + paste keyboard | ✅ | ✅ post-processing | medium |
| Aiko | iOS/Mac | ✅ Whisper | ❌ **file transcriber only** | ❌ | ⚠️ rough | ~1.8 GB |

> **The honest caveat on "type anywhere":** no IME — VibeFlow included — can insert
> into password fields or surfaces that don't expose an `InputConnection` (some
> game/canvas/custom widgets). The clipboard fallback covers most of the rest.

---

## ✅ Where VibeFlow Mobile is AHEAD (defend & market these)

1. **Offline *and* a real keyboard, together, on Android** — the rare combination.
   Cloud leaders aren't private; the private ones on Android are bare. We lead with:
   *"works in airplane mode, types into any app — no bubble, no accessibility hack."*
2. **Offline + local searchable history** — most offline rivals have no history;
   cloud rivals' history lives on their servers. Ours never leaves the phone.
3. **Lightweight (~50 MB, low RAM)** — runs on old/cheap phones the Whisper/Parakeet
   crowd (435 MB – 1.8 GB) can't touch.
4. **Raw speech → readable text, on-device** — Sayboard (the closest offline IME)
   ships lowercase with no punctuation. VibeFlow's deterministic pipeline adds
   capitalization, spoken punctuation, vocabulary casing, and snippet expansion —
   no cloud LLM.
5. **Real-time partials + low latency** — Vosk streams; we show live text as you
   speak, where Whisper-based rivals are batch/per-utterance.

## ✅ Already shipped (gaps other reviews would flag — closed in v0.1)

These were called out as common gaps; VibeFlow Mobile **already has them**:

- **Live partial transcription** in the keyboard ✅
- **Filler-word removal** ("um/uh", conservative, toggleable) ✅
- **Spoken punctuation** ("comma/period/question mark") ✅
- **Spoken layout** ("new line / new paragraph") ✅
- **Searchable history** with pin/copy/clear ✅
- **Custom vocabulary** (casing/spelling) and **snippets** ✅
- **Clipboard fallback + Quick Settings tile** for no-field capture ✅
- **Voice editing commands** ("scratch that", "delete last word") ✅
- **Hands-free mode** — on-device VAD auto-stops when you pause ✅

## ❌ Where VibeFlow LAGS — the backlog

Effort = native Kotlin IME. Value: ★ → ★★★.

| Gap | Who has it | Value | Effort | Status |
|---|---|---|---|---|
| **Voice editing commands** ("scratch that", "delete last word") | Aqua, superwhisper, Willow | ★★ | Med | **✅ shipped in v0.1** |
| **Hands-free VAD auto start/stop** | Outspoke, superwhisper | ★★ | Med | **✅ shipped in v0.1** (adaptive energy VAD) |
| **Optional high-accuracy engine pack** (Whisper/Parakeet, downloadable) | FUTO, Outspoke | ★★★ | High | planned (opt-in, protects the lightweight default) |
| **System-wide `RecognitionService` + RECOGNIZE_SPEECH intent** | FUTO, Whisper IME, Kõnele | ★★ | Med | planned |
| **More on-device cleanup** (stutter/dup collapse) | Outspoke (heuristic) | ★★ | Low–Med | planned |
| **Multilingual / larger Vosk models** | Wispr, FUTO | ★★ | Med | planned (Vosk has 20+ models) |
| **Auto-learning user dictionary** | Willow, Aqua | ★★ | Med | planned |
| **iOS app** | Wispr, superwhisper, VoiceInk | ★★★ | High | source-complete skeleton in `ios/` |
| Cross-device sync | Wispr, superwhisper | ★ | High | deferred (conflicts with offline-first; only as opt-in E2EE) |
| Full keyboard surface (layouts/emoji/themes) | Gboard, SwiftKey | ★★ | High | deferred (voice-first by design) |

---

## Recommended next features (ordered)

1. **Optional high-accuracy engine pack** — a downloadable Whisper/Parakeet
   backend behind the existing `SpeechEngine` interface, so the default stays
   ~50 MB while power users opt into max accuracy.
2. **`RecognitionService` + voice-intent registration** — let users keep their
   favourite text keyboard and still invoke VibeFlow's offline engine anywhere.
3. **More deterministic cleanup** — stutter/word-repeat collapse on top of the
   current filler removal.
4. **Multilingual models + an auto-learning user dictionary.**

_Deliberately deferred:_ the engine upgrade is the highest-value gap but the
highest effort and it threatens the lightweight moat — it belongs as an optional
pack, not the default. Cross-device sync and a full keyboard surface are off-brand
for a privacy-first, voice-first product.

---

_Research method: 4 parallel category researchers → per-competitor adversarial
verification of offline/type-anywhere/price claims → synthesis. 21 products
covered._
