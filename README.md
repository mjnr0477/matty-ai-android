# Matty AI — offline-first Android phone assistant

Matty AI is an independent native Android project. It is separate from Dream Education and Dream Wi-Fi.

## Build outputs

The GitHub Actions workflow tests and builds the debug APK, then creates release APK and AAB outputs. If release signing secrets are configured, the workflow signs the release artifacts and verifies the APK signature. Download the `matty-ai-android-builds` artifact from the latest successful Actions run.

## Android system assistant integration

Matty now declares an Android `VoiceInteractionService` and provides a setup button to request the system's default-assistant role. The user must explicitly approve the role change. When Android invokes Matty through a supported system gesture, Matty opens its voice UI and listens for a command after microphone permission is granted.

This integration is a more appropriate system entry point than relying only on a continuously running app-owned microphone service. It does **not** create a custom always-on “Matty” hotword detector, guarantee screen-off activation on every device, or grant permission to control other apps' interfaces. Android and the device manufacturer determine available invocation gestures and background behavior.

## Cross-app command flow

After microphone permission is enabled, the supported flow is:

1. Say **“Matty, open WhatsApp”** to launch the installed WhatsApp app.
2. Say **“Matty, open Bluetooth”** to open Android Bluetooth settings, or tap **Find and pair Bluetooth devices** to scan and request pairing with nearby devices.
3. To operate another app, tap **Enable hands-free controls in other apps**, review the disclosure, accept it, and enable Matty in Android Accessibility settings. Then say direct commands such as **“tap Send”**, **“type Hello there”** (after focusing a text field), **“scroll down”**, **“go back”**, or **“go home.”** These controls are opt-in and some apps or secure screens may block them.

Bluetooth pairing and connections depend on the device, Android permissions, and supported Bluetooth profiles. Generic connection to every Bluetooth device is not possible because many devices require a specific profile or companion app.

## Current implementation

- Refreshed native UI with guided microphone setup, a default-assistant role setup button, and a dedicated nearby Bluetooth device screen.
- Bluetooth discovery, paired-device listing, user-approved pairing requests, and a shortcut to Android Bluetooth settings for profile-specific connections.
- Optional Accessibility Service for explicit, deterministic commands only: tap a specifically named visible label, enter user-dictated text into a focused field, scroll, Back, and Home. The app shows a prominent disclosure and requires affirmative consent before directing users to Android Accessibility settings. Matty does not autonomously plan actions or upload screen content. The service can still trigger Android security warnings; Play Console requires the applicable Accessibility API declaration and review.
- Offline-only wake-phrase service prototype using Android on-device speech recognition, a microphone foreground-service notification, and a short screen-wake attempt. It waits for Matty's spoken reply to finish before resuming recognition to reduce self-triggering; this behavior still needs physical-device testing. It refuses to use cloud recognition for speech.
- Primary controls now include **Tap to talk** (manual one-shot speech recognition) and **Type a command** (reliable fallback if wake-word detection or speech recognition is unavailable). The optional background “Matty” wake phrase remains experimental and depends on Android's on-device speech service.
- Local commands to open installed apps and Wi-Fi / mobile-data / Bluetooth / general settings, adjust media volume, open music apps, start web searches, and—when the user explicitly enables Accessibility—tap named visible controls, enter exact dictated text into a focused field, scroll, and use Back/Home.
- **“Install [app]”** opens an app-store search for the requested app. The user reviews the result and confirms installation in Google Play or another system store; Matty does not bypass Android installation confirmations or automatically grant a newly installed app's permissions.
- **“Search for [query]”** opens a browser web search. This specific action requires an internet connection; core command parsing remains local.
- Cross-app interaction is opt-in and limited to direct spoken commands. It cannot reliably control secure system screens, custom app controls, every web view, or apps that block accessibility actions; the user must review any action that sends or publishes information.
- Deterministic local command parser with unit tests for supported commands and unknown-command safety.

## Release signing secrets

In GitHub repository **Settings → Secrets and variables → Actions**, configure these repository secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded contents of the release `.jks` keystore.
- `ANDROID_KEYSTORE_PASSWORD`: keystore password.
- `ANDROID_KEY_ALIAS`: key alias in the keystore.
- `ANDROID_KEY_PASSWORD`: private-key password.

The workflow decodes the keystore into the runner's temporary directory and does not commit it to the repository. Keep a secure backup of the original keystore and passwords. Never paste them into issues, logs, or source files. If the Base64 secret is absent, CI can still create unsigned release artifacts; those are not ready to publish.

## Important limitations — not production complete yet

- The system assistant integration and wake phrase are **not production-verified**. The Android assistant role provides system invocation only where the OS/device supports it; the optional wake phrase remains a prototype. Android SpeechRecognizer is not a dedicated low-power hotword engine; it may stop, be killed by battery management, fail to bring Matty forward while locked, or behave differently across manufacturers. A production release needs a dedicated offline keyword-spotting engine and physical-device screen-off, false-trigger, and battery tests.
- Android usually does not allow third-party apps to toggle mobile data directly. Matty opens the relevant settings page instead.
- Specific-song playback depends on the installed music app, its APIs, and track availability. This prototype opens a compatible player; it does not guarantee starting an arbitrary song.
- Android does not permit a third-party app to make a universal connection to every Bluetooth device. Matty can discover nearby devices, request pairing, and open Android's Bluetooth settings; the actual connection depends on the device's Bluetooth profile.
- Cloud AI is intentionally not wired in. Command matching is local; speech recognition works offline only when Android provides an on-device recognizer with the required downloaded language pack. This build deliberately stops the wake service rather than silently falling back to online recognition. The current app targets Android 12+ because it relies on Android on-device SpeechRecognizer.
- A CI build passing does not prove the app works reliably on a physical phone. Device verification is required before calling it production-ready.

## Manual device test checklist

1. Install the debug build and open Matty AI.
2. Tap **Allow microphone access** and grant microphone and notification permissions. Optionally tap **Set Matty as default assistant** and approve the Android role prompt for system invocation.
3. Test **Type a command** with `open Wi-Fi settings`, then return to Matty and test **Tap to talk**. Only then test the optional background wake phrase.
4. To enable cross-app commands, tap **Enable hands-free controls in other apps**, read the disclosure, accept, then enable Matty in Android Accessibility settings. Test “tap [visible label]”, “type [exact text]” with a focused field, “scroll down”, “go back”, and “go home”.
5. Tap **Find and pair Bluetooth devices**, grant Nearby devices permission, scan for a powered-on discoverable accessory, and confirm Android's pairing prompt. Test Bluetooth connection from Android settings for the relevant device profile.
6. Repeat with Wi-Fi/mobile data disabled and verify speech remains on-device. Record actual outcomes and fix device-specific failures before release. Do not publish as production-ready until these tests pass on target devices.
