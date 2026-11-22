package com.heysia.app.brain

/** Everything Sia knows how to do, as understood from a sentence. Pure data, no Android code. */
sealed interface Command {
    data class OpenApp(val name: String?) : Command
    data class Call(val who: String?) : Command
    data class CallNumber(val number: String) : Command
    /** [time] is null when you didn't say a time (she will ask); [inSeconds] for "in ten minutes". */
    data class SetAlarm(val time: NumberWords.ClockTime?, val inSeconds: Int? = null) : Command
    data class SetTimer(val seconds: Int?) : Command
    data class Flashlight(val on: Boolean) : Command
    data class Volume(val change: VolumeChange, val percent: Int? = null) : Command
    data object TellTime : Command
    data object TellDate : Command
    data object Battery : Command
    data object Stop : Command
    data object Greeting : Command
    data object Thanks : Command
    data object Help : Command
    data class Unknown(val text: String) : Command
}

enum class VolumeChange { Up, Down, Max, Mute, Unmute, Set }
