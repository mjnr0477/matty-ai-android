package com.mattyai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.SpeechService
import org.vosk.android.RecognitionListener

/**
 * Visible, user-started microphone foreground service. This is an experimental
 * wake phrase implementation, not an Android privileged hotword engine.
 * OEM background limits and on-device speech support can interrupt it.
 */
class MattyWakeService : Service() {
    private enum class ListenMode { WAKE_PHRASE, COMMAND }

    private var speechService: SpeechService? = null
    private var speechRecognizer: Recognizer? = null
    private var model: Model? = null
    private var modelLoading = false
    private var listening = false
    private var handlingUtterance = false
    private var retryCount = 0
    private var mode = ListenMode.WAKE_PHRASE
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var resumeAfterSpeech = false
    private var speechGeneration = 0
    private var activeUtteranceId: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val retryListening = Runnable { startListening() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                buildNotification("Listening for “Matty” with on-device speech recognition"),
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Listening for “Matty” with on-device speech recognition"))
        }
        tts = TextToSpeech(applicationContext) { result ->
            ttsReady = result == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = selectedVoiceLocale()
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) = Unit
                    override fun onDone(utteranceId: String?) {
                        handler.post { if (utteranceId == activeUtteranceId) finishSpeechAndResume() }
                    }
                    @Deprecated("Deprecated by Android")
                    override fun onError(utteranceId: String?) {
                        handler.post { if (utteranceId == activeUtteranceId) finishSpeechAndResume() }
                    }
                    override fun onError(utteranceId: String?, errorCode: Int) {
                        handler.post { if (utteranceId == activeUtteranceId) finishSpeechAndResume() }
                    }
                })
            }
        }
        startListening()
    }

    private fun selectedVoiceLocale(): Locale {
        val tag = getSharedPreferences("matty_settings", MODE_PRIVATE)
            .getString("voice_language", Locale.getDefault().toLanguageTag())
            ?: Locale.getDefault().toLanguageTag()
        return Locale.forLanguageTag(tag)
    }

    private fun startListening() {
        if (listening || modelLoading) return
        handler.removeCallbacks(retryListening)
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            updateNotification("Microphone permission is required. Open Matty to grant it.")
            stopSelf()
            return
        }
        if (model == null) {
            modelLoading = true
            updateNotification("Preparing Matty's built-in offline speech model. First setup needs internet once.")
            MattyOfflineSpeech.prepare(this) { result ->
                modelLoading = false
                result.onSuccess { path ->
                    try {
                        model = Model(path)
                        updateNotification("Offline speech is ready. Listening for Matty.")
                        startListening()
                    } catch (e: Exception) {
                        updateNotification("Could not load offline speech model: ${e.message ?: "unknown error"}")
                        stopSelf()
                    }
                }.onFailure { error ->
                    updateNotification("Offline speech setup failed: ${error.message ?: "connect once and retry"}")
                    stopSelf()
                }
            }
            return
        }
        try {
            val recognizer = Recognizer(model, 16000.0f)
            speechRecognizer = recognizer
            speechService = SpeechService(recognizer, 16000.0f)
            handlingUtterance = false
            listening = speechService?.startListening(object : RecognitionListener {
                override fun onPartialResult(hypothesis: String?) = Unit
                override fun onResult(hypothesis: String?) { consumeRecognition(hypothesis) }
                override fun onFinalResult(hypothesis: String?) { consumeRecognition(hypothesis) }
                override fun onError(exception: Exception?) {
                    listening = false
                    stopSpeechEngine()
                    updateNotification("Offline speech error: ${exception?.message ?: "microphone unavailable"}. Retrying.")
                    handler.postDelayed(retryListening, 1500L)
                }
                override fun onTimeout() {
                    listening = false
                    stopSpeechEngine()
                    handler.postDelayed(retryListening, 250L)
                }
            }) == true
            if (!listening) {
                stopSpeechEngine()
                updateNotification("Could not start microphone capture. Check microphone access.")
                handler.postDelayed(retryListening, 1500L)
            } else {
                updateNotification("Matty is listening offline. Say “Matty” and your command.")
            }
        } catch (e: Exception) {
            listening = false
            stopSpeechEngine()
            updateNotification("Could not start offline speech: ${e.message ?: "microphone unavailable"}")
            handler.postDelayed(retryListening, 2500L)
        }
    }

    private fun consumeRecognition(hypothesis: String?) {
        if (handlingUtterance) return
        val heard = try {
            JSONObject(hypothesis.orEmpty()).optString("text").trim()
        } catch (_: Exception) { "" }
        if (heard.isBlank()) return
        handlingUtterance = true
        listening = false
        stopSpeechEngine()
        if (mode == ListenMode.WAKE_PHRASE) {
            val wake = CommandParser.extractWakeCommand(heard)
            if (!wake.detected) {
                handlingUtterance = false
                handler.postDelayed(retryListening, 250L)
                return
            }
            if (wake.command.isBlank()) {
                mode = ListenMode.COMMAND
                updateNotification("Matty heard you. Say a command now.")
                speakThenResume("I'm listening. What should I do?")
            } else {
                mode = ListenMode.WAKE_PHRASE
                val response = executeSupportedBackgroundCommand(wake.command)
                updateNotification(response)
                speakThenResume(response)
            }
        } else {
            mode = ListenMode.WAKE_PHRASE
            val response = executeSupportedBackgroundCommand(heard)
            updateNotification(response)
            speakThenResume(response)
        }
    }

    private fun stopSpeechEngine() {
        try { speechService?.stop() } catch (_: Exception) { }
        try { speechService?.shutdown() } catch (_: Exception) { }
        speechService = null
        try { speechRecognizer?.close() } catch (_: Exception) { }
        speechRecognizer = null
    }

    private fun executeSupportedBackgroundCommand(raw: String): String {
        val command = CommandParser.parse(raw)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        return when (command.type) {
            MattyCommandType.VOLUME_UP -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                "Media volume increased."
            }
            MattyCommandType.VOLUME_DOWN -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                "Media volume decreased."
            }
            MattyCommandType.START_PLAYBACK -> {
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                "Sent play to the active media session."
            }
            MattyCommandType.TAP_TEXT -> MattyAccessibilityService.tapVisibleLabel(command.argument)
            MattyCommandType.LONG_PRESS_TEXT -> MattyAccessibilityService.longPressVisibleLabel(command.argument)
            MattyCommandType.OPEN_NOTIFICATIONS -> MattyAccessibilityService.openNotifications()
            MattyCommandType.OPEN_QUICK_SETTINGS -> MattyAccessibilityService.openQuickSettings()
            MattyCommandType.TYPE_TEXT -> MattyAccessibilityService.typeIntoFocusedField(command.argument)
            MattyCommandType.SCROLL_UP -> MattyAccessibilityService.scroll(false)
            MattyCommandType.SCROLL_DOWN -> MattyAccessibilityService.scroll(true)
            MattyCommandType.SWIPE_LEFT -> MattyAccessibilityService.swipeLeft()
            MattyCommandType.SWIPE_RIGHT -> MattyAccessibilityService.swipeRight()
            MattyCommandType.GO_BACK -> MattyAccessibilityService.goBack()
            MattyCommandType.GO_HOME -> MattyAccessibilityService.goHome()
            MattyCommandType.OPEN_APP -> launchInstalledApp(command.argument)
            MattyCommandType.OPEN_APP_AND_CLICK_LINK -> {
                launchInstalledApp(command.argument)
                "Opened ${command.argument}. Say “Matty, tap” followed by the exact visible link label so I can target the right link."
            }
            MattyCommandType.OPEN_SYSTEM_SETTINGS -> launchIntent(Intent(Settings.ACTION_SETTINGS), "Opening Android Settings.")
            MattyCommandType.OPEN_WIFI_SETTINGS -> launchIntent(Intent(Settings.ACTION_WIFI_SETTINGS), "Opening Wi-Fi settings.")
            MattyCommandType.OPEN_MOBILE_DATA_SETTINGS -> launchIntent(Intent(Settings.ACTION_DATA_USAGE_SETTINGS), "Opening mobile data settings.")
            MattyCommandType.OPEN_BLUETOOTH_SETTINGS -> launchIntent(Intent(Settings.ACTION_BLUETOOTH_SETTINGS), "Opening Bluetooth settings.")
            MattyCommandType.OPEN_NETWORK_SETTINGS -> launchIntent(Intent(Settings.ACTION_WIRELESS_SETTINGS), "Opening network settings.")
            MattyCommandType.SEARCH_WEB -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(command.argument))), "Searching the web for ${command.argument}.")
            MattyCommandType.INSTALL_APP -> launchIntent(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(command.argument))), "Opening the app store search for ${command.argument}.")
            MattyCommandType.OPEN_MUSIC -> launchMusic()
            else -> "I heard you say $raw. This action is not supported in hands-free background mode yet."
        }
    }

    /**
     * Do not restart speech recognition while Matty is speaking: otherwise the
     * recognizer can hear its own reply and mistake it for the next command.
     * A timeout is retained because some Android TTS engines omit completion callbacks.
     */

    private fun launchIntent(intent: Intent, successMessage: String): String {
        return try {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            successMessage
        } catch (_: Exception) {
            "Android blocked opening that screen from background listening. Invoke Matty through the system assistant and try again."
        }
    }

    private fun launchInstalledApp(rawName: String): String {
        val name = rawName.removePrefix("the ").trim()
        if (name.isBlank()) return "Tell me the name of the app to open."
        val aliases = mapOf(
            "browser" to listOf("Chrome", "Browser"),
            "messages" to listOf("Messages", "Messaging"),
            "camera" to listOf("Camera"),
            "phone" to listOf("Phone", "Dialer"),
            "music" to listOf("Spotify", "YouTube Music", "Music", "VLC"),
            "settings" to listOf("Settings")
        )
        val names = aliases[name.lowercase()] ?: listOf(name)
        return try {
            val pm = packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            for (candidate in names) {
                val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(candidate, true) }
                    ?: apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(candidate, true) }
                val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
                if (launch != null) return launchIntent(launch, "Opening $candidate.")
            }
            "I couldn't find an installed app matching $name."
        } catch (_: Exception) {
            "Android blocked opening $name from background listening. Invoke Matty through the system assistant and try again."
        }
    }

    private fun launchMusic(): String {
        val musicIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC)
        if (musicIntent.resolveActivity(packageManager) != null) {
            return launchIntent(musicIntent, "Opening a music app.")
        }
        val audioIntent = Intent(Intent.ACTION_VIEW).setType("audio/*")
        return if (audioIntent.resolveActivity(packageManager) != null) {
            launchIntent(audioIntent, "Opening a compatible music player.")
        } else {
            "No compatible music app was found."
        }
    }

    private fun speakThenResume(message: String) {
        handler.removeCallbacks(retryListening)
        listening = false
        stopSpeechEngine()
        val generation = ++speechGeneration
        activeUtteranceId = "matty-result-$generation"
        resumeAfterSpeech = true
        val queued = try {
            if (ttsReady) {
                val result = if (Build.VERSION.SDK_INT >= 21) {
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, activeUtteranceId)
                } else {
                    @Suppress("DEPRECATION")
                    tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null)
                }
                result == TextToSpeech.SUCCESS
            } else false
        } catch (_: Exception) { false }

        handler.postDelayed({
            if (resumeAfterSpeech && generation == speechGeneration) finishSpeechAndResume()
        }, if (queued) 8_000L else 700L)
    }

    private fun finishSpeechAndResume() {
        if (!resumeAfterSpeech) return
        resumeAfterSpeech = false
        activeUtteranceId = null
        handlingUtterance = false
        handler.removeCallbacks(retryListening)
        handler.postDelayed(retryListening, 250L)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Matty voice wake", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Matty's user-enabled microphone service is active."
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    @Suppress("DEPRECATION")
    private fun buildNotification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else Notification.Builder(this)
        return builder.setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Matty voice assistant")
            .setContentText(message)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(message: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIFICATION_ID, buildNotification(message))
    }

    override fun onDestroy() {
        listening = false
        resumeAfterSpeech = false
        handler.removeCallbacksAndMessages(null)
        stopSpeechEngine()
        try { model?.close() } catch (_: Exception) {}
        model = null
        try { tts?.stop(); tts?.shutdown() } catch (_: Exception) {}
        tts = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_WAKE_WORD = "com.mattyai.assistant.WAKE_WORD"
        private const val CHANNEL_ID = "matty_voice_wake"
        private const val NOTIFICATION_ID = 2407
    }
}
