package com.heysia.app.notify

import android.content.Context
import androidx.core.content.edit

/**
 * Apps whose notifications Sia never reads aloud. You can change the list in the app.
 * Starts with email, photos, banking, payment and password apps.
 */
class PrivateApps(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Your list, or the default one if you haven't changed it yet. */
    fun packages(): Set<String> = prefs.getStringSet(KEY, null) ?: DEFAULTS

    fun isPrivate(packageName: String, appLabel: String): Boolean {
        if (packageName in packages()) return true
        // Never edited by you: also treat anything that looks like banking, payments or email as private.
        if (prefs.getStringSet(KEY, null) == null) {
            val name = "$packageName $appLabel".lowercase()
            if (PRIVATE_WORDS.any { name.contains(it) }) return true
        }
        return false
    }

    /** Replaces the whole list with your choice. */
    fun save(packages: Set<String>) {
        prefs.edit { putStringSet(KEY, packages) }
    }

    companion object {
        private const val PREFS = "sia_private_apps"
        private const val KEY = "packages"

        val DEFAULTS = setOf(
            // Email
            "com.google.android.gm", "com.samsung.android.email.provider", "com.microsoft.office.outlook",
            "com.yahoo.mobile.client.android.mail",
            // Photos and gallery
            "com.google.android.apps.photos", "com.sec.android.gallery3d",
            // Notes and files
            "com.samsung.android.app.notes", "com.google.android.keep", "com.sec.android.app.myfiles",
            // Payments and banking (Bangladesh and global)
            "com.bKash.customerapp", "com.konasl.nagad", "com.dbbl.mbs.apps.main", "com.upay.android",
            "com.google.android.apps.walletnfcrel", "com.paypal.android.p2pmobile", "com.samsung.android.spay",
            // Passwords and two-factor codes
            "com.google.android.apps.authenticator2", "com.azure.authenticator", "com.x8bit.bitwarden"
        )

        private val PRIVATE_WORDS = listOf("bank", "wallet", "pay", "mail", "authenticator", "vault", "password")

        /** One-time codes are never read aloud, from any app. */
        private val CODE_PATTERN = Regex(
            "\\b(otp|one[- ]time|verification code|security code|login code|passcode|pin)\\b|\\bcode\\b.*\\b\\d{4,8}\\b|\\b\\d{4,8}\\b.*\\bcode\\b",
            RegexOption.IGNORE_CASE
        )

        fun looksLikeCode(text: String): Boolean = CODE_PATTERN.containsMatchIn(text)
    }
}
