package com.heysia.app.actions

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.heysia.app.R

/** Small notifications from Sia, e.g. "Couldn't find that song", so you see it even if you missed what she said. */
class Notifier(private val context: Context) {

    init {
        val channel = NotificationChannel(CHANNEL_ID, "Messages from Sia", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Things Sia wants you to know, like a song she couldn't find."
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(title: String, text: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sia_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(nextId++, notification)
    }

    private companion object {
        const val CHANNEL_ID = "sia_messages"
        var nextId = 100
    }
}
