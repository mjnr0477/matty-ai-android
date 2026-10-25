# Matty AI — native Android hands-free assistant

Matty AI is an independent Android project, separate from Dream Education and Dream Wi-Fi. The main screen is designed around one **Enable hands-free / Disable hands-free** control and an in-app voice-language selector.

## Build and download

GitHub Actions runs command-parser unit tests, Android lint, debug APK build, release APK/AAB build, and release signature verification. Download the `matty-ai-android-builds` artifact from the latest successful run:
https://github.com/mjnr0477/matty-ai-android/actions

The artifact is a test build until the device checklist below passes. A successful CI run does not prove reliable background listening or app control on every Android device.

## First-time setup

1. Install the APK and open Matty AI.
2. Tap **Enable hands-free** and grant microphone permission. Matty explains why cross-app access is needed and asks you to review the disclosure.
3. In Android Accessibility settings, explicitly enable Matty's **cross-app voice control** service. This permission is necessary for tapping, typing, scrolling, swiping, and long-pressing controls in other apps.
4. Return to Matty. The ongoing notification indicates that the background microphone service is running. Keep it enabled while using hands-free.
5. Select a voice language in Matty. Recognition and spoken replies use the selected locale when supported. If offline recognition is unavailable, Matty asks before enabling Android's standard recognizer; depending on the provider, speech audio may be processed online. You can decline and install an offline speech service and language pack instead.

You can turn hands-free off from the same main button. Android may still stop background recognition because of battery management or manufacturer restrictions.

## Supported voice actions

Say **“Matty”** followed by a command while the background listener is active.

- **Launch apps:** “open WhatsApp”, “open Camera”, “open Settings”. The app must be installed and visible to Android's package manager.
- **Navigate:** “go back”, “go home”, “open notifications”, “open quick settings”.
- **Control visible app UI** (after enabling Accessibility): “tap Search”, “tap Send”, “long press Profile”, “type Hello there” after focusing a text field, “scroll down”, “scroll up”, “swipe left”, “swipe right”.
- **Phone/media actions:** “volume up”, “volume down”, “play music”, “open Wi-Fi settings”, “open Bluetooth settings”, “open mobile data settings”.
- **Find information:** “search for weather in Bungoma” opens a browser search. This needs internet access in the browser.
- **Install an app:** “install WhatsApp” opens an app-store search. The user reviews the app and confirms installation in the store; Matty does not silently install apps or grant permissions.
- **Select a link carefully:** if you ask Matty to open an app and click a link, it opens the app and asks you to name the exact visible link label. It will not guess which link to click.

The app performs deterministic, explicit actions. It does not yet have a general-purpose AI planner that can understand arbitrary multi-step requests and independently operate every app. Secure screens, custom controls, WebViews, banking/payment flows, and apps that restrict accessibility may block actions. Review actions that send, post, share, delete, or otherwise affect other people or accounts.

## Android platform constraints

- Matty's current wake-word listener uses Android's on-device `SpeechRecognizer`; it is **not** a dedicated low-power hotword engine. Screen-off listening, false-trigger rate, battery use, and restart behavior vary by device. A default-assistant role can provide system invocation where Android supports it, but this app's simplified screen does not expose that role setup yet.
- The selected language only works if Android's installed speech recognizer supports that locale. Matty prefers on-device recognition. If it is unavailable, Matty asks permission before using Android's standard recognizer, which may process audio online; it does not silently switch.
- Android normally prevents third-party apps from directly toggling mobile data; Matty opens the relevant system settings.
- Music playback depends on the installed music app and its active media session. Matty cannot guarantee that an arbitrary song starts.
- Accessibility is powerful and sensitive. The user must explicitly enable it in Android settings; Play Console distribution requires the applicable Accessibility API declaration and policy review.
- No app can guarantee control of literally every application or secure screen through Accessibility. A CI build cannot substitute for real-device testing.

## Manual device acceptance checklist

1. Install the latest successful CI artifact on a target Android phone.
2. Enable hands-free and grant microphone permission.
3. Enable Matty in Accessibility settings after reviewing the in-app disclosure.
4. Test app launch, visible-label tap, long press, text entry, scroll, swipe, Back, Home, notifications, Quick Settings, volume, and media commands in at least three unrelated apps.
5. Test screen on/off, lock/unlock, background restrictions, reboot, speech language availability, and battery use. Confirm the ongoing notification matches the real service state.
6. Verify that unsupported actions are reported honestly and do not claim success.
7. Only label the app production-ready after real-device tests pass and Accessibility policy requirements for the intended distribution channel are met.

## Release signing

GitHub Actions reads these repository secrets without exposing their values in source:
- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

Keep a secure backup of the release keystore and passwords. Never commit or share them.