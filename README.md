# Matty AI — offline-first Android phone assistant

Matty AI is an independent native Android project. It is separate from Dream Education and Dream Wi-Fi.

## Current implementation
- Refreshed native UI with a guided **Allow Matty to access my phone** setup action.
- Microphone permission flow and separate Android Accessibility Settings step. Android does not let an app silently grant itself all permissions.
- Push-to-talk speech recognition. It prefers Android's on-device recognizer when available and requests offline processing.
- Local allow-listed commands to open installed apps, Wi-Fi / mobile-data / Bluetooth / general settings, music apps, adjust media volume, and navigate Home / Back / notifications / Quick Settings.
- Accessibility actions are restricted to explicit global navigation actions. Matty does not collect or transmit screen contents.

## Important limitations — not production complete yet
- Always-on “Matty” wake-word listening is **not implemented yet**. The current UI requires tapping **Speak to Matty**. A proper always-on feature needs a dedicated on-device wake-word engine, a foreground service and notification, battery testing, and clear microphone privacy controls.
- Android usually does not allow third-party apps to toggle mobile data directly. Matty opens the relevant settings page instead.
- Specific-song playback depends on the installed music app, its APIs, and track availability. This prototype opens a compatible player; it does not guarantee starting an arbitrary song.
- Accessibility access is a separate, explicit system opt-in. One button cannot grant all Android permissions.
- Cloud AI is not wired in yet. Command matching is local; speech recognition works offline only when an on-device recognizer and language pack are available.
- This code has not yet been built with a complete Android SDK toolchain or tested end-to-end on a physical phone. Do not treat it as release-ready.

## Build
Open the project in Android Studio with Android SDK 35 installed and sync Gradle, then run assembleDebug. No Gradle wrapper is included yet.

## Manual device test checklist
1. Install the debug build and open Matty AI.
2. Tap **Allow Matty to access my phone** and grant microphone access.
3. Enable **Matty AI phone control** under Android Settings → Accessibility, then return to Matty.
4. Tap **Speak to Matty** and test: open WhatsApp, open TikTok, open Wi-Fi, open mobile data, turn volume down, open notifications, go home, go back, play music.
5. Repeat with Wi-Fi/mobile data disabled. The command parser and settings intents are local; speech recognition only works offline if an on-device recognizer and downloaded language pack are available.
6. Record actual outcomes and fix device-specific failures before release.
