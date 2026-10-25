# Matty AI — offline-first Android phone assistant

Matty AI is an independent native Android project, separate from Dream Education and Dream Wi-Fi.

## Current implementation
- Guided setup screen for microphone permission and Android Accessibility Service.
- Push-to-talk voice commands, preferring on-device recognition when available.
- Local commands to open installed apps, Wi-Fi/mobile-data/Bluetooth settings, music apps, change media volume, and navigate Home/Back/notifications/Quick Settings with Accessibility enabled.

## Important limitations
- Always-on “Matty” wake-word listening is not implemented yet; currently the user taps Speak to Matty.
- Android does not allow an app to grant itself every permission. Accessibility must be enabled in Android Settings.
- Mobile data toggling is generally restricted; Matty opens the relevant settings page.
- Specific-song playback depends on the music app. This prototype opens a compatible player.
- Offline speech recognition depends on an installed on-device recognizer and downloaded language packs. Command parsing itself is local.
- This is not yet release-ready. Build and test on a real Android device before publishing.

## Build
Open in Android Studio with Android SDK 35 installed and sync Gradle, then run `assembleDebug`. No Gradle wrapper is included yet.

## Manual test checklist
1. Grant microphone permission and enable Matty AI phone control under Android Settings → Accessibility.
2. Tap Speak to Matty and test `open WhatsApp`, `open TikTok`, `open Wi-Fi`, `open mobile data`, `turn volume down`, `open notifications`, `go home`, `go back`, and `play music`.
3. Repeat with internet disabled to verify the device has offline speech recognition and a downloaded language pack.
