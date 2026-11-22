package com.heysia.app.a11y

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

/**
 * Lets Sia tap switches and buttons for you, only when you ask.
 * It ignores every screen event: it looks at the screen only while carrying out a command.
 */
class SiaAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit // nothing is watched or stored

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: SiaAccessibilityService? = null
            private set

        val isRunning: Boolean get() = instance != null
    }
}
