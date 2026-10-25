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
import android.app.role.RoleManager
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
    private var assistantInvocationActive = false
    private var commandStartScheduled = false
    private val requestAudio = 501
    private val requestAssistantRole = 502
    private lateinit var assistantButton: Button

    companion object {
        const val EXTRA_ASSISTANT_INVOKED = "com.mattyai.assistant.ASSISTANT_INVOKED"
    }
    private val ink = Color.rgb(20, 27, 45)
    private val muted = Color.rgb(103, 116, 140)
    private val purple = Color.rgb(103, 80, 232)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        assistantInvocationActive = intent?.getBooleanExtra(EXTRA_ASSISTANT_INVOKED, false) == true ||
            intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) == true
        if (isVoiceInvocation()) {
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
        assistantInvocationActive = intent.getBooleanExtra(EXTRA_ASSISTANT_INVOKED, false) ||
            intent.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false)
        if (isVoiceInvocation()) {
            window.addFlags(WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        }
        stopWakeServiceForWakeIntent()
        startCommandAfterUnlockIfPossible()
    }

    override fun onResume() {
        super.onResume()
        if (::accessButton.isInitialized) {
            refreshAccessState()
            if (!isVoiceInvocation()) ensureWakeService()
            startCommandAfterUnlockIfPossible()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) startCommandAfterUnlockIfPossible()
    }

    private fun startCommandAfterUnlockIfPossible() {
        if (!isVoiceInvocation() || commandStartScheduled) return
        val keyguard = getSystemService(KEYGUARD_SERVICE) as KeyguardManager
        if (keyguard.isKeyguardLocked) {
            status.text = "Unlock your phone, then speak your command."
            return
        }
        intent?.removeExtra(MattyWakeService.EXTRA_WAKE_WORD)
        intent?.removeExtra(EXTRA_ASSISTANT_INVOKED)
        status.text = "Matty heard you. Tell me what to do."
        commandStartScheduled = true
        Handler(Looper.getMainLooper()).postDelayed({
            commandStartScheduled = false
            assistantInvocationActive = false
            if (!isFinishing && !isDestroyed &&
                !(getSystemService(KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked) {
                startListening()
            }
        }, 350L)
    }

    private fun stopWakeServiceForWakeIntent() {
        if (isVoiceInvocation()) {
            try { stopService(Intent(this, MattyWakeService::class.java)) } catch (_: Exception) { }
        }
    }

    private fun ensureWakeService() {
        if (!getSharedPreferences("matty_settings", MODE_PRIVATE).getBoolean("hands_free_enabled", false)) return
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        val offlineAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val fallbackAccepted = getSharedPreferences("matty_settings", MODE_PRIVATE)
            .getBoolean("online_speech_fallback_accepted", false)
        if (!offlineAvailable && !fallbackAccepted) {
            requestOnlineSpeechFallbackConsent { startWakeService() }
            return
        }
        startWakeService()
    }

    private fun startWakeService() {
        try {
            val serviceIntent = Intent(this, MattyWakeService::class.java)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
        } catch (_: Exception) {
            if (::status.isInitialized) status.text = "Android stopped background listening. Open Matty and try enabling voice wake again."
        }
    }

    private fun requestOnlineSpeechFallbackConsent(onAccepted: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Allow online speech recognition?")
            .setMessage("This phone does not provide an available on-device speech recognizer. You can install an offline speech service and language pack, or let Matty use Android's standard speech recognizer. Depending on your device and provider, audio may be sent to a speech service for processing. Continue only if you are comfortable with that.")
            .setNegativeButton("Not now") { _, _ ->
                if (::status.isInitialized) status.text = "Speech is unavailable until you install an offline speech service or allow standard speech recognition."
            }
            .setPositiveButton("Allow standard speech") { _, _ ->
                getSharedPreferences("matty_settings", MODE_PRIVATE).edit()
                    .putBoolean("online_speech_fallback_accepted", true).apply()
                onAccepted()
            }
            .show()
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
        page.addView(text("Enable hands-free once, then speak supported commands to launch apps, control visible buttons, enter text, navigate, and manage media.", 15f, muted, false), params(bottom = 20))

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
        accessCard.addView(text("Enable microphone access and Matty in Accessibility settings to let it operate visible controls in other apps. You stay in control of every command.", 13f, muted, false), params(bottom = 14))
        accessButton = Button(this).apply {
            text = "Allow microphone access"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(ink, 16); setOnClickListener { toggleHandsFree() }
        }
        accessCard.addView(accessButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))

        accessCard.addView(text("Voice language", 13f, ink, true), params(top = 14, bottom = 5))
        val voiceLanguages = listOf(
            "English (US)" to "en-US",
            "English (UK)" to "en-GB",
            "English (Kenya)" to "en-KE",
            "Kiswahili (Kenya)" to "sw-KE",
            "Kiswahili" to "sw"
        )
        val voiceLanguage = android.widget.Spinner(this).apply {
            adapter = android.widget.ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                voiceLanguages.map { it.first })
            val savedTag = getSharedPreferences("matty_settings", MODE_PRIVATE)
                .getString("voice_language", Locale.getDefault().toLanguageTag())
            setSelection(voiceLanguages.indexOfFirst { it.second.equals(savedTag, true) }.let { if (it < 0) 0 else it })
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                    getSharedPreferences("matty_settings", MODE_PRIVATE).edit()
                        .putString("voice_language", voiceLanguages[position].second).apply()
                    if (::status.isInitialized) status.text = "Voice language: ${voiceLanguages[position].first}. The matching offline speech pack must be installed."
                }
            }
        }
        accessCard.addView(voiceLanguage, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))

        page.addView(accessCard, params(bottom = 12))

        assistantButton = Button(this).apply {
            text = "Set Matty as default assistant"
            isAllCaps = false
            textSize = 14f
            setTextColor(Color.WHITE)
            background = rounded(purple, 16)
            setOnClickListener { requestDefaultAssistant() }
        }
        assistantButton.visibility = View.VISIBLE
        page.addView(assistantButton, params(bottom = 10))

        val crossAppButton = Button(this).apply {
            text = "Enable hands-free controls in other apps"
            isAllCaps = false
            textSize = 14f
            setTextColor(ink)
            background = rounded(Color.rgb(236, 237, 246), 16)
            setOnClickListener { requestCrossAppVoiceControl() }
        }
        crossAppButton.visibility = View.GONE

        val bluetoothButton = Button(this).apply {
            text = "Find and pair Bluetooth devices"; isAllCaps = false; textSize = 14f
            setTextColor(Color.WHITE); background = rounded(purple, 16)
            setOnClickListener { startActivity(Intent(this@MainActivity, BluetoothActivity::class.java)) }
        }
        bluetoothButton.visibility = View.GONE

        val talkButton = Button(this).apply {
            text = "Tap to talk"; isAllCaps = false; textSize = 15f
            setTextColor(Color.WHITE); background = rounded(ink, 16)
            setOnClickListener { startListening() }
        }
        talkButton.visibility = View.GONE

        val typeButton = Button(this).apply {
            text = "Type a command"; isAllCaps = false; textSize = 14f
            setTextColor(ink); background = rounded(Color.rgb(236, 237, 246), 16)
            setOnClickListener { showTextCommandDialog() }
        }
        typeButton.visibility = View.GONE

        page.addView(text("Try: “Matty, open WhatsApp” · “Matty, tap Search” · “Matty, type hello” · “Matty, swipe left”", 12f, muted, false), params(bottom = 6))
        page.addView(text("PRIVACY FIRST · Commands are handled on your phone where possible. Offline speech depends on your Android speech service and downloaded language packs.", 11f, muted, false), params(top = 14))
        setContentView(scroll)
    }


    private fun isVoiceInvocation(): Boolean =
        assistantInvocationActive || intent?.getBooleanExtra(MattyWakeService.EXTRA_WAKE_WORD, false) == true ||
            intent?.getBooleanExtra(EXTRA_ASSISTANT_INVOKED, false) == true

    private fun requestDefaultAssistant() {
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            status.text = "This Android version does not expose the default assistant role."
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            status.text = "Matty is already your default assistant. Use your phone's assistant gesture to invoke it."
            return
        }
        try {
            startActivityForResult(roleManager.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), requestAssistantRole)
        } catch (_: Exception) {
            status.text = "Android couldn't open the default assistant selection screen."
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestAssistantRole) {
            val roleManager = getSystemService(RoleManager::class.java)
            val held = roleManager?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true
            assistantButton.text = if (held) "Matty is your default assistant ✓" else "Set Matty as default assistant"
            status.text = if (held) "Matty is selected. Android controls how the assistant is invoked on this phone."
                else "Matty was not selected as the default assistant."
        }
    }


    private fun requestCrossAppVoiceControl() {
        AlertDialog.Builder(this)
            .setTitle("Enable hands-free control in other apps?")
            .setMessage(
                "Matty can use Android Accessibility to tap or long-press a visible label you name, enter the exact text you dictate into a focused field, scroll or swipe, open notifications and Quick Settings, and use Back or Home. It does not make its own decisions about what to tap, and this feature does not upload screen content. Some secure screens and apps may block control. Android will show its own warning because this permission can operate the screen. You can disable Matty at any time in Accessibility settings."
            )
            .setNegativeButton("Not now", null)
            .setPositiveButton("I understand — continue") { _, _ ->
                getSharedPreferences("matty_privacy", MODE_PRIVATE)
                    .edit().putBoolean("cross_app_voice_control_disclosure_accepted", true).apply()
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    status.text = "Open Android Settings and search for Accessibility to enable Matty."
                }
            }
            .show()
    }

    private fun runCrossAppAction(action: () -> String) {
        val consent = getSharedPreferences("matty_privacy", MODE_PRIVATE)
            .getBoolean("cross_app_voice_control_disclosure_accepted", false)
        if (!consent) {
            status.text = "First enable cross-app voice control and review the disclosure."
            return
        }
        status.text = action()
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
        getSharedPreferences("matty_settings", MODE_PRIVATE).edit().putBoolean("hands_free_enabled", true).apply()
        refreshAccessState()
        val consentAccepted = getSharedPreferences("matty_privacy", MODE_PRIVATE)
            .getBoolean("cross_app_voice_control_disclosure_accepted", false)
        if (!consentAccepted) {
            status.text = "One-time setup: allow Matty to operate visible controls in other apps."
            requestCrossAppVoiceControl()
        } else if (!MattyAccessibilityService.isActive()) {
            status.text = "Enable Matty in Android Accessibility settings to control other apps."
            try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (_: Exception) { }
        } else {
            status.text = "Hands-free enabled. Say “Matty” and a command."
        }
        ensureWakeService()
    }

    private fun toggleHandsFree() {
        val prefs = getSharedPreferences("matty_settings", MODE_PRIVATE)
        if (prefs.getBoolean("hands_free_enabled", false)) {
            prefs.edit().putBoolean("hands_free_enabled", false).apply()
            stopService(Intent(this, MattyWakeService::class.java))
            refreshAccessState()
        } else {
            beginAccessSetup()
        }
    }

    private fun refreshAccessState() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val enabled = getSharedPreferences("matty_settings", MODE_PRIVATE).getBoolean("hands_free_enabled", false)
        accessButton.text = if (enabled) "Disable hands-free" else "Enable hands-free"
        status.text = when {
            !mic -> "Enable hands-free and allow microphone access."
            enabled -> "Hands-free is enabled. Say “Matty” followed by a command."
            else -> "Hands-free is off."
        }
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

    private fun selectedVoiceLocale(): Locale {
        val tag = getSharedPreferences("matty_settings", MODE_PRIVATE)
            .getString("voice_language", Locale.getDefault().toLanguageTag())
            ?: Locale.getDefault().toLanguageTag()
        return Locale.forLanguageTag(tag)
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            beginAccessSetup(); return
        }
        val offlineAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            SpeechRecognizer.isOnDeviceRecognitionAvailable(this)
        val fallbackAccepted = getSharedPreferences("matty_settings", MODE_PRIVATE)
            .getBoolean("online_speech_fallback_accepted", false)
        if (!offlineAvailable && !fallbackAccepted) {
            requestOnlineSpeechFallbackConsent { startListening() }
            return
        }
        assistantInvocationActive = false
        recognizer?.destroy()
        recognizer = try {
            if (offlineAvailable) SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) {
            status.text = "Couldn't start speech recognition. Check that an Android speech service is installed and try again."
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
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedVoiceLocale().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offlineAvailable)
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
            MattyCommandType.OPEN_QUICK_SETTINGS -> runCrossAppAction { MattyAccessibilityService.openQuickSettings() }
            MattyCommandType.OPEN_NOTIFICATIONS -> runCrossAppAction { MattyAccessibilityService.openNotifications() }
            MattyCommandType.GO_BACK -> runCrossAppAction { MattyAccessibilityService.goBack() }
            MattyCommandType.GO_HOME -> runCrossAppAction { MattyAccessibilityService.goHome() }
            MattyCommandType.VOLUME_DOWN -> adjustVolume(false)
            MattyCommandType.VOLUME_UP -> adjustVolume(true)
            MattyCommandType.START_PLAYBACK -> startPlayback()
            MattyCommandType.OPEN_MUSIC -> openMusic()
            MattyCommandType.OPEN_APP -> openApp(parsed.argument)
            MattyCommandType.INSTALL_APP -> openStoreSearch(parsed.argument)
            MattyCommandType.SEARCH_WEB -> searchWeb(parsed.argument)
            MattyCommandType.OPEN_APP_AND_CLICK_LINK -> {
                openApp(parsed.argument)
                status.text = "Opened ${parsed.argument}. To avoid tapping the wrong link, say “Matty, tap” followed by the exact visible link label."
            }
            MattyCommandType.TAP_TEXT -> runCrossAppAction { MattyAccessibilityService.tapVisibleLabel(parsed.argument) }
            MattyCommandType.LONG_PRESS_TEXT -> runCrossAppAction { MattyAccessibilityService.longPressVisibleLabel(parsed.argument) }
            MattyCommandType.TYPE_TEXT -> runCrossAppAction { MattyAccessibilityService.typeIntoFocusedField(parsed.argument) }
            MattyCommandType.SCROLL_UP -> runCrossAppAction { MattyAccessibilityService.scroll(false) }
            MattyCommandType.SCROLL_DOWN -> runCrossAppAction { MattyAccessibilityService.scroll(true) }
            MattyCommandType.SWIPE_LEFT -> runCrossAppAction { MattyAccessibilityService.swipeLeft() }
            MattyCommandType.SWIPE_RIGHT -> runCrossAppAction { MattyAccessibilityService.swipeRight() }
            MattyCommandType.CLICK_FIRST_LINK ->
                status.text = "Say 'tap' followed by the exact visible link label so Matty can target the right item."
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
