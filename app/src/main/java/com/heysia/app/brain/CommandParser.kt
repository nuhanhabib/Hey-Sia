package com.heysia.app.brain

/**
 * Understands what you said, offline, with simple patterns.
 * Rules are checked top to bottom; the first one that fits wins.
 */
object CommandParser {

    private val leadingFillers = listOf(
        "hey sia", "hi sia", "okay sia", "sia", "please", "can you", "could you", "would you", "will you",
        "i want you to", "i want to", "i would like to", "i'd like to", "go ahead and", "just", "now"
    )
    private val trailingFillers = listOf("please", "for me", "now", "right now", "sia")

    fun parse(raw: String): Command {
        val text = normalize(raw)
        val words = text.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty()) return Command.Unknown(raw)

        return stop(text)
            ?: timer(text)
            ?: alarm(text)
            ?: call(text)
            ?: flashlight(text, words)
            ?: volume(text, words)
            ?: open(text)
            ?: info(text)
            ?: smallTalk(text)
            ?: Command.Unknown(text)
    }

    /** Lower-case, no punctuation, no "please"/"can you"/"hey Sia" around the actual command. */
    fun normalize(raw: String): String {
        var text = raw.lowercase().replace(Regex("[^a-z0-9' ]"), " ").replace(Regex("\\s+"), " ").trim()
        var changed = true
        while (changed) {
            changed = false
            for (f in leadingFillers) if (text == f || text.startsWith("$f ")) {
                text = text.removePrefix(f).trim(); changed = true
            }
            for (f in trailingFillers) if (text.endsWith(" $f")) {
                text = text.removeSuffix(f).trim(); changed = true
            }
        }
        return text
    }

    private fun stop(text: String): Command? =
        if (text in setOf(
                "stop", "cancel", "never mind", "nevermind", "nothing", "forget it", "quiet", "be quiet",
                "shut up", "that's all", "thats all", "bye", "goodbye", "no thanks", "exit"
            )
        ) Command.Stop else null

    private fun timer(text: String): Command? {
        if (!Regex("\\b(timer|countdown|count down)\\b").containsMatchIn(text)) return null
        return Command.SetTimer(NumberWords.parseDuration(text))
    }

    private fun alarm(text: String): Command? {
        val m = Regex("\\b(alarm|wake me up|wake me)\\b").find(text) ?: return null
        val after = text.substring(m.range.last + 1).trim()
        val inMatch = Regex("^(?:for |at )?in (.+)").find(after)
        if (inMatch != null) return Command.SetAlarm(null, NumberWords.parseDuration(inMatch.groupValues[1]))
        val timeText = after.removePrefix("for ").removePrefix("at ").trim()
        return Command.SetAlarm(NumberWords.parseTime(timeText))
    }

    private fun call(text: String): Command? {
        val m = Regex("^(call|phone|dial|ring|make a call to|make a call|place a call to)\\b(.*)").find(text) ?: return null
        val target = m.groupValues[2].trim()
            .removePrefix("to ").removeSuffix(" on mobile").removeSuffix(" on phone").removeSuffix(" mobile").trim()
        if (target.isEmpty()) return Command.Call(null)
        val number = NumberWords.digits(target.split(" "))
        if (number != null && number.count { it.isDigit() } >= 3) return Command.CallNumber(number)
        return Command.Call(target)
    }

    private fun flashlight(text: String, words: List<String>): Command? {
        if (!Regex("\\b(flashlight|flash light|torch|the light|flash)\\b").containsMatchIn(text)) return null
        val off = words.any { it in setOf("off", "disable", "stop", "close", "deactivate") }
        return Command.Flashlight(on = !off)
    }

    private fun volume(text: String, words: List<String>): Command? {
        val aboutVolume = "volume" in words || "sound" in words
        val change = when {
            "unmute" in words -> VolumeChange.Unmute
            "mute" in words || (aboutVolume && "silent" in words) -> VolumeChange.Mute
            words.any { it in setOf("louder") } -> VolumeChange.Up
            words.any { it in setOf("quieter", "softer") } -> VolumeChange.Down
            !aboutVolume -> return null
            words.any { it in setOf("max", "maximum", "full", "highest") } -> VolumeChange.Max
            words.any { it in setOf("up", "increase", "raise", "higher", "high") } -> VolumeChange.Up
            words.any { it in setOf("down", "decrease", "lower", "reduce", "low") } -> VolumeChange.Down
            else -> VolumeChange.Set
        }
        if (change != VolumeChange.Set) return Command.Volume(change)
        val n = words.indices.firstNotNullOfOrNull { NumberWords.parse(words, it) }?.value
        val percent = when {
            n == null -> return Command.Volume(VolumeChange.Up)
            "percent" in words || n > 10 -> n.coerceIn(0, 100)
            else -> n.coerceIn(0, 10) * 10 // "volume five" = 50%
        }
        return Command.Volume(VolumeChange.Set, percent)
    }

    private fun open(text: String): Command? {
        val m = Regex("^(open|launch|start|run|show me|show|go to|take me to)\\b(.*)").find(text) ?: return null
        val name = m.groupValues[2].trim()
            .removePrefix("the ").removeSuffix(" app").removeSuffix(" application").removeSuffix(" please").trim()
        return Command.OpenApp(name.ifEmpty { null })
    }

    private fun info(text: String): Command? = when {
        Regex("^(what's|what is|whats|tell me)? ?(the )?(current )?time( is it| now| please)?$").matches(text) ||
            text == "what time is it" || text == "time" -> Command.TellTime
        Regex("\\b(date|what day|which day|day is it|day today)\\b").containsMatchIn(text) -> Command.TellDate
        Regex("\\bbattery\\b|\\bcharge left\\b|\\bhow much charge\\b").containsMatchIn(text) -> Command.Battery
        else -> null
    }

    private fun smallTalk(text: String): Command? = when {
        text.startsWith("thank") -> Command.Thanks
        text in setOf("hello", "hi", "hey", "how are you", "how are you doing", "what's up", "whats up") -> Command.Greeting
        text == "help" || text.contains("what can you do") || text.contains("what do you do") -> Command.Help
        else -> null
    }
}
