package com.heysia.app.actions

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Is the phone online right now? Sia never turns the internet on by herself. */
object Network {
    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
