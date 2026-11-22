package com.heysia.app.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.heysia.app.MainActivity
import com.heysia.app.service.SiaService
import com.heysia.app.service.SiaState

/** A "Sia" tile for Quick Settings: tap it to talk without saying "Hey Sia". */
class SiaTileService : TileService() {

    override fun onStartListening() {
        qsTile?.apply {
            state = if (SiaState.phase.value == SiaState.Phase.Off) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
            updateTile()
        }
    }

    @SuppressLint("StartActivityAndCollapseDeprecated") // the old call only runs on Android 13 and below
    override fun onClick() {
        if (SiaState.phase.value != SiaState.Phase.Off) {
            SiaService.talkNow(this)
            return
        }
        // Sia is off: Android only allows switching the microphone on from a visible screen, so open the app.
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_TALK)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
