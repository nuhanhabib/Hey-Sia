package com.heysia.app.listen

import android.content.Context
import androidx.core.content.edit
import java.util.Locale

/** How Sia listens to your commands. Saved on the phone. */
class ListenSettings(context: Context) {

    enum class Engine(val title: String, val description: String) {
        Phone(
            "Phone's speech recognition (recommended)",
            "Much more accurate, understands accents well. Works offline once the offline English pack is downloaded."
        ),
        Vosk(
            "Sia's built-in offline model",
            "100% offline and private, but less accurate, especially with accents."
        )
    }

    enum class Accent(val title: String, val tag: String) {
        Auto("Same as phone", ""),
        US("English (US)", "en-US"),
        UK("English (UK)", "en-GB"),
        India("English (India)", "en-IN"),
        Australia("English (Australia)", "en-AU")
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var engine: Engine
        get() = prefs.getString(KEY_ENGINE, null)?.let { runCatching { Engine.valueOf(it) }.getOrNull() } ?: Engine.Phone
        set(value) = prefs.edit { putString(KEY_ENGINE, value.name) }

    var accent: Accent
        get() = prefs.getString(KEY_ACCENT, null)?.let { runCatching { Accent.valueOf(it) }.getOrNull() } ?: Accent.Auto
        set(value) = prefs.edit { putString(KEY_ACCENT, value.name) }

    /** Language tag sent to the recogniser, e.g. "en-US". */
    fun languageTag(): String {
        accent.tag.takeIf { it.isNotEmpty() }?.let { return it }
        val device = Locale.getDefault()
        return if (device.language == "en") device.toLanguageTag() else "en-US"
    }

    private companion object {
        const val PREFS = "sia_listen"
        const val KEY_ENGINE = "engine"
        const val KEY_ACCENT = "accent"
    }
}
