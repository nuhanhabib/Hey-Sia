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
    /** [query] is null for "play some music". */
    data class PlayMusic(val query: String?, val source: MusicSource) : Command
    data class Media(val action: MediaAction) : Command
    data class Toggle(val setting: PhoneSetting, val on: Boolean) : Command
    /** Missing parts ([to], [text], or [app]) are asked for. */
    data class SendMessage(val app: MessageApp?, val to: String?, val text: String?) : Command
    data object ReadNotifications : Command
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

/** Where to look for a song. [Any]: YouTube when online, your own songs when offline. */
enum class MusicSource { Any, YouTube, Local }

enum class MediaAction { Pause, Resume, Next, Previous }

/** Settings Sia switches from Quick Settings. [spoken] is how she names them. */
enum class PhoneSetting(val spoken: String) {
    WiFi("Wi-Fi"), Bluetooth("Bluetooth"), MobileData("Mobile data"), Hotspot("Hotspot"), Airplane("Airplane mode")
}

enum class MessageApp(val spoken: String) { WhatsApp("WhatsApp"), Messenger("Messenger") }
