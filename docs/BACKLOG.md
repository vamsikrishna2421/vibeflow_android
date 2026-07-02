# VibeFlow — Backlog (pending / deferred work)

The running list of everything not yet done, so it's never forgotten. Ordered roughly by priority.

## Big features (need setup or are large efforts)
- [x] **Managed tier (Supabase)** — DONE end-to-end (2026-06-27): project `vibeflow`, `polish` Edge Function proxy (holds VibeFlow's OpenAI key), `profiles` + atomic metering (`reserve_polish`/`record_tokens`/`refund_polish`), Google sign-in, 50-free-trial + `is_pro` flag. App: `smartFormatTier` (private/byok/managed) + sign-in UI + dispatch. *Remaining:* (a) **billing** via Google Play Billing/RevenueCat — NOT a Vercel checkout (Play policy); (b) live "X free left" readout in Settings; (c) **ops dashboard** over Supabase (reuse Lucy's). Rotate the Supabase management token + Google web-client secret used during setup.
- ~~**Local LLM (on-device polish)**~~ — **CUT (final, 2026-06-27).** Decision (Vamsi + Claude): heat/battery/perf/quality risk on mid- & low-end Android outweighs the value. NOT building it. Private Mode = clean on-device L1/L2 (nothing leaves the device); AI polish is remote only.
- [ ] **Audio capture + re-process** — store compressed (Opus) audio per capture so any engine/L3 can re-run from the recording (true "never re-speak"). Blocked by the system-recognizer mic-sharing constraint; needs its own capture path. + storage caps/purge.

## Managed-tier follow-ups
- [x] ~~Cloud history (shared across devices)~~ — **DROPPED (2026-06-27).** History is offline-first by design; Vamsi confirmed no cloud caching needed. (Device-sharing abuse is already bounded by the per-seat limit.)
- [x] **Per-time rate limit for Pro** — DONE via the daily request cap (see Anti-abuse §).
- [ ] **Ops/monitoring dashboard** (reuse Lucy's, Next.js on Vercel over Supabase) — users, polishes/day, token spend → $, free-vs-pro, errors. **Include a one-click maintenance toggle** (just runs the `app_status` UPDATE we already support) + message box to broadcast downtime.
- [x] **Live "X free polishes left" readout** — DONE. Settings ▸ Smart Formatting (managed, signed in) shows "✨ N free polishes left" / "resets weekly" / "Pro · unlimited", computed from the user's own profile + config (RLS-read, no quota consumed), refreshed on each Settings open.

## Updates / distribution
- **DECISION (2026-06-27): Play-compliant only — NO sideload self-updater, no remote-code execution.** Stick to Play policies. So:
  - [ ] **Server-driven config** — route logic/config through the server (prompts, limits, model, feature flags, kill-switch — some already done) so most changes ship instantly without a store push. This is our "OTA-equivalent."
  - [ ] **Play in-app updates** (`AppUpdateManager`) for actual app/native updates once published.
  - Note: no true OTA for native Kotlin (unlike Lucy's Expo/RN) — accepted tradeoff for native IME/speech/overlay capability.
  - Note: subscriptions must use **Play Billing** (already decided, not a Vercel checkout). Switch from the committed sideload keystore to **Play App Signing** for production (see release-signing item).
  - (`adb install` stays our dev-test path — that's local testing, not distribution; the decision is about not shipping a self-updater.)

## Anti-abuse (frictionless — decided 2026-06-27, NOT mandatory phone)
Decision: phone-at-signup rejected (friction + SMS cost > ~$0.15/account exposure; Google already phone-gates account creation). Layer cheap, zero-friction defenses instead:
- [x] **Free-tier buckets + device-scoped pool** — DONE & verified (2026-06-27). Welcome 50 (lifetime) → 20/week (rolling 7d), server-configurable via `app_status` (welcome_limit/weekly_limit/device_cap). `device_usage` table shares a pool per physical install (kills many-accounts-one-phone farm). `signOut` preserves `device_id`.
- [ ] **Play Integrity API** — block emulators / tampered apps / bot farms (Android attestation + server verify). Bigger effort.
- [x] **Daily request rate-limit** — DONE (2026-06-27). `reserve_polish` caps requests/day per user via `profiles.day_used`/`day_start` vs `app_status.daily_limit` (500, live-tunable), resets at UTC midnight, applies even to Pro. Not refunded on failure → also throttles retry-hammering. Verified live.
- Phone only at the PAID step (billing identity) if ever.

## Premium UI overhaul (in progress — adaptive light+dark; design-system-first)
Refs from Vamsi: Notion (splash/onboarding/paywall/account restraint) + WhatsApp (grouped cards, chat-list rows = "familiar = addictive"). Principle: whitespace, one bold idea/screen, strong type hierarchy, one gradient accent.
- [x] **Design system** — `ui/components/Design.kt` (Dimens, brandBrush, BrandMark, SectionHeader, GroupCard, SettingsRow, PrimaryButton). Theme already adaptive.
- [x] **Splash** (~3s logo→wordmark→"Polish with AI" gradient tagline).
- [x] **Onboarding**: value carousel (3 cards + progress + Next) → Sign-in (Google + legal text) → Setup checklist (live ✓/○). DONE — shows after splash on first run (`onboardingDone` flag). The sign-in screen's "Terms" & "Privacy Policy" are now tappable links (modern `withLink`/`LinkAnnotation`) opening the in-app `LegalScreen` as a self-contained overlay.
- [x] **History** → WhatsApp chat-list pattern — DONE. Search pill + filter chips (All/Pinned/AI-polished) + rich rows (initial-letter avatar, dictation preview + timestamp, app subtitle with ✨ for polished), long-press → Copy/Pin/Delete menu, inset dividers. *(2026-06-27 bugfix: destination-app icons showed the fallback mic for all apps — root cause was missing `<queries>`; added the LAUNCHER intent query so the main app can resolve other apps' icons on Android 11+.)*
- [x] **Settings** → icon tiles + profile header — DONE. Each collapsible section header has a leading brand-tinted icon tile (Person/Checklist/Send/AutoAwesome/Tune/Spellcheck/RecordVoiceOver/Science/Adjust/Keyboard/Info); a WhatsApp-style profile header on top (gradient avatar + name + signed-in email/status, tap to edit).
- [x] **Account sheet** — DONE. Tapping the Settings profile header opens a Notion-style bottom sheet (gradient avatar + name + Google email/status, a Plan/quota card, and actions: Edit profile, Sign in/Sign out). `ModalBottomSheet` + `SheetAction` rows in `SettingsScreen.kt`.
- [ ] **Paywall** (Notion-style) — wires up with Google Play Billing.
- [x] **Legal** — DONE. Privacy Policy + Terms drafted (reflect real practices: on-device speech, optional cloud proxy, local history, managed-tier account data). In-app viewer (`LegalScreen.kt`) linked from Settings ▸ About. Hostable public pages added to the dashboard (`/legal/privacy`, `/legal/terms`, exempt from admin gate). *Before launch: set a real contact email (currently `support@vibeflow.app` placeholder in `LegalScreen.kt` + `dashboard/lib/legal.js` — keep both in sync), have a lawyer review, and link the onboarding "Terms & Privacy" text to the screens (still plain text).*

## Adoption wedge
- [x] **Clipboard paste chip** — DONE (Gboard-style "📋" chip in the suggestion strip).
- [x] **Floating dictation button (overlay)** — DONE. Step 1: draggable bubble → dictate → clipboard. **Step 2 (Accessibility auto-insert) — DONE (2026-06-27), opt-in + disclosed (Wispr parity):** new `accessibility/VibeFlowAccessibilityService` + `AutoInsert` bridge. When the user enables it (Settings ▸ Floating mic ▸ "Auto-insert at cursor" → disclosure dialog → Accessibility settings), the bubble **syncs to the keyboard** (visible only while an editable field is focused) and dictation is **inserted at the cursor** — **progressive**: the cleaned text lands instantly (zero gap), then `AutoInsert.swap` replaces it with the polished version when the AI returns (only if untouched; clipboard fallback otherwise). OFF by default; narrow service config (focus/selection events only); description states it only writes into the focused field. Files: service + config XML + manifest + strings + `Settings.autoInsert` + Settings toggle/dialog + `FloatingMicService` rewire. **Play risk accepted** (Accessibility for a productivity feature) — mitigated by opt-in + disclosure; revisit before submission.

## Parity features
- [x] **Inline autofill / password helper** — DONE. `onCreateInlineSuggestionsRequest` + `onInlineSuggestionsResponse` (API 30+) host the system autofill provider's chips (Google Password Manager / OTP) in a scrollable strip. Keyboard never stores/sees credentials. *Next: theme the chips to match the dark strip (currently default system style).*
- [x] **Floating bubble uses the VibeFlow waveform mark** (was a mic glyph) — `ic_vibeflow_mark`, white on the colored state circle.

## Keyboard look (familiarity = adoption)
- [~] **Gboard look-alike (Android)** — IN PROGRESS. Neutral grays, number-row hints, no suggestion dividers. 2026-06-27: fixed the flat/washed look vs Gboard — added a **baked key-shadow lip** (`ImePalette.keyShadow` + `rippleKey` LayerDrawable, ~2dp bottom edge, both themes) for depth + airier **6dp gutters** (`wkLp` 2→3dp). *Pending: verify the LIGHT keyboard on-device (user's OnePlus) + tune shadow strength; optional spacebar label "English", suggestion-strip toolbar.*
- [x] **Keyboard theme override (System / Light / Dark)** — DONE (Settings ▸ Keyboard ▸ Theme), like Gboard.
- [x] **Setup & permissions section + first-run recommendations** — DONE (Settings ▸ Setup & permissions; re-doable anytime).
- [x] **Key-press pop-up preview** (the enlarged key above your finger) — DONE. Magnified balloon on char/number/symbol keys (PopupWindow, `isClippingEnabled=false`), suppressed on password fields, dismissed on release / window-hide / detach.
- [ ] **Apple keyboard look-alike (iOS)** — for the iOS app (currently shelved); ships with iOS.
- [ ] Optional **theme picker** (Gboard-style / iOS-style / VibeFlow warm).

## Engine intelligence
- ~~Quiet-voice → Whisper fallback~~ · ~~Whisper vocab biasing~~ — **CUT (2026-06-27)** with the Whisper removal. Everyday = system on-device recognizer; fallback = downloadable Vosk. The `core/` engine-brain (`EngineRouter`/`TranscriptHealth`/`HypothesisFusion`) stays as unused pure-Kotlin (no native dep).

## Smart Formatting polish
- [x] **Polish FIDELITY — fixed via prompt (2026-06-27).** Same speech: Wispr stayed faithful, VibeFlow paraphrased/drifted ("Save the Tigers Season 3"→"save the season 3 of Tiger"; "cooked"→"enjoyed"; added "Additionally"). Root cause: the default `structured`/`auto`/`message` prompts told GPT-4.1-nano to "rewrite" / "turn rambling speech into paragraphs" → a tiny model paraphrases + drops proper nouns. **Kept nano** (user: mini is 5× pricier); rewrote those three prompts to **faithful cleanup** — fix only grammar/spelling/punctuation/casing/sentence-breaks, PRESERVE words/meaning/names/titles verbatim, never paraphrase/summarize/reword/invent. Applies to managed + BYOK (both build the prompt via `SmartFormatter.systemPrompt`; default style = `structured`). *Watch: nano may still slip occasionally; verify on-device. `email`/`notes` styles intentionally still reshape.*
- [ ] **Keyboard "✨ Enhance" button** — manual re-polish / "try harder" affordance (auto-polish covers the default case).
- [ ] **Progressive 1→2→3 indicator** — fuller visual than the current "✨ Polishing…" chip.

## Production hardening (do before release)
- [x] **Token-readout debug code** — DONE. The History capture footer "N in / N out" token readout is now gated behind `BuildConfig.DEBUG`, so it shows during debug testing and is automatically absent from release builds (keyboard was already clean). Backup posture verified hardened: `backup_rules.xml` + `data_extraction_rules.xml` both exclude all files from cloud backup, so the encrypted DataStore (auth tokens + API-key ciphertext) never leaves the device.
- [x] **API key → Android Keystore** — DONE. AES-256-GCM with a non-extractable Keystore key; only `enc:v1:` ciphertext on disk. Fail-safe: `encrypt`→null skips the write (never persists plaintext); `decrypt` separates transient hiccups (keep blob) from bad-tag (drop); save path refuses to wipe a key it couldn't read this session. Auto-migrates legacy plaintext on launch.
- [x] **Whisper removed + app size slashed** (2026-06-27) — Whisper lived only in the experimental Engine Lab (never wired live); deleted it + the native C++ build. Un-bundled the 68 MB Vosk model → now downloaded on demand (Settings ▸ Offline mode). **Per-device Play download ~70 MB → ~13 MB** (AAB 56→17 MB, universal APK 87→38 MB). Everyday dictation = OS on-device recognizer (System-first everywhere); Vosk = fallback only.
- [x] **Play listing copy + Data Safety + content-rating draft** — DONE → `docs/PLAY_LISTING.md` (app name / short / full description, Data Safety matrix, permissions justification, content rating, pre-launch checklist).
- [x] **Play graphics** — DONE → `dist/play-assets/`: 512² Play icon + 1024×500 feature graphic (rendered to match the in-app brand via `scripts/make_play_icons.py`), plus Home/History/Settings screenshots. *Optional polish: hide the floating-mic bubble for cleaner shots + add a keyboard-in-action shot.*
- [ ] **Release signing → Play App Signing** — enroll at first AAB upload (replaces the committed dev keystore).

## Shelved
- [ ] **iOS** — needs a Mac/Apple Developer Program + Codemagic CI → TestFlight. Native iOS has no true OTA. Parked per Vamsi.

## Notes / decisions captured
- Auto-vocab learns on the **1st correction** (guarded: ≤3 changed words, never >½ the line, original must be unknown).
- Smart Formatting tiers (final): **Private/offline** = on-device L1/L2, nothing leaves device · **Free** = BYOK (own key) · **Paid** = Managed (remote GPT-nano via Supabase). No local LLM.
- GPT-nano cost ≈ **~0.01–0.03 cent per message**; token usage shown only in History.
