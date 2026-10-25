package com.mattyai.assistant

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Hands the system-triggered interaction to Matty's existing, permission-aware
 * voice UI. Commands are still handled by the local deterministic command parser.
 */
class MattyVoiceInteractionSession(context: android.content.Context) : VoiceInteractionSession(context) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val launch = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_ASSISTANT_INVOKED, true)
        }
        startAssistantActivity(launch)
        finish()
    }
}