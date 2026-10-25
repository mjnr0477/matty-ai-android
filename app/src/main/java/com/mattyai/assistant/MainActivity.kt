package com.mattyai.assistant

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.view.KeyEvent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.app.KeyguardManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var status: TextView
    private lateinit var transcript: TextView
    private lateinit var accessButton: Button
    private var recognizer: SpeechRecognizer? = null
    private val requestAudio = 501
    private val ink = Color.rgb(20, 27, 45)
    private val muted = Color.rgb(103, 116, 140)
    private val purple = Color.rgb(103, 80, 232)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) == true) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        window.statusBarColor = Color.rgb(247, 248, 255)
        window.navigationBarColor = Color.rgb(247, 248, 255)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUi()
        refreshAccessState()
        stopWakeServiceForWakeIntent()
        startCommandAfterUnlockIfPossible()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false)) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        stopWakeServiceForWakeIntent()
        startCommandAfterUnlockIfPossible()
    }

    override fun onResume() {
        super.onResume()
        if (::accessButton.isInitialized) {
            refreshAccessState()
            if (intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) != true) ensureWakeService()
            startCommandAfterUnlockIfPossible()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) startCommandAfterUnlockIfPossible()
    }

    private fun startCommandAfterUnlockIfPossible() {
        if (intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) != true) return
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) {
            status.text = "Unlock your phone, then speak your command."
            return
        }
        intent?.removeExtra(MattyWakeService.EXTRA_WAKE_WORD)
        status.text = "Matty heard you. Tell me what to do."
        Handler(Looper.getMainLooper()).postDelayed({
            if (!isFinishing && !isDestroyed &&
                !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                startListening()
            }
        }, 350L)
    }

    private fun stopWakeServiceForWakeIntent() {
        if (intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) == true) {
            try { stopService(Intent(this, MattyWakeService::class.java)) } catch (_: Exception) { }
        }
    }

    private fun ensureWakeService() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            if (::status.isInitialized) status.text = "This phone has no available on-device speech recognizer. Install an offline speech service and language pack; Matty will not use online speech."
            return
        }
        try {
            val serviceIntent = Intent(this, MattyWakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (_: Exception) {
            if (::status.isInitialized) status.text = "Android stopped background listening. Open Matty and try enabling voice wake again."
        }
    }

    private fun buildUi() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(14), dp(22), dp(28))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(247, 248, 255), Color.WHITE))
        }
        val scroll = ScrollView(this).apply { isFillViewport = true; addView(page) }
        val brandRow = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; orientation = LinearLayout.HORIZONTAL }
        val logo = TextView(this).apply {
            text = "M"; textSize = 24f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = rounded(purple, 18); layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        }
        val brandText = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brandText.addView(text("MATTY AI", 17f, ink, true))
        brandText.addView(text("YOUR PHONE, YOUR VOICE", 10f, muted, true))
        brandRow.addView(logo); brandRow.addView(brandText)
        page.addView(brandRow, params(bottom = 24))

        page.addView(text("MEET YOUR PERSONAL ASSISTANT", 11f, purple, true), params(bottom = 8))
        val headline = text("Just say it.\nMatty gets it done.", 34f, ink, true).apply { setLineSpacing(dp(1).toFloat(), 1f) }
        page.addView(headline, params(bottom = 10))
        page.addView(text("Use voice or type a command to open apps, adjust media volume, search the web, and open phone settings.", 15f, muted, false), params(bottom = 20))

        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(18), dp(22), dp(18), dp(22))
            background = rounded(Color.WHITE, 26); elevation = dp(3).toFloat()
        }
        val orb = TextView(this).apply {
            text = "✦"; textSize = 34f; gravity = Gravity.CENTER; setTextColor(Color.WHITE)
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Color.rgb(126, 105, 255), Color.rgb(75, 62, 194))).apply { shape = GradientDrawable.OVAL }
            layoutParams = LinearLayout.LayoutParams(dp(82), dp(82)).apply { bottomMargin = dp(14) }
        }
        hero.addView(orb)
        hero.addView(text("Ready when you are", 20f, ink, true).apply { gravity = Gravity.CENTER }, params(bottom = 5))
        status = text("Set up access to start using Matty.", 13f, muted, false).apply { gravity = Gravity.CENTER }
        hero.addView(status, params(bottom = 8))
        transcript = text("Try: “Matty, open WhatsApp”", 13f, ink, false).apply { gravity = Gravity.CENTER }
        hero.addView(transcript, params(bottom = 14))
        page.addView(hero, params(bottom = 16))

        val accessCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(Color.rgb(241, 239, 255), 22)
        }
        accessCard.addView(text("One setup, then you're ready", 17f, ink, true), params(bottom = 6))
        accessCard.addView(text("Android will ask for microphone and nearby Bluetooth permissions when needed. Bluetooth scanning and pairing are optional.", 13f, muted, false), params(bottom = 14))
        accessButton = Button(this).apply {
            text = "Allow microphone access"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(ink, 16); setOnClickListener { beginAccessSetup() }
        }
        accessCard.addView(accessButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        page.addView(accessCard, params(bottom = 12))

        val bluetoothButton = Button(this).apply {
            text = "Find and pair Bluetooth devices"; isAllCaps = false; textSize = 14f
            setTextColor(Color.WHITE); background = rounded(purple, 16)
            setOnClickListener { startActivity(Intent(this@MainActivity, BluetoothActivity::class.java)) }
        }
        page.addView(bluetoothButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            bottomMargin = dp(10)
        })

        val talkButton = Button(this).apply {
            text = "Tap to talk"; isAllCaps = false; textSize = 15f
            setTextColor(Color.WHITE); background = rounded(ink, 16)
            setOnClickListener { startListening() }
        }
        page.addView(talkButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply {
            bottomMargin = dp(10)
        })

        val typeButton = Button(this).apply {
            text = "Type a command"; isAllCaps = false; textSize = 14f
            setTextColor(ink); background = rounded(Color.rgb(236, 237, 246), 16)
            setOnClickListener { showTextCommandDialog() }
        }
        page.addView(typeButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply {
            bottomMargin = dp(12)
        })

        page.addView(text("Try: open Bluetooth settings · open Wi-Fi settings · open WhatsApp · volume up · search for weather", 12f, muted, false), params(bottom = 6))
        page.addView(text("PRIVACY FIRST · Commands are handled on your phone where possible. Offline speech depends on your Android speech service and downloaded language packs.", 11f, muted, false), params(top = 14))
        setContentView(scroll)
    }

    private fun beginAccessSetup() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), requestAudio)
            return
        }
        continueAccessSetup()
    }

    private fun continueAccessSetup() {
        status.text = "Starting offline voice wake. Keep Matty's ongoing notification enabled."
        refreshAccessState()
        ensureWakeService()
    }

    private fun refreshAccessState() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        accessButton.text = if (!mic) "Allow microphone access" else "Voice access enabled ✓"
        status.text = if (!mic) "Microphone permission is needed for voice commands."
            else "Matty is ready for supported voice commands. Say “Matty” to begin."
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudio) {
            refreshAccessState()
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                status.text = "Microphone access is required for voice commands."
                return
            }
            continueAccessSetup()
        }
    }

    private fun showTextCommandDialog() {
        val commandInput = EditText(this).apply {
            hint = "e.g. open Bluetooth settings"
            setSingleLine(false)
            minLines = 1
            maxLines = 3
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        AlertDialog.Builder(this)
            .setTitle("Give Matty a command")
            .setMessage("Matty currently supports a limited set of phone commands. It is not yet a general AI assistant.")
            .setView(commandInput)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Run command") { _, _ ->
                val command = commandInput.text?.toString().orEmpty().trim()
                if (command.isBlank()) {
                    status.text = "Type a command first."
                } else {
                    handleCommand(command)
                }
            }
            .show()
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            beginAccessSetup(); return
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || !SpeechRecognizer.isOnDeviceRecognitionAvailable(this)) {
            status.text = "Offline speech recognition is unavailable. Install an on-device speech service and download your language pack; Matty will not use cloud speech."
            return
        }
        recognizer?.destroy()
        recognizer = try {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
        } catch (_: Exception) {
            status.text = "Couldn't start on-device speech recognition. Check the offline language pack and try again."
            return
        }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening… say a command." }
            override fun onBeginningOfSpeech() { status.text = "I'm listening." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Working on it…" }
            override fun onError(error: Int) { status.text = "Didn't catch that. Check speech service / offline language pack and try again." }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                // End this one-shot recognizer before a command starts the persistent wake service.
                try { recognizer?.destroy() } catch (_: Exception) { }
                recognizer = null
                handleCommand(phrase)
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        }
        try { recognizer?.startListening(intent) } catch (_: Exception) { status.text = "Couldn't start speech recognition on this phone." }
    }

    private fun handleCommand(raw: String) {
        transcript.text = "Heard: $raw"
        val parsed = CommandParser.parse(raw)
        when (parsed.type) {
            MattyCommandType.OPEN_SYSTEM_SETTINGS -> openSettings(Settings.ACTION_SETTINGS, "Opening Android Settings. You can use the screen normally; say Matty when you want another command.")
            MattyCommandType.OPEN_WIFI_SETTINGS -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings.")
            MattyCommandType.OPEN_MOBILE_DATA_SETTINGS -> openSettings(Settings.ACTION_DATA_USAGE_SETTINGS, "Opening mobile data settings. Android controls whether data can be switched directly.")
            MattyCommandType.OPEN_BLUETOOTH_SETTINGS -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings.")
            MattyCommandType.OPEN_NETWORK_SETTINGS -> openSettings(Settings.ACTION_WIRELESS_SETTINGS, "Opening network settings.")
            MattyCommandType.OPEN_NOTIFICATIONS -> openSettings(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, "Opening notification access settings.")
            MattyCommandType.OPEN_QUICK_SETTINGS -> openSettings(Settings.ACTION_SETTINGS, "Open Quick Settings using your phone's notification shade.")
            MattyCommandType.GO_BACK, MattyCommandType.GO_HOME -> status.text = "Use your phone's Back or Home controls; Matty does not control other apps in this build."
            MattyCommandType.VOLUME_DOWN -> adjustVolume(false)
            MattyCommandType.VOLUME_UP -> adjustVolume(true)
            MattyCommandType.START_PLAYBACK -> startPlayback()
            MattyCommandType.OPEN_MUSIC -> openMusic()
            MattyCommandType.OPEN_APP -> openApp(parsed.argument)
            MattyCommandType.INSTALL_APP -> openStoreSearch(parsed.argument)
            MattyCommandType.SEARCH_WEB -> searchWeb(parsed.argument)
            MattyCommandType.OPEN_APP_AND_CLICK_LINK -> openApp(parsed.argument)
            MattyCommandType.CLICK_FIRST_LINK, MattyCommandType.TAP_TEXT, MattyCommandType.TYPE_TEXT,
            MattyCommandType.SCROLL_UP, MattyCommandType.SCROLL_DOWN ->
                status.text = "Cross-app tapping, typing, and scrolling are not enabled in this privacy-focused build."
            MattyCommandType.UNKNOWN -> status.text = "I heard you, but that action is not supported yet."
        }
        ensureWakeService()
    }

    private fun openSettings(action: String, message: String) {
        try { startActivity(Intent(action)); status.text = message }
        catch (_: Exception) { status.text = "Android couldn't open that settings page on this device." }
    }

    private fun adjustVolume(up: Boolean) {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            status.text = if (up) "Music volume increased." else "Music volume decreased."
        } catch (_: Exception) { status.text = "Couldn't adjust media volume." }
    }

    private fun startPlayback() {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
            audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            status.text = "Sent Play to the active media session. If nothing is playing, open a music app and choose a track first."
        } catch (_: Exception) {
            status.text = "Could not control active media. Opening a compatible music app instead."
            openMusic()
        }
    }

    private fun openMusic() {
        val intents = listOf(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_APP_MUSIC),
            Intent(Intent.ACTION_VIEW).setType("audio/*")
        )
        for (intent in intents) try {
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent); status.text = "Opening a music app. Playback depends on the app and available tracks."; return
            }
        } catch (_: Exception) { }
        val likely = findLaunchIntent(listOf("Spotify", "YouTube Music", "Music", "YouTube", "VLC"))
        if (likely != null) try { startActivity(likely); status.text = "Opened a music app. Choose a track there."; return } catch (_: Exception) { }
        status.text = "No compatible music app was found. Install a music player and try again."
    }

    /**
     * Installation is intentionally handed to the official store/system UI. Matty must not
     * silently install packages or bypass Android's user consent and security checks.
     */
    private fun openStoreSearch(appName: String) {
        val cleaned = appName.trim()
        if (cleaned.isBlank()) {
            status.text = "Tell me the app name, for example: “Matty, install WhatsApp”."
            return
        }
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(cleaned)))
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/search?q=" + Uri.encode(cleaned) + "&c=apps")
        )
        try {
            if (marketIntent.resolveActivity(packageManager) != null) {
                startActivity(marketIntent)
                status.text = "Opening the app store to find $cleaned. Review the app and tap Install."
            } else {
                startActivity(webIntent)
                status.text = "Opening Google Play search for $cleaned. Review the app and tap Install."
            }
        } catch (_: Exception) {
            try {
                startActivity(Intent.createChooser(webIntent, "Find app to install"))
                status.text = "Choose a store or browser to find $cleaned."
            } catch (_: Exception) {
                status.text = "I couldn't open an app store. Check that a store or browser is installed."
            }
        }
    }

    private fun searchWeb(query: String) {
        val cleaned = query.trim()
        if (cleaned.isBlank()) {
            status.text = "Tell me what you want to search for."
            return
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(cleaned)))
        try {
            startActivity(intent)
            status.text = "Searching the web for: $cleaned"
        } catch (_: Exception) {
            status.text = "No browser or search app is available. Web search needs an internet connection."
        }
    }

    private fun openApp(name: String) {
        val cleaned = name.removePrefix("the ").replace(Regex("\\s+"), " ").trim()
        val aliases = mapOf(
            "browser" to listOf("Chrome", "Browser"),
            "messages" to listOf("Messages", "Messaging"),
            "camera" to listOf("Camera"),
            "phone" to listOf("Phone", "Dialer"),
            "music" to listOf("Spotify", "YouTube Music", "Music", "VLC"),
            "settings" to listOf("Settings")
        )
        if (cleaned == "settings" || cleaned == "phone settings") { openSettings(Settings.ACTION_SETTINGS, "Opening Settings."); return }
        val intent = findLaunchIntent(aliases[cleaned] ?: listOf(cleaned))
        if (intent == null) { status.text = "I couldn't find an installed app matching ‘$cleaned’."; return }
        try { startActivity(intent); status.text = "Opening $cleaned." }
        catch (_: Exception) { status.text = "Android couldn't launch $cleaned." }
    }

    private fun findLaunchIntent(names: List<String>): Intent? {
        val pm = packageManager
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        for (name in names) {
            val match = apps.firstOrNull { pm.getApplicationLabel(it).toString().equals(name, true) }
                ?: apps.firstOrNull { pm.getApplicationLabel(it).toString().contains(name, true) }
            val launch = match?.let { pm.getLaunchIntentForPackage(it.packageName) }
            if (launch != null) return launch
        }
        return null
    }

    private fun text(value: String, size: Float, color: Int, bold: Boolean) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun params(top: Int = 0, bottom: Int = 0) = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
        topMargin = dp(top); bottomMargin = dp(bottom)
    }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }

    override fun onDestroy() { recognizer?.destroy(); recognizer = null; super.onDestroy() }
}
