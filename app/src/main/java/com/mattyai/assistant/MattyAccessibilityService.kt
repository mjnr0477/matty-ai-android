package com.mattyai.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Optional, user-enabled cross-app voice control.
 *
 * Executes only explicit, deterministic commands from the user. It does not
 * plan tasks, infer actions, or transmit screen content off-device.
 */
class MattyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        activeService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (activeService === this) activeService = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var activeService: MattyAccessibilityService? = null

        fun isActive(): Boolean = activeService != null

        private fun hasConsent(service: MattyAccessibilityService): Boolean =
            service.getSharedPreferences("matty_privacy", Context.MODE_PRIVATE)
                .getBoolean("cross_app_voice_control_disclosure_accepted", false)

        private fun consentMessage(service: MattyAccessibilityService): String? =
            if (hasConsent(service)) null else "Open Matty and accept the cross-app voice-control disclosure before using these actions."


        fun goBack(): String {
            val service = activeService ?: return "Enable Matty cross-app voice control in Accessibility settings first."
            consentMessage(service)?.let { return it }
            return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) "Went back."
                else "Android did not allow the Back action."
        }

        fun goHome(): String {
            val service = activeService ?: return "Enable Matty cross-app voice control in Accessibility settings first."
            consentMessage(service)?.let { return it }
            return if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) "Went to the Home screen."
                else "Android did not allow the Home action."
        }

        fun tapVisibleLabel(label: String): String {
            val service = activeService ?: return "Enable Matty cross-app voice control in Accessibility settings first."
            consentMessage(service)?.let { return it }
            val cleaned = label.trim()
            if (cleaned.isEmpty()) return "Tell Matty the exact visible button or label to tap."
            val root = service.rootInActiveWindow ?: return "No active app screen is available to Matty."
            val matches = try { root.findAccessibilityNodeInfosByText(cleaned).orEmpty() } catch (_: Exception) { emptyList() }
            val clickTargets = matches.mapNotNull { node ->
                var current: AccessibilityNodeInfo? = node
                var depth = 0
                while (current != null && depth < 8 && !current.isClickable) {
                    current = current.parent
                    depth++
                }
                current
            }.distinctBy { it.windowId.toString() + ":" + it.viewIdResourceName + ":" + it.text + ":" + it.contentDescription }
            if (clickTargets.isEmpty()) return "I couldn't find a tappable item named $cleaned on this screen."
            if (clickTargets.size > 1) return "I found more than one item named $cleaned. Say a more specific visible label."
            return if (clickTargets.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                "Tapped $cleaned."
            } else {
                "Android did not allow Matty to tap $cleaned."
            }
        }

        fun typeIntoFocusedField(value: String): String {
            val service = activeService ?: return "Enable Matty cross-app voice control in Accessibility settings first."
            consentMessage(service)?.let { return it }
            val root = service.rootInActiveWindow ?: return "No active app screen is available to Matty."
            val focused = try { root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) } catch (_: Exception) { null }
            if (focused == null || !focused.isEditable) {
                return "Focus a text field in the app first, then say 'Matty, type' followed by your text."
            }
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value)
            }
            return if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                "Entered the text you requested."
            } else {
                "This app or field does not allow Matty to enter text."
            }
        }

        fun scroll(down: Boolean): String {
            val service = activeService ?: return "Enable Matty cross-app voice control in Accessibility settings first."
            consentMessage(service)?.let { return it }
            val root = service.rootInActiveWindow ?: return "No active app screen is available to Matty."
            val action = if (down) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            val candidates = mutableListOf<AccessibilityNodeInfo>()
            fun visit(node: AccessibilityNodeInfo, depth: Int) {
                if (depth > 20) return
                if (node.isScrollable) candidates.add(node)
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    visit(child, depth + 1)
                }
            }
            visit(root, 0)
            val targets = candidates.distinctBy { it.windowId.toString() + ":" + it.viewIdResourceName + ":" + it.className }
            if (targets.isEmpty()) return "I couldn't find a scrollable area on this screen."
            val successful = targets.firstOrNull { it.performAction(action) }
            return if (successful != null) {
                if (down) "Scrolled down." else "Scrolled up."
            } else {
                "This app did not allow scrolling by accessibility action."
            }
        }
    }
}