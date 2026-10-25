package com.mattyai.assistant

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
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
        window.statusBarColor = Color.rgb(247, 248, 255)
        window.navigationBarColor = Color.rgb(247, 248, 255)
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        buildUi()
        refreshAccessState()
    }

    override fun onResume() {
        super.onResume()
        if (::accessButton.isInitialized) refreshAccessState()
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
        page.addView(text("Open apps, find music and navigate phone settings with simple voice commands — with offline-first command handling.", 15f, muted, false), params(bottom = 20))

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
        val speak = Button(this).apply {
            text = "Speak to Matty  ·  🎙"; textSize = 15f; isAllCaps = false; setTextColor(Color.WHITE)
            background = rounded(purple, 18); setPadding(dp(18), dp(8), dp(18), dp(8))
            setOnClickListener { startListening() }
        }
        hero.addView(speak, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)))
        page.addView(hero, params(bottom = 16))

        val accessCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(17), dp(18), dp(17))
            background = rounded(Color.rgb(241, 239, 255), 22)
        }
        accessCard.addView(text("One setup, then you're ready", 17f, ink, true), params(bottom = 6))
        accessCard.addView(text("Android asks you to approve microphone access and phone-control accessibility separately. These system permissions cannot be granted by one in-app button.", 13f, muted, false), params(bottom = 14))
        accessButton = Button(this).apply {
            text = "Allow Matty to access my phone"; isAllCaps = false; textSize = 14f; setTextColor(Color.WHITE)
            background = rounded(ink, 16); setOnClickListener { beginAccessSetup() }
        }
        accessCard.addView(accessButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)))
        page.addView(accessCard, params(bottom = 20))

        page.addView(text("TRY SAYING", 11f, muted, true), params(bottom = 10))
        val examples = listOf("Open WhatsApp", "Open Wi-Fi settings", "Open mobile data settings", "Play music", "Turn volume down", "Open TikTok")
        examples.forEach { example ->
            val chip = TextView(this).apply {
                text = "↗   $example"; textSize = 14f; setTextColor(ink); setPadding(dp(15), dp(13), dp(15), dp(13))
                background = rounded(Color.rgb(247, 248, 252), 14)
            }
            page.addView(chip, params(bottom = 7))
        }
        page.addView(text("PRIVACY FIRST · Commands are handled on your phone where possible. Offline speech depends on your Android speech service and downloaded language packs.", 11f, muted, false), params(top = 14))
        setContentView(scroll)
    }

    private fun beginAccessSetup() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), requestAudio)
            return
        }
        if (!MattyAccessibilityService.isEnabled(this)) {
            status.text = "Enable Matty in Android Accessibility settings, then return here."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        } else {
            status.text = "Matty has the permissions currently needed for supported commands."
            refreshAccessState()
        }
    }

    private fun refreshAccessState() {
        val mic = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val accessibility = MattyAccessibilityService.isEnabled(this)
        accessButton.text = when {
            !mic -> "Allow Matty to access my phone"
            !accessibility -> "Finish setup: enable phone control"
            else -> "Phone access enabled ✓"
        }
        status.text = when {
            !mic -> "Microphone permission is needed for voice commands."
            !accessibility -> "Microphone ready. Enable Matty's Accessibility Service for supported phone navigation."
            else -> "Matty is ready for supported voice commands. Tap Speak to Matty to begin."
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestAudio) {
            refreshAccessState()
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && !MattyAccessibilityService.isEnabled(this)) {
                status.text = "Microphone allowed. Next, enable Matty in Accessibility settings."
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun startListening() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            beginAccessSetup(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            status.text = "Speech recognition isn't available. Install or enable a speech service, then try again."
            return
        }
        recognizer?.destroy()
        recognizer = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(this))
                SpeechRecognizer.createOnDeviceSpeechRecognizer(this)
            else SpeechRecognizer.createSpeechRecognizer(this)
        } catch (_: Exception) { SpeechRecognizer.createSpeechRecognizer(this) }
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "Listening… say a command." }
            override fun onBeginningOfSpeech() { status.text = "I'm listening." }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "Working on it…" }
            override fun onError(error: Int) { status.text = "Didn't catch that. Check speech service / offline language pack and try again." }
            override fun onResults(results: Bundle?) {
                val phrase = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
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
        val command = raw.trim().lowercase(Locale.ROOT).replace(Regex("^(hey\\s+)?matty[ ,]*"), "").trim()
        transcript.text = if (command.isBlank()) raw else "Heard: $raw"
        when {
            command.contains("wifi") || command.contains("wi-fi") -> openSettings(Settings.ACTION_WIFI_SETTINGS, "Opening Wi-Fi settings.")
            command.contains("mobile data") || command.contains("cellular data") || command == "data" -> openSettings(Settings.ACTION_DATA_USAGE_SETTINGS, "Opening mobile data settings. Android controls whether data can be switched directly.")
            command.contains("bluetooth") -> openSettings(Settings.ACTION_BLUETOOTH_SETTINGS, "Opening Bluetooth settings.")
            command.contains("internet settings") || command.contains("network settings") -> openSettings(Settings.ACTION_WIRELESS_SETTINGS, "Opening network settings.")
            command.contains("notification") -> globalAction(AccessibilityServiceAction.NOTIFICATIONS, "Opening notifications.")
            command.contains("quick settings") || command.contains("control center") -> globalAction(AccessibilityServiceAction.QUICK_SETTINGS, "Opening quick settings.")
            command.contains("go back") || command == "back" -> globalAction(AccessibilityServiceAction.BACK, "Going back.")
            command.contains("go home") || command == "home" -> globalAction(AccessibilityServiceAction.HOME, "Going home.")
            command.contains("volume down") || command.contains("turn down volume") || command.contains("lower volume") -> adjustVolume(false)
            command.contains("volume up") || command.contains("turn up volume") || command.contains("raise volume") -> adjustVolume(true)
            command.contains("play music") || command.contains("start music") || command.contains("start this song") || command.contains("open music") -> openMusic()
            command.startsWith("open ") || command.startsWith("launch ") || command.startsWith("start ") -> openApp(command.substringAfter(' ').trim())
            else -> status.text = "I heard you, but don't have a safe action for that command yet. Try one of the examples below."
        }
    }

    private fun openSettings(action: String, message: String) {
        try { startActivity(Intent(action)); status.text = message }
        catch (_: Exception) { status.text = "Android couldn't open that settings page on this device." }
    }

    private fun globalAction(action: AccessibilityServiceAction, message: String) {
        val service = MattyAccessibilityService.instance
        if (service == null) { status.text = "Enable Matty's Accessibility Service first to use this phone navigation command."; return }
        status.text = if (service.doAction(action)) message else "Android did not allow that navigation action."
    }

    private fun adjustVolume(up: Boolean) {
        try {
            val audio = getSystemService(AUDIO_SERVICE) as AudioManager
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
            status.text = if (up) "Music volume increased." else "Music volume decreased."
        } catch (_: Exception) { status.text = "Couldn't adjust media volume." }
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
