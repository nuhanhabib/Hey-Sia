package com.heysia.app.brain

import android.content.Context
import android.content.Intent
import android.provider.Settings
import com.heysia.app.a11y.Messaging
import com.heysia.app.a11y.QuickSettings
import com.heysia.app.a11y.ScreenRobot
import com.heysia.app.a11y.SiaAccessibilityService
import com.heysia.app.actions.AppLauncher
import com.heysia.app.actions.Calls
import com.heysia.app.actions.Contacts
import com.heysia.app.actions.DeviceControls
import com.heysia.app.actions.Music
import com.heysia.app.actions.Network
import com.heysia.app.actions.Notifier
import com.heysia.app.actions.Screens
import com.heysia.app.voice.Phrases
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Turns what you said into an action and a spoken reply. */
class SiaBrain(private val context: Context) {

    private val apps = AppLauncher(context)
    private val contacts = Contacts(context)
    private val calls = Calls(context)
    private val device = DeviceControls(context)
    private val music = Music(context, apps)
    private val notifier = Notifier(context)
    private val messaging = Messaging(context, apps)

    suspend fun handle(text: String): Reply = when (val command = CommandParser.parse(text)) {
        is Command.OpenApp -> openApp(command.name)
        is Command.Call -> call(command.who)
        is Command.CallNumber -> callNumber(command.number, spoken = spellOut(command.number))
        is Command.SetAlarm -> setAlarm(command)
        is Command.SetTimer -> setTimer(command.seconds)
        is Command.Flashlight -> flashlight(command.on)
        is Command.Volume -> volume(command)
        is Command.PlayMusic -> playMusic(command)
        is Command.Media -> media(command.action)
        is Command.Toggle -> toggle(command.setting, command.on)
        is Command.SendMessage -> sendMessage(command)
        Command.TellTime -> Reply("It's ${SimpleDateFormat("h:mm a", Locale.US).format(Date())}.")
        Command.TellDate -> Reply("Today is ${SimpleDateFormat("EEEE, MMMM d", Locale.US).format(Date())}.")
        Command.Battery -> battery()
        Command.Stop -> Reply("Okay.")
        Command.Greeting -> Reply("Hi! What can I do for you?")
        Command.Thanks -> Reply("You're welcome.")
        Command.Help -> Reply(HELP)
        is Command.Unknown -> unknown(command.text)
    }

    // ---------- Apps ----------

    private fun openApp(name: String?): Reply {
        if (name == null) return Reply("Which app?") { answer -> openApp(CommandParser.normalize(answer)) }
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        val app = apps.find(name) ?: return Reply("I couldn't find an app called $name.")
        return if (apps.open(app)) Reply("Opening ${app.label}.") else Reply("I couldn't open ${app.label}.")
    }

    // ---------- Calls ----------

    private fun call(who: String?): Reply {
        if (who == null) return Reply("Who should I call?") { answer -> call(CommandParser.normalize(answer)) }
        if (!contacts.allowed) return Reply(Phrases.NEED_CONTACTS)
        val matches = contacts.search(who)
        val best = matches.firstOrNull() ?: return Reply("I couldn't find $who in your contacts.")
        val second = matches.getOrNull(1)

        // Two people sound alike: ask which one.
        if (second != null && best.score - second.score < 0.05) {
            val a = best.person
            val b = second.person
            return Reply("Do you mean ${a.name} or ${b.name}?") { answer ->
                val t = answer.lowercase()
                when {
                    Answers.isNo(t) -> Reply("Okay, I won't call.")
                    t.contains("first") || Fuzzy.score(t, a.name) >= Fuzzy.score(t, b.name) -> callNumber(a.number, a.name)
                    else -> callNumber(b.number, b.name)
                }
            }
        }
        // Not sure we heard the name right: check first.
        if (best.score < SURE) {
            return Reply("Did you mean ${best.person.name}?") { answer ->
                if (Answers.isYes(answer)) callNumber(best.person.number, best.person.name) else Reply("Okay, I won't call.")
            }
        }
        return callNumber(best.person.number, best.person.name)
    }

    private fun callNumber(number: String, spoken: String): Reply {
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        return if (calls.call(number)) Reply("Calling $spoken.") else Reply("I couldn't start the call.")
    }

    // ---------- Alarms and timers ----------

    private fun setAlarm(command: Command.SetAlarm): Reply {
        val now = Calendar.getInstance()
        val (hour, minute) = when {
            command.inSeconds != null -> {
                now.add(Calendar.SECOND, command.inSeconds)
                now.get(Calendar.HOUR_OF_DAY) to now.get(Calendar.MINUTE)
            }
            command.time != null -> NumberWords.resolve(command.time, now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE))
            else -> return Reply("For what time?") { answer ->
                val time = NumberWords.parseTime(CommandParser.normalize(answer))
                if (time == null) Reply("Sorry, I didn't get the time.") else setAlarm(Command.SetAlarm(time))
            }
        }
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        return if (device.setAlarm(hour, minute)) Reply("Alarm set for ${clock(hour, minute)}.")
        else Reply("I couldn't find a clock app to set the alarm.")
    }

    private fun setTimer(seconds: Int?): Reply {
        if (seconds == null) return Reply("For how long?") { answer ->
            val s = NumberWords.parseDuration(CommandParser.normalize(answer))
            if (s == null) Reply("Sorry, I didn't get how long.") else setTimer(s)
        }
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        return if (device.setTimer(seconds)) Reply("Timer set for ${duration(seconds)}.")
        else Reply("I couldn't find a clock app to set the timer.")
    }

    // ---------- Music ----------

    private suspend fun playMusic(command: Command.PlayMusic): Reply {
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        val query = command.query
            ?: return if (music.openMusicApp()) Reply("Opening your music.") else Reply("I couldn't find a music app.")
        val online = Network.isOnline(context)

        return when (command.source) {
            MusicSource.YouTube -> if (online) youTube(query) else Reply(Phrases.OFFLINE)
            MusicSource.Local -> local(query, offerYouTube = false)
            // Online: YouTube, as you asked. Offline: your own songs.
            MusicSource.Any -> if (online) youTube(query) else local(query, offerYouTube = true)
        }
    }

    private fun local(query: String, offerYouTube: Boolean): Reply {
        if (!music.canReadSongs) return Reply(Phrases.NEED_MUSIC)
        val song = music.findLocal(query) ?: return notFound(
            query,
            if (offerYouTube) "I couldn't find $query on your phone. Say \"turn on data\" and I'll look on YouTube."
            else "I couldn't find $query on your phone."
        )
        return if (music.playLocal(song)) Reply("Playing ${song.title}" + (song.artist?.let { " by $it." } ?: "."))
        else Reply("I found ${song.title}, but no app could play it.")
    }

    private suspend fun youTube(query: String): Reply {
        val app = if (music.youTubeApp()?.contains("vanced") == true) "Vanced" else "YouTube"
        return when (music.playOnYouTube(query)) {
            Music.YouTubeResult.Playing -> Reply("Playing $query on $app.")
            Music.YouTubeResult.ShowingSearch -> Reply("Here are the results for $query on $app.")
            Music.YouTubeResult.Failed -> notFound(query, "I couldn't open YouTube to find $query.")
        }
    }

    /** Says it and leaves a notification, so you know even if you didn't hear her. */
    private fun notFound(query: String, speech: String): Reply {
        notifier.show("Song not found", "Sia couldn't find \"$query\".")
        return Reply(speech)
    }

    private fun media(action: MediaAction): Reply {
        when (action) {
            MediaAction.Pause -> music.pause()
            MediaAction.Resume -> music.resume()
            MediaAction.Next -> music.next()
            MediaAction.Previous -> music.previous()
        }
        return Reply("Okay.")
    }

    // ---------- Wi-Fi, Bluetooth, data, hotspot, airplane mode ----------

    private suspend fun toggle(setting: PhoneSetting, on: Boolean): Reply {
        val name = setting.spoken
        val service = SiaAccessibilityService.instance ?: return openSettingFallback(setting)
        return when (QuickSettings(ScreenRobot(service)).set(setting, on)) {
            QuickSettings.Result.Done -> Reply("$name ${if (on) "on" else "off"}.")
            QuickSettings.Result.AlreadySet -> Reply("$name is already ${if (on) "on" else "off"}.")
            QuickSettings.Result.NotFound -> Reply(
                "I couldn't find the $name switch in Quick Settings. Please add the $name tile to your Quick Settings panel."
            )
        }
    }

    /** Without the accessibility service, the best Sia can do is open the right settings page. */
    private fun openSettingFallback(setting: PhoneSetting): Reply {
        val action = when (setting) {
            PhoneSetting.WiFi -> Settings.Panel.ACTION_WIFI
            PhoneSetting.MobileData -> Settings.Panel.ACTION_INTERNET_CONNECTIVITY
            PhoneSetting.Bluetooth -> Settings.ACTION_BLUETOOTH_SETTINGS
            PhoneSetting.Hotspot -> Settings.ACTION_WIRELESS_SETTINGS
            PhoneSetting.Airplane -> Settings.ACTION_AIRPLANE_MODE_SETTINGS
        }
        val opened = Screens.canOpen(context) && Screens.open(context, Intent(action))
        return Reply(
            (if (opened) "I've opened the ${setting.spoken} settings. Tap the switch. " else "") + Phrases.NEED_ACCESSIBILITY
        )
    }

    // ---------- WhatsApp and Messenger ----------

    private suspend fun sendMessage(command: Command.SendMessage): Reply {
        val app = command.app ?: return Reply("On WhatsApp or Messenger?") { answer ->
            val t = answer.lowercase()
            val picked = when {
                t.contains("whats") || t.contains("what's") -> MessageApp.WhatsApp
                t.contains("messenger") || t.contains("facebook") -> MessageApp.Messenger
                else -> return@Reply Reply("Okay, I won't send it.")
            }
            sendMessage(command.copy(app = picked))
        }
        val to = command.to ?: return Reply("Who should I send it to?") { answer ->
            sendMessage(command.copy(to = CommandParser.normalize(answer).ifBlank { null } ?: return@Reply Reply("Okay, cancelled.")))
        }
        val text = command.text ?: return Reply("What should I say?") { answer ->
            if (answer.isBlank()) Reply("Okay, cancelled.") else sendMessage(command.copy(text = answer.trim()))
        }

        return when (app) {
            MessageApp.WhatsApp -> {
                if (!messaging.whatsAppInstalled()) return Reply("WhatsApp isn't installed.")
                if (!contacts.allowed) return Reply(Phrases.NEED_CONTACTS)
                if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
                val person = contacts.search(to).firstOrNull()?.person
                    ?: return Reply("I couldn't find $to in your contacts.")
                confirmThenSend(text, person.name, app) { messaging.sendWhatsApp(person.number, text) }
            }
            MessageApp.Messenger -> {
                if (!messaging.messengerInstalled()) return Reply("Messenger isn't installed.")
                if (!SiaAccessibilityService.isRunning) return Reply(Phrases.NEED_ACCESSIBILITY)
                confirmThenSend(text, to, app) { messaging.sendMessenger(to, text) }
            }
        }
    }

    /** Speech recognition can mishear, so Sia always reads the message back before sending. */
    private fun confirmThenSend(text: String, name: String, app: MessageApp, send: suspend () -> Messaging.Result) =
        Reply("Send \"$text\" to $name on ${app.spoken}?") { answer ->
            if (!Answers.isYes(answer)) return@Reply Reply("Okay, I didn't send it.")
            when (send()) {
                Messaging.Result.Sent -> Reply("Sent.")
                Messaging.Result.OpenedOnly -> Reply("I've opened the chat with your message. Tap send.")
                Messaging.Result.AppMissing -> Reply("${app.spoken} isn't installed.")
                Messaging.Result.Failed -> Reply("I couldn't send it on ${app.spoken}. Please send it yourself.")
            }
        }

    // ---------- Phone controls ----------

    private fun flashlight(on: Boolean): Reply = when {
        !device.setFlashlight(on) -> Reply("I couldn't control the flashlight. Is the camera in use?")
        on -> Reply("Flashlight on.")
        else -> Reply("Flashlight off.")
    }

    private fun volume(command: Command.Volume): Reply {
        when (command.change) {
            VolumeChange.Up -> device.volumeUp()
            VolumeChange.Down -> device.volumeDown()
            VolumeChange.Max -> device.setVolumePercent(100)
            VolumeChange.Mute -> return Reply("Muted.").also { device.mute() }
            VolumeChange.Unmute -> device.unmute()
            VolumeChange.Set -> device.setVolumePercent(command.percent ?: 50)
        }
        return Reply("Volume is at ${device.volumePercent()} percent.")
    }

    private fun battery(): Reply {
        val b = device.battery()
        return Reply("Battery is at ${b.percent} percent" + if (b.charging) " and charging." else ".")
    }

    // ---------- Fallback ----------

    /** Didn't match any command: maybe it's just an app name ("YouTube"). */
    private fun unknown(text: String): Reply {
        val app = apps.find(text, threshold = 0.88)
        if (app != null && Screens.canOpen(context) && apps.open(app)) return Reply("Opening ${app.label}.")
        return Reply(Phrases.NOT_UNDERSTOOD + " " + Phrases.TRY_HELP)
    }

    private fun clock(hour: Int, minute: Int): String {
        val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute) }
        return SimpleDateFormat("h:mm a", Locale.US).format(c.time)
    }

    private fun duration(seconds: Int): String {
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return listOfNotNull(
            h.takeIf { it > 0 }?.let { "$it hour" + if (it > 1) "s" else "" },
            m.takeIf { it > 0 }?.let { "$it minute" + if (it > 1) "s" else "" },
            s.takeIf { it > 0 }?.let { "$it second" + if (it > 1) "s" else "" }
        ).joinToString(" and ")
    }

    /** "01712" -> "0 1 7 1 2", so the voice reads digits one by one. */
    private fun spellOut(number: String) = number.toList().joinToString(" ")

    private companion object {
        const val SURE = 0.85
        const val HELP = "You can say things like: open YouTube, call Mom, set an alarm for six thirty, " +
            "timer for ten minutes, play Believer, next song, turn on Wi-Fi, " +
            "send a WhatsApp message to Rafi saying I'm on my way, flashlight on, volume up, what time is it, or battery."
    }
}
