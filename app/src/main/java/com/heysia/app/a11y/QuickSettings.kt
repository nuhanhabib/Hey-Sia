package com.heysia.app.a11y

import android.os.Build
import android.view.accessibility.AccessibilityNodeInfo
import com.heysia.app.brain.PhoneSetting
import kotlinx.coroutines.delay

/**
 * Switches settings that Android no longer lets apps change directly (Wi-Fi, Bluetooth, mobile data,
 * hotspot, airplane mode) by opening Quick Settings and tapping the tile, just like you would.
 */
class QuickSettings(private val robot: ScreenRobot) {

    enum class Result { Done, AlreadySet, NotFound }

    suspend fun set(setting: PhoneSetting, on: Boolean): Result {
        robot.openQuickSettings()
        delay(OPEN_DELAY_MS)
        try {
            val names = tileNames.getValue(setting)
            val tile = findTile(names) ?: return Result.NotFound
            if (isOn(tile) == on) return Result.AlreadySet
            if (!robot.tap(tile)) return Result.NotFound
            delay(TAP_DELAY_MS)
            confirmDialogIfShown()
            return Result.Done
        } finally {
            delay(TAP_DELAY_MS)
            robot.closeShade()
        }
    }

    /** Looks on the first page of tiles, then scrolls once to the next page. */
    private suspend fun findTile(names: List<String>): AccessibilityNodeInfo? {
        robot.waitFor(TILE_WAIT_MS) { tileMatch(it, names) }?.let { return it }
        robot.find { it.isScrollable }?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        delay(TAP_DELAY_MS)
        return robot.waitFor(TILE_WAIT_MS) { tileMatch(it, names) }
    }

    private fun tileMatch(node: AccessibilityNodeInfo, names: List<String>): Boolean =
        (node.isClickable || node.isCheckable) && ScreenRobot.labelStartsWith(node, names)

    /** Reads whether a tile is on: its switch state, or "On"/"Off" in what it announces. */
    private fun isOn(tile: AccessibilityNodeInfo): Boolean? {
        if (tile.isCheckable) return tile.isChecked
        val state = buildString {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) append(tile.stateDescription ?: "")
            append(" ").append(ScreenRobot.label(tile))
        }.lowercase()
        return when {
            Regex("\\b(off|disabled|not connected|turned off)\\b").containsMatchIn(state) -> false
            Regex("\\b(on|enabled|connected|turned on)\\b").containsMatchIn(state) -> true
            else -> null
        }
    }

    /** Some switches ask first ("Turn off mobile data?"). Press the confirm button if so. */
    private suspend fun confirmDialogIfShown() {
        val button = robot.waitFor(DIALOG_WAIT_MS) { node ->
            node.isClickable && node.className?.contains("Button") == true &&
                ScreenRobot.label(node).trim().lowercase() in confirmWords
        } ?: return
        robot.tap(button)
        delay(TAP_DELAY_MS)
    }

    private companion object {
        const val OPEN_DELAY_MS = 700L
        const val TAP_DELAY_MS = 500L
        const val TILE_WAIT_MS = 1500L
        const val DIALOG_WAIT_MS = 1200L

        /** Tile names on Samsung One UI and stock Android. */
        val tileNames = mapOf(
            PhoneSetting.WiFi to listOf("Wi-Fi", "WiFi", "Wireless"),
            PhoneSetting.Bluetooth to listOf("Bluetooth"),
            PhoneSetting.MobileData to listOf("Mobile data", "Mobile Data", "Cellular data", "Data"),
            PhoneSetting.Hotspot to listOf("Mobile Hotspot", "Hotspot", "Mobile hotspot"),
            PhoneSetting.Airplane to listOf("Airplane mode", "Flight mode", "Aeroplane mode")
        )
        val confirmWords = setOf("turn off", "turn on", "ok", "okay", "allow", "continue", "yes")
    }
}
