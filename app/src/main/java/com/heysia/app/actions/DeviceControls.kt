package com.heysia.app.actions

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.AlarmClock
import kotlin.math.roundToInt

/** Flashlight, volume, battery, alarms and timers. None of these need a special permission. */
class DeviceControls(private val context: Context) {

    private val cameras = context.getSystemService(CameraManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)

    // ---------- Flashlight ----------

    private val torchCamera: String? by lazy {
        cameras.cameraIdList.firstOrNull { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    }

    /** Returns false if this phone has no flash or the camera is busy. */
    fun setFlashlight(on: Boolean): Boolean {
        val id = torchCamera ?: return false
        return try {
            cameras.setTorchMode(id, on)
            true
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Volume (media) ----------

    fun volumeUp() = repeat(2) { adjust(AudioManager.ADJUST_RAISE) }
    fun volumeDown() = repeat(2) { adjust(AudioManager.ADJUST_LOWER) }
    fun mute() = adjust(AudioManager.ADJUST_MUTE)
    fun unmute() = adjust(AudioManager.ADJUST_UNMUTE)

    fun setVolumePercent(percent: Int) {
        val max = audio.getStreamMaxVolume(STREAM)
        audio.setStreamVolume(STREAM, (max * percent / 100.0).roundToInt().coerceIn(0, max), AudioManager.FLAG_SHOW_UI)
    }

    fun volumePercent(): Int {
        val max = audio.getStreamMaxVolume(STREAM)
        return if (max == 0) 0 else (audio.getStreamVolume(STREAM) * 100.0 / max).roundToInt()
    }

    private fun adjust(direction: Int) = audio.adjustStreamVolume(STREAM, direction, AudioManager.FLAG_SHOW_UI)

    // ---------- Battery ----------

    data class Battery(val percent: Int, val charging: Boolean)

    fun battery(): Battery {
        val manager = context.getSystemService(BatteryManager::class.java)
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return Battery(
            percent = manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        )
    }

    // ---------- Clock app ----------

    /** Sets an alarm in your Clock app without opening it. */
    fun setAlarm(hour: Int, minute: Int): Boolean = Screens.open(
        context,
        Intent(AlarmClock.ACTION_SET_ALARM)
            .putExtra(AlarmClock.EXTRA_HOUR, hour)
            .putExtra(AlarmClock.EXTRA_MINUTES, minute)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Sia alarm")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    )

    /** Starts a timer in your Clock app without opening it. */
    fun setTimer(seconds: Int): Boolean = Screens.open(
        context,
        Intent(AlarmClock.ACTION_SET_TIMER)
            .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            .putExtra(AlarmClock.EXTRA_MESSAGE, "Sia timer")
            .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
    )

    private companion object {
        const val STREAM = AudioManager.STREAM_MUSIC
    }
}
