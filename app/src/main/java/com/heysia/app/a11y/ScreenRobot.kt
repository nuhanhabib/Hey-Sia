package com.heysia.app.a11y

import android.accessibilityservice.AccessibilityService
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.heysia.app.brain.Fuzzy
import kotlinx.coroutines.delay

/** Finds things on screen and taps them, through Sia's accessibility service. */
class ScreenRobot(private val service: AccessibilityService) {

    /** Every window on screen (app, notification shade, dialogs). */
    private fun roots(): List<AccessibilityNodeInfo> =
        service.windows.mapNotNull { it.root }.ifEmpty { listOfNotNull(service.rootInActiveWindow) }

    /** First node anywhere on screen that matches, or null. */
    fun find(match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        for (root in roots()) search(root, match)?.let { return it }
        return null
    }

    fun findAll(match: (AccessibilityNodeInfo) -> Boolean): List<AccessibilityNodeInfo> {
        val out = mutableListOf<AccessibilityNodeInfo>()
        for (root in roots()) collect(root, match, out)
        return out
    }

    /** Keeps looking for up to [timeoutMs] while the screen loads. */
    suspend fun waitFor(timeoutMs: Long = 5000, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            find(match)?.let { return it }
            delay(POLL_MS)
        }
        return null
    }

    /** Taps the node, or its nearest tappable parent. */
    fun tap(node: AccessibilityNodeInfo): Boolean {
        var n: AccessibilityNodeInfo? = node
        while (n != null) {
            if (n.isClickable && n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
            n = n.parent
        }
        return false
    }

    fun type(node: AccessibilityNodeInfo, text: String): Boolean {
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun back() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)

    fun openQuickSettings() = service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)

    fun closeShade() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
        } else {
            back(); back()
        }
    }

    private fun search(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean): AccessibilityNodeInfo? {
        if (match(node)) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            search(child, match)?.let { return it }
        }
        return null
    }

    private fun collect(node: AccessibilityNodeInfo, match: (AccessibilityNodeInfo) -> Boolean, out: MutableList<AccessibilityNodeInfo>) {
        if (match(node)) out += node
        for (i in 0 until node.childCount) node.getChild(i)?.let { collect(it, match, out) }
    }

    companion object {
        private const val POLL_MS = 250L

        /** Text and description together, e.g. "Wi-Fi, On". */
        fun label(node: AccessibilityNodeInfo): String =
            listOfNotNull(node.text, node.contentDescription).joinToString(" ")

        /** True if the label starts with one of [names], ignoring case, spaces and hyphens ("Wi‑Fi" = "wifi"). */
        fun labelStartsWith(node: AccessibilityNodeInfo, names: List<String>): Boolean {
            val l = Fuzzy.compact(label(node))
            return l.isNotEmpty() && names.any { l.startsWith(Fuzzy.compact(it)) }
        }
    }
}
