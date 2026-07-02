# VibeFlow — Test Checklist (blind-testable)

Each test has exact steps, a sentence to use, and what to expect. Tick as you go.
Prereqs for AI tests: **Settings → Smart Formatting → API key set**, **Auto-polish ON**.

---

## A. Live keyboard — accuracy, stitching, periods
**Do:** Open Messages, tap VibeFlow mic, speak this with a clear pause between sentences:
> "The quarterly revenue grew by eighteen percent this March. We onboarded forty-two new enterprise clients across three regions. Please schedule a follow-up with the finance team next Tuesday at four thirty."

- [x] All three sentences appear (nothing dropped, nothing duplicated)
- [x] Periods separate the sentences (not one run-on)
- [x] Words are mostly accurate

---

## B. Auto-vocabulary learning (learns on the 1st correction)
**Do:** In any text field, dictate:
> "Let's review the Grafana dashboard with Anirudh before the Kubernetes migration."

1. Note which word(s) came out wrong (likely *Grafana*, *Anirudh*, or *Kubernetes*).
2. Fix **one** wrong word by hand to the correct spelling, then **send** (or close the keyboard).
3. Dictate the **same sentence again**.

- [x] On the 2nd dictation, the previously-wrong word now comes out **correct automatically**
- [x] **Settings → Personalize → Learned corrections** shows `wrongword → yourfix · active`
- [ ] Deleting it there stops it applying
- [ ] (Sanity) If you instead rewrite the *whole* sentence before sending, it should NOT learn anything

---

## C. Context-aware Smart Formatting (style follows the app)
**Do:** With auto-polish ON + "Match style to app" ON, dictate the **same** text in two apps:
> "hey wanted to flag that the launch slipped to next friday and we still need final sign off from legal and design so can you nudge them today and also block thirty minutes tomorrow for a quick review"

- [x] In **Gmail/Outlook** → comes out as a structured **email** (greeting, body, sign-off)
- [x] In **WhatsApp/Messages** → comes out as a **casual, concise message**
- [x] In a **notes app** (Samsung Notes) → comes out as **notes/bullets** (heading + nested bullets)

---

## D. Private Mode (on-device only)
**Do:** Settings → Smart Formatting → **Private Mode ON**. Then dictate:
> "summarize the three blockers we discussed today and assign one owner for each of them"

- [x] Text stays as plain dictation — **no AI restructure, no swap** (nothing sent to cloud)
- [x] Turn Private Mode **OFF**, dictate again → it polishes normally

---

## E. Show-original (undo an auto-polish swap)
**Do:** Auto-polish ON. Dictate:
> "can you put together a quick note for the team about the new deployment process and mention the rollback steps in case something breaks"

- [x] Text first appears, then **swaps to a polished version** a couple seconds later
- [x] The suggestion strip shows **"↩ Original"**
- [x] Tapping **"↩ Original"** restores the pre-polish text
- [ ] If you start editing/sending **before** it swaps, your text is NOT clobbered (polished goes to clipboard instead, with a toast)

---

## F. Token usage shows ONLY in History
**Do:** After any polish, go **History → tap the capture → "Polished" tab**.

- [x] A line shows **"AI usage: X in · Y out tokens"**
- [x] **No** token text appears anywhere on the keyboard

---

## G. Capture history stages
**Do:** Dictate anything, then **History → tap it**.

- [x] **Raw** = the recognizer's literal run-on text; **Clean** = punctuated/curated; they now **differ**
- [x] **Polished** shows the AI version (or "Polish with AI" button if not auto-polished)
- [x] Edit a stage, tap Copy, leave and return → **your edit persisted** (no revert)

---

## H. Math suggestions
**Do:** In a text field, type each:

- [x] `1+2=` → offers **3**
- [x] `1+2=3` then a few spaces then `6+7=` → offers **13**
- [x] `1+2` alone (no `=`) → offers **nothing**

---

## I. Keyboard hardening
- [x] **"="**: tap `?123` → `=\<` → press `=`, `/`, `[`, `]`, `{`, `}` — they type
- [x] **Lag**: type a fast burst of letters — instant, no backlog
- [x] **Backspace on selection**: select some text, press backspace → deletes the whole selection
- [x] **Theme**: switch phone to dark (or light) mode → keyboard follows

---

## K. Clipboard paste chip
**Do:** Copy some text in any app (or get an OTP SMS and copy it). Open a text field with the VibeFlow keyboard.
- [ ] A **"📋 <preview>"** chip appears in the suggestion strip
- [ ] Tapping it pastes the full copied text at the cursor
- [ ] It disappears once you start typing a word, and after ~6 minutes
- [ ] In a **password** field, no clipboard chip appears

## L. Floating mic button
**Do:** Settings → Floating mic → **Floating mic button ON** → grant "Display over other apps" → return to the app.
- [ ] A draggable **teal bubble showing the VibeFlow waveform mark** (not a mic glyph) appears; you can drag it anywhere
- [ ] Open any app (e.g. Notes with **Gboard**, not VibeFlow keyboard). Tap the bubble, speak:
  > "remind me to send the updated invoice to the vendor before five today"
- [ ] Bubble turns coral (listening) → lavender (processing) → toast "Copied…"
- [ ] Your keyboard's **paste chip** now shows the result — tap to paste
- [ ] **Long-press** the bubble stops it; the toggle in Settings also stops it

## M. Password / autofill helper (like Gboard)
**Pre:** Have at least one saved login in **Google Password Manager** for some app/site, and Google set as your autofill service (Settings → General management → Passwords/Autofill).
**Do:** Open that app/site's **login screen** and tap the **username or password** field with the VibeFlow keyboard up.
- [ ] An autofill **chip** (account name / "🔑 Passwords") appears in the suggestion strip
- [ ] Tapping it fills the credentials (handled by the system — VibeFlow never sees the password)
- [ ] Multiple saved logins → the strip **scrolls** horizontally through them
- [ ] Leaving the field / typing clears the chips; a normal field shows word suggestions again

## N. Key-press pop-up preview
**Do:** In any normal text field, tap letters/numbers/symbols.
- [ ] A magnified balloon of the key pops up **above your finger**, gone on release
- [ ] It shows the **shifted** glyph when Shift/Caps is on
- [ ] In a **password** field, **no** preview balloon appears
- [ ] Swipe down to hide the keyboard mid-press → no balloon is left floating on screen

## O. API key encryption (Keystore) — should be invisible
**Do:** This update migrates your stored key to encrypted-at-rest; verify nothing broke.
- [ ] AI polish **still works** right after updating (your existing key survived the migration)
- [ ] Force-close the app and reopen → **Settings → Smart Formatting** still shows the key as set; polish still works
- [ ] (Optional) Settings → clear the key field and Save when it shows your key → it clears as expected

## J. Regression (core must still work after the big changes)
- [ ] Plain typing is instant; autocorrect + word suggestions still appear
- [ ] Voice dictation still types correctly with sentence periods
- [ ] App opens, History/Settings tabs work, no crashes

---

_See `BACKLOG.md` for everything not yet built._
