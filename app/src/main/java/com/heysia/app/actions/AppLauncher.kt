package com.heysia.app.actions

import android.content.Context
import android.content.Intent
import com.heysia.app.brain.Fuzzy

/** Finds and opens installed apps by their spoken name. Only sees apps that have a home-screen icon. */
class AppLauncher(private val context: Context) {

    data class App(val label: String, val packageName: String)

    private var cache: List<App> = emptyList()
    private var cachedAt = 0L

    /** Installed apps with a launcher icon (refreshed at most once a minute). */
    fun apps(): List<App> {
        val now = System.currentTimeMillis()
        if (cache.isEmpty() || now - cachedAt > CACHE_MS) {
            val pm = context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            cache = pm.queryIntentActivities(launcher, 0)
                .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
                .filter { it.packageName != context.packageName }
                .distinctBy { it.packageName }
            cachedAt = now
        }
        return cache
    }

    /** The app you most likely meant, or null if nothing is close enough. */
    fun find(spoken: String, threshold: Double = 0.72): App? {
        val names = aliases[Fuzzy.compact(spoken)] ?: listOf(spoken)
        return names.firstNotNullOfOrNull { name -> Fuzzy.best(name, apps(), threshold) { listOf(it.label) }?.first }
    }

    fun isInstalled(packageName: String): Boolean =
        context.packageManager.getLaunchIntentForPackage(packageName) != null

    fun open(app: App): Boolean = open(app.packageName)

    fun open(packageName: String): Boolean {
        val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return Screens.open(context, intent)
    }

    private companion object {
        const val CACHE_MS = 60_000L

        /** Common ways to ask for an app whose real name is different. */
        val aliases = mapOf(
            "browser" to listOf("chrome", "internet", "samsung internet", "browser"),
            "internet" to listOf("internet", "chrome"),
            "gallery" to listOf("gallery", "photos"),
            "photos" to listOf("photos", "gallery"),
            "dialer" to listOf("phone"),
            "dialler" to listOf("phone"),
            "phone" to listOf("phone"),
            "sms" to listOf("messages"),
            "messages" to listOf("messages"),
            "text" to listOf("messages"),
            "music" to listOf("music", "samsung music", "youtube music"),
            "mail" to listOf("gmail", "email"),
            "email" to listOf("gmail", "email"),
            "store" to listOf("play store", "galaxy store"),
            "playstore" to listOf("play store"),
            "maps" to listOf("maps", "google maps"),
            "map" to listOf("maps"),
            "fb" to listOf("facebook"),
            "insta" to listOf("instagram"),
            "vanced" to listOf("youtube vanced", "vanced", "revanced"),
            "calculator" to listOf("calculator"),
            "notes" to listOf("samsung notes", "keep notes", "notes")
        )
    }
}
