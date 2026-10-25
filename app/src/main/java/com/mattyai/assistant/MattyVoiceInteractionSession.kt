package com.mattyai.assistant

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionSession

/**
 * Hands the system-triggered interaction to Matty's existing, permission-aware
 * voice UI. Commands are still handled by the local deterministic command parser.
 */
class MattyVoiceInteractionSession(private val appContext: Context) : VoiceInteractionSession(appContext) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val launch = Intent(appContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_ASSISTANT_INVOKED, true)
        }
        startAssistantActivity(launch)
        finish()
    }
}