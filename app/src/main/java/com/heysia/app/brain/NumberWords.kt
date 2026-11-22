package com.heysia.app.brain

/**
 * Turns spoken numbers, times and durations into values.
 * The offline speech model writes numbers as words ("six thirty", "ten minutes"), never digits.
 */
object NumberWords {

    private val units = mapOf(
        "zero" to 0, "oh" to 0, "o" to 0, "one" to 1, "a" to 1, "an" to 1, "two" to 2, "three" to 3,
        "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val tens = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )
    private val digitWords = mapOf(
        "zero" to '0', "oh" to '0', "o" to '0', "one" to '1', "two" to '2', "three" to '3', "four" to '4',
        "five" to '5', "six" to '6', "seven" to '7', "eight" to '8', "nine" to '9'
    )

    /** A number read from the start of [words]: its value and how many words it used. */
    data class Parsed(val value: Int, val used: Int)

    /**
     * Reads a number like "seven", "twenty five", "one hundred and twenty" or "15" from the start of [words].
     * "a"/"an" count as 1 only when followed by a unit word, handled by callers.
     */
    fun parse(words: List<String>, start: Int = 0): Parsed? {
        var i = start
        var total = 0
        var current = 0
        var any = false
        while (i < words.size) {
            val w = words[i]
            val digits = w.toIntOrNull()
            when {
                digits != null && !any -> return Parsed(digits, 1)
                w in tens -> {
                    if (current % 100 >= 20 || current % 100 in 1..19) break
                    current += tens.getValue(w); any = true
                }
                w in units && w !in setOf("a", "an", "o", "oh") -> {
                    if (current % 10 != 0 || current % 100 in 10..19) break
                    current += units.getValue(w); any = true
                }
                w == "hundred" && any -> current = maxOf(current, 1) * 100
                w == "thousand" && any -> { total += maxOf(current, 1) * 1000; current = 0 }
                w == "and" && any && i + 1 < words.size && (words[i + 1] in units || words[i + 1] in tens) -> Unit
                else -> break
            }
            i++
        }
        return if (any) Parsed(total + current, i - start) else null
    }

    /** "zero one seven one two", "double five" or "plus eight eight" -> "01712", "55", "+88". Null if not all digits. */
    fun digits(words: List<String>): String? {
        val out = StringBuilder()
        var i = 0
        while (i < words.size) {
            val w = words[i]
            when {
                w == "double" && words.getOrNull(i + 1) in digitWords -> {
                    repeat(2) { out.append(digitWords.getValue(words[i + 1])) }
                    i++
                }
                w == "plus" && out.isEmpty() -> out.append('+')
                w in digitWords -> out.append(digitWords.getValue(w))
                w.isNotEmpty() && w.all { it.isDigit() } -> out.append(w)
                else -> return null
            }
            i++
        }
        return out.toString().takeIf { it.any { c -> c.isDigit() } }
    }

    /**
     * A clock time. [hour] is 0-23 when [meridiemKnown], otherwise 1-12 (or 0-23 if said as 24h)
     * and the caller picks the next matching morning/evening time.
     */
    data class ClockTime(val hour: Int, val minute: Int, val meridiemKnown: Boolean)

    /** Understands "six", "six thirty", "six thirty pm", "half past six", "quarter to seven", "noon", "seven in the evening". */
    fun parseTime(text: String): ClockTime? {
        val words = text.lowercase().replace(".", " ").replace("o'clock", " ").replace(":", " ")
            .split(" ").filter { it.isNotBlank() && it != "at" && it != "for" && it != "clock" }
        if (words.isEmpty()) return null
        if ("noon" in words || "midday" in words) return ClockTime(12, 0, true)
        if ("midnight" in words) return ClockTime(0, 0, true)

        var hour: Int
        var minute = 0
        var i = 0
        when {
            words[0] == "half" && words.getOrNull(1) == "past" -> {
                hour = parse(words, 2)?.also { i = 2 + it.used }?.value ?: return null
                minute = 30
            }
            words[0] == "quarter" && words.getOrNull(1) == "past" -> {
                hour = parse(words, 2)?.also { i = 2 + it.used }?.value ?: return null
                minute = 15
            }
            words[0] == "quarter" && words.getOrNull(1) == "to" -> {
                hour = (parse(words, 2)?.also { i = 2 + it.used }?.value ?: return null) - 1
                if (hour < 0) hour = 23
                minute = 45
            }
            else -> {
                val h = parse(words, 0) ?: return null
                i = h.used
                hour = h.value
                // "six thirty", "six oh five"
                if (words.getOrNull(i) == "oh" || words.getOrNull(i) == "o") {
                    val m = parse(words, i + 1)
                    if (m != null && m.value < 10) { minute = m.value; i += 1 + m.used }
                } else {
                    val m = parse(words, i)
                    if (m != null && m.value < 60) { minute = m.value; i += m.used }
                }
                // "630" or "1830" said as one number
                if (hour in 100..2359 && minute == 0) {
                    minute = hour % 100
                    hour /= 100
                }
            }
        }
        if (hour !in 0..23 || minute !in 0..59) return null

        val rest = words.drop(i).joinToString(" ")
        val pm = Regex("\\b(p m|pm|evening|night|afternoon|tonight)\\b").containsMatchIn(rest)
        val am = Regex("\\b(a m|am|morning)\\b").containsMatchIn(rest)
        return when {
            pm && hour in 1..11 -> ClockTime(hour + 12, minute, true)
            pm -> ClockTime(hour, minute, true)
            am && hour == 12 -> ClockTime(0, minute, true)
            am -> ClockTime(hour, minute, true)
            hour > 12 || hour == 0 -> ClockTime(hour, minute, true)
            else -> ClockTime(hour, minute, false)
        }
    }

    /** Picks the next upcoming time for an hour said without am/pm (e.g. "six" at 3 PM -> 18:00). */
    fun resolve(time: ClockTime, nowHour: Int, nowMinute: Int): Pair<Int, Int> {
        if (time.meridiemKnown) return time.hour to time.minute
        val now = nowHour * 60 + nowMinute
        val morning = (time.hour % 12) * 60 + time.minute
        val evening = morning + 12 * 60
        return when {
            morning > now -> (time.hour % 12) to time.minute
            evening > now -> (time.hour % 12 + 12) to time.minute
            else -> (time.hour % 12) to time.minute // tomorrow morning
        }
    }

    /** "ten minutes", "an hour and a half", "one hour thirty minutes", "half an hour", "90 seconds" -> seconds. */
    fun parseDuration(text: String): Int? {
        val words = text.lowercase().split(" ").filter { it.isNotBlank() && it != "and" && it != "for" }
        var total = 0
        var found = false
        var lastUnitSeconds = 3600
        var i = 0
        while (i < words.size) {
            if (words[i] == "half" && words.getOrNull(i + 1) in setOf("an", "a") && words.getOrNull(i + 2)?.startsWith("hour") == true) {
                total += 1800; found = true; i += 3; continue
            }
            if (words[i] == "a" && words.getOrNull(i + 1) == "half" && found) { // "an hour and a half"
                total += lastUnitSeconds / 2; i += 2; continue
            }
            val n = when (words[i]) {
                "a", "an" -> Parsed(1, 1)
                else -> parse(words, i)
            }
            if (n != null) {
                val unit = words.getOrNull(i + n.used)
                val secs = unitSeconds(unit)
                if (secs != null) {
                    total += n.value * secs
                    lastUnitSeconds = secs
                    found = true
                    i += n.used + 1
                    continue
                }
            }
            i++
        }
        return if (found && total > 0) total else null
    }

    private fun unitSeconds(word: String?): Int? = when {
        word == null -> null
        word.startsWith("second") || word == "sec" || word == "secs" -> 1
        word.startsWith("minute") || word == "min" || word == "mins" -> 60
        word.startsWith("hour") -> 3600
        else -> null
    }
}
