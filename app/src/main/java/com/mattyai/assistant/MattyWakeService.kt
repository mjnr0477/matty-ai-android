package com.mattyai.assistant

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Bundle
import java.util.Locale

/**
 * Offline-only wake phrase monitor. It deliberately refuses cloud recognition.
 * Android SpeechRecognizer is not a dedicated hotword engine, so reliability and
 * battery use must be verified on each target device before release.
 */
class MattyWakeService : Service() {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val retryListening = Runnable { startWakeListening() }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MattyAI:WakePhrase").apply {
            setReferenceCounted(false)
            acquire()
        }
        startWakeListening()
    }

    private fun startWakeListening() {
        if (listening) return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            updateNotification("Offline speech recognition unavailable. Install an offline speech pack.")
            stopSelf()
            return
        }
        try {
            recognizer?.destroy()
            recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            recognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { listening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { listening = false }
                override fun onError(error: Int) {
                    listening = false
                    if (!wakeTriggered) handler.postDelayed(retryListening, 900L)
                }
                override fun onResults(results: Bundle?) {
                    listening = false
                    val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull().orEmpty().lowercase(Locale.ROOT)
                    if (heard.matches(Regex(".*\\bmatty\\b.*"))) {
                        wakeTriggered = true
                        val intent = Intent(this@MattyWakeService, MainActivity::class.java).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                            putExtra(EXTRA_WAKE_WORD, true)
                        }
                        try { startActivity(intent) } catch (_: Exception) { }
                        stopSelf()
                    } else {
                        handler.postDelayed(retryListening, 350L)
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
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL_ID, "Matty voice wake", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows when Matty is listening for its wake phrase."
                setShowBadge(false)
            }
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        )
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Matty is ready")
                .setContentText("Listening for “Matty” using on-device speech recognition")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentTitle("Matty is ready")
                .setContentText("Listening for “Matty” using on-device speech recognition")
                .setContentIntent(open)
                .setOngoing(true)
                .build()
        }
    }

    private fun updateNotification(message: String) {
        val n = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Matty voice wake")
            .setContentText(message).build()
        else {
            @Suppress("DEPRECATION")
            Notification.Builder(this).setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Matty voice wake").setContentText(message).build()
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIFICATION_ID, n)
    }

    override fun onDestroy() {
        listening = false
        handler.removeCallbacksAndMessages(null)
        try { recognizer?.cancel(); recognizer?.destroy() } catch (_: Exception) {}
        recognizer = null
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        wakeLock = null
        wakeTriggered = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_WAKE_WORD = "com.mattyai.assistant.WAKE_WORD"
        private const val CHANNEL_ID = "matty_voice_wake"
        private const val NOTIFICATION_ID = 2407
        @Volatile private var wakeTriggered = false
    }
}
