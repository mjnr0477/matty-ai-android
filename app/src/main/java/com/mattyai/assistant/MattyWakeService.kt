package com.mattyai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import java.util.Locale

/**
 * Visible, user-started microphone foreground service. This is an experimental
 * wake phrase implementation, not an Android privileged hotword engine.
 * OEM background limits and on-device speech support can interrupt it.
 */
class MattyWakeService : Service() {
    private enum class ListenMode { WAKE_PHRASE, COMMAND }

    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var retryCount = 0
    private var mode = ListenMode.WAKE_PHRASE
    private var tts: TextToSpeech? = null
    private var ttsReady = false
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
            if (ttsReady) tts?.language = Locale.getDefault()
        }
        startListening()
    }

    private fun startListening() {
        if (listening) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            updateNotification("Offline speech unavailable. Install an on-device speech service and language pack.")
            stopSelf()
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true; retryCount = 0 }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    val delayMs = minOf(900L * (1 shl retryCount.coerceAtMost(4)).toLong(), 15_000L)
                    retryCount = (retryCount + 1).coerceAtMost(4)
                    handler.postDelayed(retryListening, delayMs)
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().trim()
                    if (mode == ListenMode.WAKE_PHRASE) {
                        if (Regex("(?i).*\\bmatty\\b.*").matches(heard)) {
                            mode = ListenMode.COMMAND
                            updateNotification("Matty heard you. Say a command now.")
                            speak("I'm listening. What should I do?")
                            handler.postDelayed(retryListening, 700L)
                        } else {
                            handler.postDelayed(retryListening, 350L)
                        }
                    } else {
                        mode = ListenMode.WAKE_PHRASE
                        if (heard.isBlank()) {
                            updateNotification("Didn't catch that. Listening for “Matty” again.")
                        } else {
                            executeSupportedBackgroundCommand(heard)
                        }
                        handler.postDelayed(retryListening, 900L)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            }
            recognizer?.startListening(intent)
        } catch (_: Exception) {
            listening = false
            updateNotification("Voice listening stopped by Android. Open Matty to restart it.")
            stopSelf()
        }
    }

    private fun executeSupportedBackgroundCommand(raw: String) {
        val command = CommandParser.parse(raw)
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        when (command.type) {
            MattyCommandType.VOLUME_UP -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, 0)
                confirm("Media volume increased.")
            }
            MattyCommandType.VOLUME_DOWN -> {
                audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, 0)
                confirm("Media volume decreased.")
            }
            MattyCommandType.START_PLAYBACK -> {
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                audio.dispatchMediaKeyEvent(android.view.KeyEvent(android.view.KeyEvent.ACTION_UP, android.view.KeyEvent.KEYCODE_MEDIA_PLAY))
                confirm("Sent play to the active media session.")
            }
            else -> {
                val message = "Heard: $raw. This action needs Matty open and may require unlocking your phone."
                updateNotification(message)
                speak("I heard you. Please open Matty and unlock your phone to run that action.")
            }
        }
    }

    private fun confirm(message: String) {
        updateNotification(message)
        speak(message)
    }

    private fun speak(message: String) {
        if (ttsReady) {
            if (Build.VERSION.SDK_INT >= 21) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "matty-result")
            } else {
                @Suppress("DEPRECATION")
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
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

    private fun buildNotification(message: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            else @Suppress("DEPRECATION") Notification.Builder(this)
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
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
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
