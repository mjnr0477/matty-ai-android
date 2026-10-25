package com.mattyai.assistant

import android.service.voice.VoiceInteractionService

/**
 * System-integrated assistant entry point. The user must explicitly choose Matty
 * as the device's default digital assistant. Android owns system invocation and
 * decides when this service is active; this is not a promise of a custom hotword.
 */
class MattyVoiceInteractionService : VoiceInteractionService()