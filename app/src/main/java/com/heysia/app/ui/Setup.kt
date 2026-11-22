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
