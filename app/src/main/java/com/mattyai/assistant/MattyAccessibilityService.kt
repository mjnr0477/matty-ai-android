package com.mattyai.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityEvent

internal enum class AccessibilityServiceAction { BACK, HOME, NOTIFICATIONS, QUICK_SETTINGS }

/** Executes only explicit, allow-listed Android navigation actions requested by the user. */
class MattyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Matty does not collect or transmit screen contents.
    }
    override fun onInterrupt() { }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    internal fun doAction(action: AccessibilityServiceAction): Boolean = when (action) {
        AccessibilityServiceAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        AccessibilityServiceAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        AccessibilityServiceAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        AccessibilityServiceAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
    }

    companion object {
        @Volatile var instance: MattyAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${MattyAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return TextUtils.SimpleStringSplitter(':').run {
                setString(enabled)
                any { it.equals(expected, ignoreCase = true) }
            }
        }
    }
}
