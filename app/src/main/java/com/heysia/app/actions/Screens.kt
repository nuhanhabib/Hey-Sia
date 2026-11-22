package com.heysia.app.actions

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.Settings

/** Opening other apps' screens from Sia's background service. */
object Screens {

    /** Android only lets a background app open screens if you allowed "Display over other apps". */
    fun canOpen(context: Context): Boolean = Settings.canDrawOverlays(context)

    /** Returns false if no app can handle it or Android refused. */
    fun open(context: Context, intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
