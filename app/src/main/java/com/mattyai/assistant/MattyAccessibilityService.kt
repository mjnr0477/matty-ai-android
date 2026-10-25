package com.mattyai.assistant

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

internal enum class AccessibilityServiceAction { BACK, HOME, NOTIFICATIONS, QUICK_SETTINGS, SCROLL_UP, SCROLL_DOWN }

/**
 * Performs a narrow set of local UI actions explicitly requested by the user.
 * Screen nodes are inspected only in memory for the current action and are never saved or transmitted.
 */
class MattyAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No event or screen content is persisted or transmitted.
    }
    override fun onInterrupt() { }
    override fun onDestroy() { if (instance === this) instance = null; super.onDestroy() }

    internal fun doAction(action: AccessibilityServiceAction): Boolean = when (action) {
        AccessibilityServiceAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
        AccessibilityServiceAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        AccessibilityServiceAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        AccessibilityServiceAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
        AccessibilityServiceAction.SCROLL_UP -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
        AccessibilityServiceAction.SCROLL_DOWN -> scroll(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
    }

    internal fun clickText(target: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val node = findTextNode(root, target.trim()) ?: return false
            performClickOnNodeOrParent(node)
        } finally {
            root.recycle()
        }
    }

    internal fun enterText(value: String): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val node = findEditableNode(root) ?: return false
            val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, value) }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } finally {
            root.recycle()
        }
    }

    private fun scroll(action: Int): Boolean {
        val root = rootInActiveWindow ?: return false
        return try {
            val scrollable = findScrollableNode(root) ?: root
            scrollable.performAction(action)
        } finally {
            root.recycle()
        }
    }

    private fun findTextNode(node: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val label = listOfNotNull(node.text?.toString(), node.contentDescription?.toString())
            .joinToString(" ").trim()
        if (label.equals(target, ignoreCase = true) || label.contains(target, ignoreCase = true)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findTextNode(child, target)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findEditableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditableNode(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun findScrollableNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable && node.isEnabled) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findScrollableNode(child)
            if (found != null) return found
            child.recycle()
        }
        return null
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo): Boolean {
        var candidate: AccessibilityNodeInfo? = node
        while (candidate != null) {
            if (candidate.isClickable && candidate.isEnabled &&
                candidate.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            candidate = candidate.parent
        }
        return false
    }

    companion object {
        @Volatile var instance: MattyAccessibilityService? = null
            private set

        fun isEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${MattyAccessibilityService::class.java.name}"
            val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
        }
    }
}
