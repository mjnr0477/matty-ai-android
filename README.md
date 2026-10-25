# Matty AI — offline-first Android phone assistant

Matty AI is an independent native Android project. It is separate from Dream Education and Dream Wi-Fi.

## Current implementation
- Refreshed native UI with a guided **Allow Matty to access my phone** setup action.
- Microphone permission flow and separate Android Accessibility Settings step. Android does not let an app silently grant itself all permissions.
- Offline-only wake-phrase service prototype using Android on-device speech recognition, a microphone foreground-service notification, and a wake lock. It refuses to use cloud recognition for the wake phrase.
- One primary setup button; after Android permissions are approved, Matty attempts to listen for “Matty” and then accept a command after the user unlocks the phone.
- Local allow-listed commands to open installed apps, Wi-Fi / mobile-data / Bluetooth / general settings, music apps, adjust media volume, and navigate Home / Back / notifications / Quick Settings.
- Accessibility actions are restricted to explicit global navigation actions. Matty does not collect or transmit screen contents.
- Deterministic local command parser with unit tests for supported commands and unknown-command safety.
- GitHub Actions workflow runs parser tests, builds a debug APK, and uploads it as a workflow artifact when successful.

## Important limitations — not production complete yet
- The wake phrase is now a **prototype, not production-verified**. Android SpeechRecognizer is not a dedicated hotword engine; it may stop, be killed by battery management, consume significant power, fail to bring the activity forward while locked, or require device-specific offline speech support. A production release needs a dedicated offline keyword-spotting engine and physical-device screen-off/battery tests.
- Android usually does not allow third-party apps to toggle mobile data directly. Matty opens the relevant settings page instead.
- Specific-song playback depends on the installed music app, its APIs, and track availability. This prototype opens a compatible player; it does not guarantee starting an arbitrary song.
- Accessibility access is a separate, explicit system opt-in. One button cannot grant all Android permissions.
- Cloud AI is intentionally not wired in. Command matching is local; speech recognition works offline only when Android provides an on-device recognizer with the required downloaded language pack. This build deliberately stops the wake service rather than silently falling back to online recognition.
- This code has not yet been built with a complete Android SDK toolchain or tested end-to-end on a physical phone. Do not treat it as release-ready.

## Build
Open the project in Android Studio with Android SDK 35 installed and sync Gradle, then run `gradle testDebugUnitTest` and `gradle assembleDebug`. No Gradle wrapper is included yet. GitHub Actions is configured to run these checks and publish a debug APK artifact, but a successful build has not yet been verified here.

## Manual device test checklist
1. Install the debug build and open Matty AI.
2. Tap **Allow Matty to access my phone** and grant microphone access.
3. Enable **Matty AI phone control** under Android Settings → Accessibility, then return to Matty.
4. Confirm the ongoing “Matty is ready” notification appears. With the screen on and then screen off, say “Matty”; unlock the phone and test an offline command such as “open Wi-Fi settings”.
5. Repeat with Wi-Fi/mobile data disabled and verify that the speech service stays on-device. Check Android battery usage, screen-off reliability, and whether the phone manufacturer kills the service.
6. Record actual outcomes and fix device-specific failures before release. Do not publish as production-ready until these tests pass on target devices.
