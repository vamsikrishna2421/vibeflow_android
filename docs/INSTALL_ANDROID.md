# 📲 Install VibeFlow on your Android phone

VibeFlow is a **100% offline voice keyboard**: switch to it in any app, tap the
mic, speak, and your words are typed right where the cursor is. No text field
focused? It copies to your clipboard and saves it to a searchable history. **No
internet, ever** — the speech model is bundled inside the app.

You have two ways to install. **Method A (wireless, no cable)** is easiest.

---

## Method A — copy the APK to your phone (no cable)

1. **Get the APK onto your phone.** The ready-to-install file is:

   ```
   dist/VibeFlow-0.1.0.apk
   ```

   Send it to your phone any way you like — Google Drive, email to yourself,
   WhatsApp "to myself", or a USB cable copy to Downloads.

2. **Tap the APK** in your phone's Files/Downloads app.

3. Android will say *"For your security, your phone isn't allowed to install
   unknown apps from this source."* Tap **Settings → allow this source**, then
   back out and tap **Install**. (This is normal for any app installed outside
   the Play Store.)

4. Tap **Open**. You'll land on the VibeFlow setup screen.

---

## Method B — install over USB with adb (for developers)

> Needs the Android Platform Tools (`adb`) and **USB debugging** enabled on your
> phone (*Settings → About phone → tap "Build number" 7 times → Developer
> options → USB debugging*).

```bash
adb install -r dist/VibeFlow-0.1.0.apk
```

---

## First-run setup (60 seconds, one time)

When you open VibeFlow, the **Talk** screen shows a short checklist:

1. **Grant microphone** → tap **Grant** → **Allow**. *(Used only on-device; never uploaded.)*
2. **Enable VibeFlow keyboard** → tap **Enable** → flip **VibeFlow Voice
   Keyboard** on in the system list → tap **OK** on the warning (every custom
   keyboard shows it). Come back to VibeFlow.
3. **Pick VibeFlow when typing** → tap **Choose** → select **VibeFlow Voice
   Keyboard**.

That's it. The checklist disappears once you're set.

---

## Use it — type anywhere by voice

1. Open **any** app (WhatsApp, Gmail, Chrome, Notes…) and tap a text box.
2. If your normal keyboard appears, tap the **🌐 globe** key and choose
   **VibeFlow** (Android remembers it next time).
3. Tap the big **mic**, speak, then tap it again to stop. Your words are
   **typed right into the field**. 🎉 *(Or turn on **Hands-free** in Settings —
   then just tap once, speak, and it stops itself when you pause.)*

**Speak punctuation & layout** naturally: say *"comma"*, *"period"*,
*"question mark"*, *"new line"*, *"new paragraph"*.

**Edit by voice** — say a command on its own:
- *"scratch that"* (or *"delete that"*) → undoes your last dictation
- *"delete last word"* → removes the previous word
- *"new line"* / *"new paragraph"* → inserts a break

---

## Dictate to the clipboard from anywhere (no text field needed)

Add the **Quick Settings tile** so you can capture a thought from any screen:

1. Pull down the quick-settings shade → **edit (✏️/pencil)** → drag the
   **VibeFlow Dictate** tile up into your active tiles.
2. Tap the tile anytime → speak → it's **copied to your clipboard** and saved to
   **History**. Switch to any app and paste.

You can also open the VibeFlow app and use the big mic on the **Talk** screen the
same way.

---

## History — copy anything back later

Everything you dictate is saved (locally, on your phone) under the **History**
tab: search it, tap **copy** to reuse text, **pin** favourites, or clear the
rest. Turn history off anytime in **Settings → Keep history**.

---

## Tips & troubleshooting

| Situation | What to do |
|---|---|
| Keyboard doesn't show the mic | Make sure you picked **VibeFlow Voice Keyboard** (🌐 globe key → VibeFlow). |
| "Microphone permission needed" on the keyboard | Open the VibeFlow app → **Grant microphone**. |
| First dictation is a beat slow | The model loads once on first use, then it's instant. |
| Want true hands-free | **Settings → Hands-free (auto-stop)** — tap once, speak, and it stops itself when you pause. |
| Prefer hold-to-talk | **Settings → Hold-to-talk**. |
| Text went to clipboard instead of a field | You weren't in a text box (or output is set to Clipboard). Just paste. |

**Privacy:** VibeFlow has **no internet permission at all**. Your voice is
transcribed entirely on your phone and never leaves it.
