package com.heysia.app.notify

import android.app.Notification
import android.content.pm.PackageManager
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lets Sia read your notifications aloud when you ask ("read my notifications").
 * Nothing is saved: she looks at what's in your notification shade at that moment, and skips
 * private apps and anything that looks like a one-time code.
 */
class SiaNotificationListener : NotificationListenerService() {

    data class Item(val app: String, val title: String, val text: String)

    override fun onListenerConnected() {
        instance = this
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Readable notifications, newest first. */
    fun readable(): List<Item> {
        val privateApps = PrivateApps(this)
        val all = try {
            activeNotifications.orEmpty()
        } catch (e: SecurityException) {
            emptyArray()
        }
        return all
            .filter { isReadable(it) }
            .sortedByDescending { it.postTime }
            .mapNotNull { sbn ->
                val app = appLabel(sbn.packageName)
                if (privateApps.isPrivate(sbn.packageName, app)) return@mapNotNull null
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().trim()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))
                    ?.toString().orEmpty().trim()
                if (title.isEmpty() && text.isEmpty()) return@mapNotNull null
                if (PrivateApps.looksLikeCode("$title $text")) return@mapNotNull null
                Item(app, title, text.take(MAX_TEXT))
            }
            .distinct()
    }

    private fun isReadable(sbn: StatusBarNotification): Boolean {
        val n = sbn.notification
        return sbn.packageName != packageName &&
            !sbn.isOngoing &&
            n.flags and Notification.FLAG_GROUP_SUMMARY == 0 &&
            n.category !in setOf(
                Notification.CATEGORY_SERVICE, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_TRANSPORT,
                Notification.CATEGORY_SYSTEM, Notification.CATEGORY_NAVIGATION
            )
    }

    private fun appLabel(pkg: String): String = try {
        packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
    } catch (e: PackageManager.NameNotFoundException) {
        pkg
    }

    companion object {
        private const val MAX_TEXT = 200

        @Volatile
        var instance: SiaNotificationListener? = null
            private set
    }
}
