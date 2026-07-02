# 🎙️ VibeFlow Mobile — Talk. It types. Anywhere. Offline.

**VibeFlow Mobile is a 100%-offline voice keyboard for your phone.** Switch to it
in any app, tap the mic, and speak — your words are typed right where the cursor
is. Not in a text box? It copies to your clipboard and keeps a searchable
history so you can paste it later. **No internet, no account, no cloud** — the
speech model lives inside the app and your voice never leaves the device.

> The mobile sibling of [VibeFlow for Windows/Mac](../vibeflow). Same philosophy —
> private, offline, "talk anywhere" — rebuilt natively for phones.

---

## ✨ What it does

- ⌨️ **Type anywhere by voice** — a real Android keyboard (IME). Tap the mic in
  WhatsApp, Gmail, Chrome, anywhere, and dictated text lands at the cursor.
- 📋 **Clipboard fallback + Quick tile** — dictate from any screen via the Quick
  Settings tile; with no text field it copies to the clipboard and notifies you.
- 🕘 **Local searchable history** — every dictation is saved on-device. Search,
  copy back, pin favourites, clear the rest.
- 🗣️ **Spoken punctuation & layout** — say *"comma"*, *"period"*,
  *"question mark"*, *"new line"*, *"new paragraph"*.
- 🙌 **Hands-free mode** — tap once, speak, and VibeFlow auto-stops when you
  pause (on-device voice-activity detection). Or say *"scratch that"* / *"delete
  last word"* to edit by voice.
- 🧠 **Smart offline tidy-up** — auto-capitalisation, "i"→"I", optional
  filler-word removal — all deterministic, all on-device.
- 📚 **Vocabulary & snippets** — teach it your jargon ("GitHub", "Kubernetes") so
  it's spelled/cased right; say a trigger phrase to expand longer text.
- 🔒 **No internet permission at all.** Recognition runs entirely on your phone.

👉 **[Install it on your phone → docs/INSTALL_ANDROID.md](docs/INSTALL_ANDROID.md)**

---

## 🧭 How it works

```
            you tap the mic (in the keyboard, the tile, or the app)
                              │
                              ▼
                 ┌─────────────────────────┐
                 │  Vosk on-device ASR      │  streaming, offline
                 │  (bundled 40 MB model)   │
                 └────────────┬─────────────┘
                              │ raw text (live partials)
                              ▼
                 ┌─────────────────────────┐
                 │  Pipeline (pure Kotlin)  │  vocabulary → snippets →
                 │  vibeflow :core          │  spoken punctuation, casing…
                 └────────────┬─────────────┘
                              ▼
                   In a text field?
                   ├─ Yes → InputConnection types it at the cursor
                   └─ No  → copied to clipboard + saved to history
```

The recognition→formatting→delivery chain is shared by all three entry points
(keyboard, Quick Settings tile, in-app recorder), so behaviour is identical
everywhere.

---

## 🏗️ Project layout

```
vibeflow_mobile/
├── android/                     # Android Studio / Gradle project (ships now)
│   ├── core/                    # pure-Kotlin, unit-tested: curation, routing,
│   │                            #   vocabulary, snippets, history model
│   └── app/                     # Compose UI + IME keyboard + Vosk engine + tile
│       └── src/main/assets/model-en-us/   # bundled offline speech model
├── ios/                         # SwiftUI app + paste-keyboard extension (build on a Mac)
├── docs/
│   ├── INSTALL_ANDROID.md       # step-by-step install for your phone
│   └── COMPETITIVE_ANALYSIS.md  # VibeFlow Mobile vs the market
└── shared/design/               # design + architecture notes
```

**Android stack:** Kotlin · Jetpack Compose (Material 3) · `InputMethodService`
keyboard · [Vosk](https://alphacephei.com/vosk/) offline ASR · DataStore ·
kotlinx.serialization. Engine is behind a `SpeechEngine` interface so a
whisper.cpp backend can drop in later.

---

## 🔨 Build from source

Requires the Android SDK (Android Studio). The bundled model means the **app is
offline from first launch** — only *building* needs the internet (for Gradle and
libraries).

```bash
cd android

# Universal debug APK (installs on any phone, debug-signed):
./gradlew assembleDebug
#   → app/build/outputs/apk/debug/app-debug.apk

# Smaller, signed release APK for sharing (recommended deliverable):
./gradlew assembleRelease
#   → app/build/outputs/apk/release/app-release.apk

# Slim it to real-phone ABIs only:
./gradlew assembleRelease -Pabi=arm64-v8a,armeabi-v7a

# Run the core unit tests (no device needed):
./gradlew :core:test
```

Open `android/` in Android Studio and hit ▶ to run on a device/emulator.

---

## 🍎 iOS

A SwiftUI app + keyboard-extension project lives in `ios/`. iOS forbids
microphone access from keyboard extensions, so the design is: the **app**
records (on-device `SFSpeechRecognizer`) → copies to a shared App Group → the
**VibeFlow keyboard** pastes it into any field. It's source-complete and builds
in Xcode on a Mac. See [`ios/README.md`](ios/README.md).

---

## 🔒 Privacy

VibeFlow Mobile declares **no `INTERNET` permission**. The speech model is
on-device; transcription, history, vocabulary and snippets are all local and
stay on your phone. History is excluded from cloud backup by default.

## 📄 License

MIT. Speech recognition by [Vosk](https://github.com/alphacep/vosk-api)
(Apache-2.0); model weights from the Vosk project, bundled in the app.
