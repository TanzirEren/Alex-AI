# Alex AI v2.0.0 — Gemini voice assistant for Android (Java + XML)

Alex turns natural language into a **fixed set of 51 supported Android actions**. Gemini only *proposes* actions;
`ActionValidator` and `CommandRouter` check each one locally, ask you before sensitive ones (calls, sends, uninstalls,
risky taps), and report real failures instead of fake success.

## Build
**GitHub:** push this folder → Actions → *Android CI* → download `app-debug` (runs unit tests, then `assembleDebug`).
**Local:** Android Studio (JDK 17) → Run. One-time CLI wrapper: `gradle wrapper --gradle-version 8.7`
(the wrapper jar can't be generated offline, so it is not bundled; CI uses a pinned Gradle 8.7 instead).
Output: `app/build/outputs/apk/debug/app-debug.apk`

## First run
1. Settings → paste a Gemini key (https://aistudio.google.com/apikey). It is stored encrypted with Android Keystore and never in source.
2. Settings → Permissions: microphone, notifications, *Display over other apps* (orb + confirmation cards), Accessibility.
   Sideloaded apps on Android 13+: App info → ⋮ → **Allow restricted settings** first.
3. Home → **Start Alex**. Tap the orb or mic, or enable the "Hey Alex" phrase in Settings (off by default).

## PRD status (honest)
| Area | Status |
|---|---|
| F-001/002/003/004/005 Gemini, intents, context, allowlist router, errors | Done (retries, timeouts, rate-limit and offline handling) |
| F-006 History | Done: view, clear, disable retention. *Editable messages: planned* |
| F-007 Voice input | Done (device recognizer, English + Bengali if installed, typed fallback) |
| F-008 Voice output | Done: rate, pitch, mute, stop. *Separate confirmation-voice toggle: planned* |
| F-009 Wake phrase | **Partial.** Uses Android SpeechRecognizer (may use network), not a local detector. Off by default, notification + orb show mic state. Offline detector planned |
| F-010 Activation | Orb + notification. System-assistant role: planned |
| F-011–013 In-app photo viewer / video / music players | **Not built.** Opens system gallery/camera/music apps instead |
| F-014 Other apps' media | Media keys; works only if the app exposes a media session |
| F-015 YouTube | Search, YouTube Music, browser fallback. No in-player control |
| F-016–020 Apps, navigation, screen, install/uninstall | Done as scoped; closing apps opens Recents (Android blocks force-stop); install/uninstall use system screens |
| F-021–026 Messaging & calls | Open chats via search, type drafts, WhatsApp prefilled link, SMS draft. Send/call always ask first. Third-party UI changes can break it (best-effort) |
| F-027–032 Utilities | Done (brightness opens Display settings; flashlight, alarm, timer, panels, utilities) |
| F-033/034/036/037/038 Confirm, stop, permissions, privacy, capability list | Done |
| F-035 Shortcut manager | Planned |
| Package layout, Gradle wrapper, instrumented tests | Flat package; wrapper not bundled; only unit tests for the validator/registry |

Not verified: this project was written without an Android SDK, so it has **not been compiled or run**.
Please run the CI and send me any build error.

## Known limits
Accessibility actions depend on each app's screen labels. Voice recognition accuracy and the "young boy" TTS voice depend on the device.
`QUERY_ALL_PACKAGES` is used to find apps by name; that is fine for sideloading but restricted on Google Play.
