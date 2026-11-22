package com.heysia.app.actions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/** Normal phone calls only (no WhatsApp/Messenger calls). */
class Calls(private val context: Context) {

    /** Calls straight away if allowed, otherwise opens the dialer with the number filled in. */
    fun call(number: String): Boolean {
        val canCall = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        val action = if (canCall) Intent.ACTION_CALL else Intent.ACTION_DIAL
        return Screens.open(context, Intent(action, Uri.fromParts("tel", number, null)))
    }
}
