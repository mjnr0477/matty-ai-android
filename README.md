# Matty AI — offline-first Android phone assistant

Matty AI is an independent native Android project. It is separate from Dream Education and Dream Wi-Fi.


## Cross-app command flow

After microphone permission and Matty's Accessibility Service are enabled, the intended flow is:

1. Say **“Matty, open WhatsApp”** to launch the installed WhatsApp app.
2. Once WhatsApp is open, say **“Matty, click the link”** to ask Matty to activate the first accessible visible URL/link on the current screen.
3. For a simple compound request, say **“Open WhatsApp and click the link”**. Matty opens the app and then attempts to activate the first accessible link after a short wait.
4. You can also say **“Matty, click Send”**, **“Matty, type hello”** (after focusing a text field), or **“Matty, scroll down”**.

Link activation depends on the target app exposing the link through Android's accessibility tree. Some custom-rendered screens do not expose links or controls, and Matty must report failure rather than claim the tap succeeded. These cross-app flows still require real-device tests in WhatsApp and other target apps.

## Current implementation
- Refreshed native UI with a guided **Allow Matty to access my phone** setup action.
- Microphone permission flow and separate Android Accessibility Settings step. Android does not let an app silently grant itself all permissions.
- Offline-only wake-phrase service prototype using Android on-device speech recognition, a microphone foreground-service notification, and a short screen-wake attempt. It refuses to use cloud recognition for speech.
- One primary setup button; after Android permissions are approved, Matty attempts to listen for “Matty” and then accept a command after the user unlocks the phone.
- Local commands to open installed apps and Wi-Fi / mobile-data / Bluetooth / general settings, adjust media volume, open music apps, navigate Home / Back / notifications / Quick Settings, tap visible controls by label, enter text into an editable field, and scroll.
- **“Install [app]”** opens an app-store search for the requested app. The user reviews the result and confirms installation in Google Play or another system store; Matty does not bypass Android installation confirmations or automatically grant a newly installed app's permissions.
- **“Search for [query]”** opens a browser web search. This specific action requires an internet connection; core command parsing remains local.
- Accessibility is opt-in. For tap, text-entry, and scroll commands, Matty returns to the app underneath before performing the requested action so it does not accidentally target its own setup screen. It inspects the current accessibility node tree in memory only for that action; it does not save or transmit screen contents.
- Deterministic local command parser with unit tests for supported commands and unknown-command safety.
- GitHub Actions runs parser tests and Android lint, then builds a debug APK plus unsigned release APK/AAB artifacts. Unsigned release artifacts still need secure signing before distribution.

## Important limitations — not production complete yet
- The wake phrase is a **prototype, not production-verified**. Android SpeechRecognizer is not a dedicated low-power hotword engine; it may stop, be killed by battery management, fail to bring Matty forward while locked, or behave differently across manufacturers. A production release needs a dedicated offline keyword-spotting engine and physical-device screen-off, false-trigger, and battery tests.
- Android usually does not allow third-party apps to toggle mobile data directly. Matty opens the relevant settings page instead.
- Specific-song playback depends on the installed music app, its APIs, and track availability. This prototype opens a compatible player; it does not guarantee starting an arbitrary song.
- Accessibility access is a separate, explicit system opt-in. One button cannot grant all Android permissions.
- Cloud AI is intentionally not wired in. Command matching is local; speech recognition works offline only when Android provides an on-device recognizer with the required downloaded language pack. This build deliberately stops the wake service rather than silently falling back to online recognition. The current app targets Android 12+ because it relies on Android on-device SpeechRecognizer.
- This code has not yet been built with a complete Android SDK toolchain or tested end-to-end on a physical phone. Do not treat it as release-ready.

## Build
Open the project in Android Studio with Android SDK 35 installed and sync Gradle, then run `gradle testDebugUnitTest` and `gradle assembleDebug`. No Gradle wrapper is included yet. GitHub Actions is configured to run tests, lint, and debug/release builds and upload the artifacts. A successful run and physical-device verification have not yet been confirmed here; release artifacts from CI are unsigned and are not ready to publish.

## Manual device test checklist
1. Install the debug build and open Matty AI.
2. Tap **Allow Matty to access my phone** and grant microphone and notification permissions.
3. Enable **Matty AI phone control** under Android Settings → Accessibility, then return to Matty.
4. Confirm the ongoing “Matty is ready” notification appears. With the screen on and then screen off, say “Matty”; unlock the phone and test an offline command such as “open Wi-Fi settings”. Also test “tap Search”, “type Hello There!”, and “scroll down” in a safe test screen.
5. Repeat with Wi-Fi/mobile data disabled and verify that the speech service stays on-device. Check Android battery usage, screen-off reliability, and whether the phone manufacturer kills the service.
6. Record actual outcomes and fix device-specific failures before release. Do not publish as production-ready until these tests pass on target devices.
