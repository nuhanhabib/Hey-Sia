package com.heysia.app.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.heysia.app.actions.Music

/**
 * One thing Sia needs you to allow. Each item says exactly why it's needed.
 *
 * Either [permissions] (asked with the normal Android pop-up) or [settingsScreen]
 * (a toggle you switch on in Settings) is set.
 */
data class SetupItem(
    val title: String,
    val why: String,
    val required: Boolean,
    val permissions: List<String> = emptyList(),
    val settingsScreen: ((Context) -> Intent)? = null,
    val isDone: (Context) -> Boolean
)

object Setup {

    fun items(): List<SetupItem> = buildList {
        add(
            SetupItem(
                title = "Microphone",
                why = "To hear \"Hey Sia\" and your commands. Speech is processed on your phone only.",
                required = true,
                permissions = listOf(Manifest.permission.RECORD_AUDIO),
                isDone = { granted(it, Manifest.permission.RECORD_AUDIO) }
            )
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(
                SetupItem(
                    title = "Notifications",
                    why = "To show that Sia is listening, and to tell you when a song can't be found.",
                    required = false,
                    permissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                    isDone = { granted(it, Manifest.permission.POST_NOTIFICATIONS) }
                )
            )
        }
        add(
            SetupItem(
                title = "Open apps",
                why = "Lets Sia open apps, the dialer and the clock while she runs in the background " +
                    "(Android calls this \"Display over other apps\").",
                required = false,
                settingsScreen = { context ->
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.fromParts("package", context.packageName, null))
                },
                isDone = { Settings.canDrawOverlays(it) }
            )
        )
        add(
            SetupItem(
                title = "Contacts",
                why = "To call people by name. Sia reads only names and numbers, and never stores them.",
                required = false,
                permissions = listOf(Manifest.permission.READ_CONTACTS),
                isDone = { granted(it, Manifest.permission.READ_CONTACTS) }
            )
        )
        add(
            SetupItem(
                title = "Phone calls",
                why = "To start a call straight away. Without it, Sia opens the dialer and you press call.",
                required = false,
                permissions = listOf(Manifest.permission.CALL_PHONE),
                isDone = { granted(it, Manifest.permission.CALL_PHONE) }
            )
        )
        add(
            SetupItem(
                title = "Music files",
                why = "To find and play songs saved on your phone when you're offline. Audio files only: no photos, videos or documents.",
                required = false,
                permissions = listOf(Music.audioPermission),
                isDone = { granted(it, Music.audioPermission) }
            )
        )
        add(
            SetupItem(
                title = "Keep running in background",
                why = "Samsung phones close background apps to save battery. This keeps Sia awake.",
                required = false,
                settingsScreen = { context ->
                    Intent(
                        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                },
                isDone = { context ->
                    context.getSystemService(PowerManager::class.java)
                        .isIgnoringBatteryOptimizations(context.packageName)
                }
            )
        )
    }

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Android's page for this app, where you can change any permission by hand. */
    fun appDetails(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
}
