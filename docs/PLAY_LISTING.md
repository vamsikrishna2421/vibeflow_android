# VibeFlow — Play Store Listing & Data Safety (draft)

Ready-to-paste content for the Google Play Console. **Review before submitting** — the Data
Safety section is a legal declaration; have a quick professional look if you can. Anything in
_italics with a ⚠️_ is a decision/caveat for you.

---

## 1. Store listing

**App name** (≤30 chars)
> `VibeFlow: Voice Keyboard`

Alternatives: `VibeFlow — AI Voice Typing` · `VibeFlow Voice Keyboard`

**Short description** (≤80 chars)
> `Talk instead of type. On-device voice typing with optional AI cleanup.`

**Full description** (≤4000 chars)

```
Type with your voice — anywhere.

VibeFlow turns speech into clean, ready-to-send text, right from your keyboard. Open any
app, tap the mic, and talk. Your words land at the cursor — no more thumb-typing long
messages, emails, or notes.

PRIVATE BY DEFAULT
Your speech is transcribed on your device. Audio never leaves your phone. There's a true
on-device mode that uses no internet at all.

AI THAT POLISHES YOUR WORDS (OPTIONAL)
Rambling speech becomes structured, properly punctuated text — great for emails, messages,
and notes. Turn it on only if you want it. Choose how it runs:
• Private — on-device cleanup only; nothing leaves your phone.
• Your own key — bring your own AI key.
• VibeFlow — our managed AI, no setup, with free polishes every week.

WORKS EVERYWHERE
• A full keyboard you can use in any app.
• A floating mic bubble to dictate without switching keyboards.
• Spoken punctuation and commands ("comma", "new line", "scratch that").
• Learns the words and names you use most.

YOURS TO KEEP
• Dictation history stays on your device.
• Copy, re-format, or edit any capture.
• No ads. No selling your data.

Give your thumbs a break. Just talk — VibeFlow writes it down.

Questions or feedback: vamsy.24@gmail.com
```
_(~1,050 chars — room to expand with feature highlights or testimonials later.)_

**Category:** Productivity
**Tags:** keyboard, voice typing, dictation, speech to text, productivity
**Contact email:** `vamsy.24@gmail.com`
**Privacy Policy URL:** `https://vibeflow-dashboard-swart.vercel.app/legal/privacy`
**Website (optional):** `https://vibeflow-dashboard-swart.vercel.app`

**Graphics needed (you/I still to make):** app icon 512×512, feature graphic 1024×500,
phone screenshots (≥2, 16:9 or 9:16). _⚠️ I can help generate the icon + capture screenshots._

---

## 2. Data Safety form

Answer the Console's questions with the matrix below. Principles: **audio never collected**
(on-device recognition); **dictated text is sent off-device only in managed/own-key AI mode**,
and we don't store it (only counts/tokens).

**Does your app collect or share any of the required user data types?** → **Yes**

| Data type | Collected | Shared | Purpose | Linked to user | Optional? |
|---|---|---|---|---|---|
| **Email address** | Yes | No | Account management, App functionality | Yes | Yes — only if the user signs in for the managed AI tier |
| **User IDs** (account id) | Yes | No | App functionality | Yes | Yes — managed tier only |
| **Device or other IDs** (install id for seat/quota + anti-abuse) | Yes | No | App functionality, Fraud prevention | Yes | No |
| **App activity** (polish counts, token totals) | Yes | No | App functionality, Analytics | Yes | No |
| **User content — other user-generated content** (text you dictate, when you use AI polish) | Yes | **Yes → OpenAI** | App functionality | No | Yes — only when AI polish is used |
| **Audio / voice recordings** | **No** | No | — | — | — |

**Security practices to declare:**
- ✅ Data is **encrypted in transit** (HTTPS/TLS to Supabase + OpenAI).
- ✅ Users **can request deletion** (Google account sign-out + email us; profile row deletes on account deletion). Provide the contact email.
- For **User content (dictated text)**: mark **"Data is processed ephemerally"** is **not** fully accurate because it's sent to OpenAI which may retain it briefly for abuse monitoring — so declare it as **collected + shared (OpenAI), not stored by us**.

_⚠️ Decisions for you:_
1. **Audio = Not collected** is correct *because* transcription is on-device (system recognizer / downloadable Vosk). Keep it that way — don't add any server audio upload.
2. The **dictated-text** row only applies to the managed + own-key AI modes. In Private mode nothing leaves the device. The form can't express "only in one mode," so we disclose the broader (managed) behavior — which is the safe, honest choice.
3. OpenAI is the **third party** the text is shared with for processing. Supabase is our **processor/host** (not a separate "share" in Play's sense, but list it in the privacy policy — already done).

---

## 3. Content rating questionnaire

- App category: **Utility / Productivity / Communication**
- Violence, sexual content, profanity, drugs, gambling: **None**
- User-generated content shared publicly / social features: **No** (history is local; no public sharing)
- Shares user location: **No**
- Expected result: **Everyone / PEGI 3** (lowest rating).

---

## 4. App content / other Console sections

- **Ads:** No ads.
- **In-app purchases:** None yet. _(When Pro billing ships via Play Billing, update to "Yes — subscriptions".)_
- **Target audience & content:** Adults / 18+ default; **not** designed for children (avoids the Families policy + the audio/data scrutiny).
- **Government app:** No.
- **Permissions — justify in the Console / privacy policy:**
  - `RECORD_AUDIO` — core: voice dictation (on-device).
  - `INTERNET` — managed AI polish + sign-in + offline-model download.
  - `POST_NOTIFICATIONS` — recording status & quick actions.
  - `SYSTEM_ALERT_WINDOW` — the optional floating mic bubble (user-enabled).
  - `FOREGROUND_SERVICE` (+ mic type) — keeps dictation alive while recording.
- **IME disclosure:** Play scrutinizes keyboards/IMEs. Be ready to state plainly: *the keyboard
  never transmits keystrokes; it only sends dictated text to our AI proxy when the user
  explicitly uses AI polish, and never in Private mode.*

---

## 5. Pre-launch checklist (tie-in with BACKLOG)
- [ ] App icon 512×512 + feature graphic + screenshots
- [ ] Set the Privacy Policy URL above in the Console
- [ ] Fill Data Safety per §2, Content rating per §3
- [ ] Upload the AAB (`app/build/outputs/bundle/release/app-release.aab`) → enroll in **Play App Signing**
- [ ] Internal testing track → fix → Production
- [ ] (Later) Play Billing product → wire the paywall
