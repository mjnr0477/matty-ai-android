package com.mattyai.assistant

import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

class MattyVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: android.os.Bundle?): VoiceInteractionSession {
        return MattyVoiceInteractionSession(this)
    }
}