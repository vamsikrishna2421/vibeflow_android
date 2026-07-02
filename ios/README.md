# 🍎 VibeFlow for iOS

A SwiftUI app + custom keyboard extension that brings VibeFlow's "talk → text,
offline" experience to iPhone. **Source-complete; build it on a Mac with Xcode.**

## The iOS design (and why it differs from Android)

iOS **does not allow keyboard extensions to use the microphone** (a hard Apple
sandbox rule). So VibeFlow on iOS splits the job:

```
   VibeFlow app                         VibeFlow keyboard (extension)
   ────────────                         ────────────────────────────
   record (on-device SFSpeechRecognizer)
   → TextPipeline (same rules as Android)
   → copy to clipboard
   → save to App Group  ───────────────►  "Paste latest" / pick a recent one
                                          → insertText() into ANY app's field
```

- **Fully offline:** `SFSpeechRecognizer` runs with
  `requiresOnDeviceRecognition = true`. No audio leaves the phone.
- **Type anywhere:** you dictate in the app, then in any other app switch to the
  VibeFlow keyboard and tap **Paste latest** (or pick a recent dictation). This is
  the standard, App-Store-compliant pattern for offline voice keyboards on iOS.
- **No "open access" needed:** the app↔keyboard hand-off uses an **App Group**,
  not the system pasteboard, so the keyboard requests *no* special access.

The text-formatting rules (`TextPipeline.swift`) are a faithful Swift port of the
Android `:core` module, so dictation is formatted identically on both platforms.

## Layout

```
ios/
├── project.yml                 # XcodeGen spec → generates VibeFlow.xcodeproj
├── VibeFlowCore/               # shared, compiled into BOTH targets
│   ├── TextPipeline.swift      #   curation/vocab/snippets (port of Android :core)
│   └── SharedStore.swift       #   App Group store (latest + history)
├── VibeFlow/                   # the app
│   ├── VibeFlowApp.swift
│   ├── ContentView.swift       #   Talk / History / About
│   └── SpeechManager.swift     #   on-device SFSpeechRecognizer
└── VibeFlowKeyboard/           # the keyboard extension
    └── KeyboardViewController.swift
```

## Build & run (on a Mac)

```bash
brew install xcodegen          # one-time
cd ios
xcodegen generate              # creates VibeFlow.xcodeproj
open VibeFlow.xcodeproj
```

In Xcode:
1. Select the **VibeFlow** target → Signing & Capabilities → pick your **Team**
   (free Apple ID works for on-device testing). Do the same for **VibeFlowKeyboard**.
2. Confirm both targets have the **App Group** `group.com.vibeflow.mobile`
   (already in the entitlements; just toggle it on under your account).
3. Run on your iPhone.
4. On the phone: **Settings → General → Keyboard → Keyboards → Add New Keyboard →
   VibeFlow**.
5. Open the VibeFlow app, dictate, then in any app switch to the VibeFlow
   keyboard and tap **Paste latest**.

> Without a paid Apple Developer account you can still run on your own device
> (7-day provisioning). App Groups work with a free account for personal use.

## Status

This is a clean, working MVP skeleton. The Android app is the further-along,
ship-today build; the iOS app mirrors its core and is ready to grow (settings
parity, richer keyboard, Shortcuts/Action-button trigger).
