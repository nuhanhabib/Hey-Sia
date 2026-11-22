package com.heysia.app.brain

import android.app.AlarmManager
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.telephony.TelephonyManager
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
import com.heysia.app.actions.News
import com.heysia.app.actions.Notifier
import com.heysia.app.actions.Screens
import com.heysia.app.notify.SiaNotificationListener
import com.heysia.app.voice.Phrases
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.net.URLEncoder
import kotlinx.coroutines.delay

/** Turns what you said into an action and a spoken reply. */
class SiaBrain(private val context: Context) {

    private val apps = AppLauncher(context)
    private val contacts = Contacts(context)
    private val calls = Calls(context)
    private val device = DeviceControls(context)
    private val music = Music(context, apps)
    private val notifier = Notifier(context)
    private val messaging = Messaging(context, apps)
    private val news = News(countryCode())

    /**
     * The speech recogniser gives several guesses of what you said, best first.
     * Use the first one that makes sense as a command; otherwise the best guess.
     */
    fun pick(guesses: List<String>): String {
        val parsed = guesses.map { it to CommandParser.parse(it) }
        // Best: a guess naming an app you have, or a person in your contacts.
        parsed.firstOrNull { (_, c) ->
            (c is Command.OpenApp && c.name != null && apps.find(c.name) != null) ||
                (c is Command.Call && c.who != null && contacts.search(c.who).any { it.score >= SURE })
        }?.let { return it.first }
        // Next: any guess that is a command Sia knows.
        return parsed.firstOrNull { it.second !is Command.Unknown }?.first ?: guesses.first()
    }

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
        Command.ReadNotifications -> readNotifications()
        is Command.News -> news(command.topic)
        Command.Briefing -> briefing()
        is Command.WebSearch -> webSearch(command.query)
        Command.OnlineStatus -> Reply(if (Network.isOnline(context)) "You're online." else "You're offline.")
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
            QuickSettings.Result.Done ->
                if (on && setting in CONNECTIONS) Reply(waitUntilOnline(name)) else Reply("$name ${if (on) "on" else "off"}.")
            QuickSettings.Result.AlreadySet -> Reply("$name is already ${if (on) "on" else "off"}.")
            QuickSettings.Result.NotFound -> Reply(
                "I couldn't find the $name switch in Quick Settings. Please add the $name tile to your Quick Settings panel."
            )
        }
    }

    /** After switching data or Wi-Fi on, wait a little for the connection, then say whether it worked. */
    private suspend fun waitUntilOnline(name: String): String {
        repeat(ONLINE_WAIT_STEPS) {
            if (Network.isOnline(context)) return "$name on. I'm online now."
            delay(ONLINE_WAIT_STEP_MS)
        }
        return "$name is on, but I'm not connected to the internet yet."
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

    // ---------- Notifications ----------

    private fun readNotifications(): Reply {
        val listener = SiaNotificationListener.instance ?: return Reply(Phrases.NEED_NOTIFICATION_ACCESS)
        val items = listener.readable()
        if (items.isEmpty()) return Reply("You have no new notifications.")
        val intro = if (items.size == 1) "You have one notification. " else "You have ${items.size} notifications. "
        return readBatch(items, 0, intro)
    }

    /** Reads [NOTIFICATIONS_PER_TURN] at a time, then offers to continue. */
    private fun readBatch(items: List<SiaNotificationListener.Item>, from: Int, intro: String = ""): Reply {
        val batch = items.drop(from).take(NOTIFICATIONS_PER_TURN)
        val spoken = batch.joinToString(" ") { item ->
            val who = if (item.title.isNotEmpty() && item.title != item.app) "${item.app}, ${item.title}" else item.app
            "From $who: ${item.text.ifEmpty { "new notification" }}."
        }
        val next = from + batch.size
        if (next >= items.size) return Reply(intro + spoken)
        return Reply("$intro$spoken Shall I read more?") { answer ->
            if (Answers.isYes(answer)) readBatch(items, next) else Reply("Okay.")
        }
    }

    // ---------- News, briefing and web search (online only) ----------

    private suspend fun news(topic: String?): Reply {
        if (!Network.isOnline(context)) return Reply(Phrases.OFFLINE)
        val headlines = news.headlines(topic)
        if (headlines.isEmpty()) return Reply("Sorry, I couldn't get the news right now.")
        val intro = if (topic == null) "Here are today's top headlines. " else "Here's the latest on $topic. "
        return Reply(intro + numbered(headlines))
    }

    /** Your morning briefing: everything you need to plan the day, in one go. */
    private suspend fun briefing(): Reply {
        val now = Calendar.getInstance()
        val greeting = when (now.get(Calendar.HOUR_OF_DAY)) {
            in 4..11 -> "Good morning!"
            in 12..16 -> "Good afternoon!"
            else -> "Good evening!"
        }
        val parts = mutableListOf(
            greeting,
            "It's ${SimpleDateFormat("h:mm a", Locale.US).format(now.time)} on ${SimpleDateFormat("EEEE, MMMM d", Locale.US).format(now.time)}."
        )
        nextAlarm()?.let { parts += it }
        val b = device.battery()
        parts += "Battery is at ${b.percent} percent."
        if (Network.isOnline(context)) {
            val headlines = news.headlines(count = BRIEFING_HEADLINES)
            if (headlines.isNotEmpty()) parts += "Top news. " + numbered(headlines)
        } else {
            parts += "You're offline, so I can't get the news. Say \"turn on data\" if you want the headlines."
        }
        parts += "Have a great day!"
        return Reply(parts.joinToString(" "))
    }

    /** "Your next alarm is at 6:00 AM." if one is set within the next day. */
    private fun nextAlarm(): String? {
        val next = context.getSystemService(AlarmManager::class.java).nextAlarmClock ?: return null
        val inMs = next.triggerTime - System.currentTimeMillis()
        if (inMs !in 0..DAY_MS) return null
        return "Your next alarm is at ${SimpleDateFormat("h:mm a", Locale.US).format(Date(next.triggerTime))}."
    }

    private fun webSearch(query: String): Reply {
        if (!Network.isOnline(context)) return Reply(Phrases.OFFLINE)
        if (!Screens.canOpen(context)) return Reply(Phrases.NEED_OPEN_PERMISSION)
        val opened = Screens.open(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)) ||
            Screens.open(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + URLEncoder.encode(query, "UTF-8"))))
        return if (opened) Reply("Searching for $query.") else Reply("I couldn't open a browser.")
    }

    private fun numbered(items: List<String>): String =
        items.mapIndexed { i, h -> "${ORDINALS.getOrElse(i) { "Next" }}. $h." }.joinToString(" ")

    private fun countryCode(): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return tm?.simCountryIso?.ifBlank { null } ?: tm?.networkCountryIso?.ifBlank { null } ?: Locale.getDefault().country
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
        const val NOTIFICATIONS_PER_TURN = 5
        const val BRIEFING_HEADLINES = 3
        const val ONLINE_WAIT_STEPS = 12
        const val ONLINE_WAIT_STEP_MS = 1000L
        const val DAY_MS = 24 * 60 * 60 * 1000L
        val CONNECTIONS = setOf(PhoneSetting.MobileData, PhoneSetting.WiFi)
        val ORDINALS = listOf("One", "Two", "Three", "Four", "Five")
        const val HELP = "You can say things like: open YouTube, call Mom, set an alarm for six thirty, " +
            "timer for ten minutes, play Believer, next song, turn on Wi-Fi, " +
            "send a WhatsApp message to Rafi saying I'm on my way, read my notifications, tell me today's news, good morning, flashlight on, volume up, what time is it, or battery."
    }
}
