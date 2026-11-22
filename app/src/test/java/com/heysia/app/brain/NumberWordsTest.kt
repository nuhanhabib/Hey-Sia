package com.heysia.app.brain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NumberWordsTest {

    private fun num(text: String) = NumberWords.parse(text.split(" "))?.value

    @Test fun numbers() {
        assertEquals(7, num("seven"))
        assertEquals(25, num("twenty five"))
        assertEquals(120, num("one hundred and twenty"))
        assertEquals(15, num("fifteen"))
        assertEquals(1500, num("one thousand five hundred"))
        assertNull(num("hello"))
    }

    @Test fun stopsAtTheNextNumber() {
        // "six thirty" is two numbers (hour, minute), not 36
        assertEquals(NumberWords.Parsed(6, 1), NumberWords.parse(listOf("six", "thirty")))
    }

    @Test fun times() {
        assertEquals(NumberWords.ClockTime(6, 30, false), NumberWords.parseTime("six thirty"))
        assertEquals(NumberWords.ClockTime(18, 30, true), NumberWords.parseTime("six thirty pm"))
        assertEquals(NumberWords.ClockTime(6, 5, false), NumberWords.parseTime("six oh five"))
        assertEquals(NumberWords.ClockTime(0, 0, true), NumberWords.parseTime("midnight"))
        assertEquals(NumberWords.ClockTime(12, 0, true), NumberWords.parseTime("twelve p m"))
        assertEquals(NumberWords.ClockTime(0, 15, true), NumberWords.parseTime("twelve fifteen a m"))
        assertEquals(NumberWords.ClockTime(7, 30, false), NumberWords.parseTime("half past seven"))
        assertEquals(NumberWords.ClockTime(21, 0, true), NumberWords.parseTime("nine at night"))
        assertNull(NumberWords.parseTime("banana"))
    }

    @Test fun resolvesAmOrPm() {
        val six = NumberWords.ClockTime(6, 0, false)
        assertEquals(6 to 0, NumberWords.resolve(six, nowHour = 3, nowMinute = 0))   // 3 AM -> 6 AM
        assertEquals(18 to 0, NumberWords.resolve(six, nowHour = 15, nowMinute = 0)) // 3 PM -> 6 PM
        assertEquals(6 to 0, NumberWords.resolve(six, nowHour = 22, nowMinute = 0))  // 10 PM -> 6 AM tomorrow
    }

    @Test fun durations() {
        assertEquals(600, NumberWords.parseDuration("ten minutes"))
        assertEquals(90, NumberWords.parseDuration("ninety seconds"))
        assertEquals(1800, NumberWords.parseDuration("half an hour"))
        assertEquals(5400, NumberWords.parseDuration("an hour and a half"))
        assertEquals(60, NumberWords.parseDuration("a minute"))
        assertNull(NumberWords.parseDuration("soon"))
    }

    @Test fun phoneDigits() {
        assertEquals("017", NumberWords.digits("zero one seven".split(" ")))
        assertEquals("+880", NumberWords.digits("plus eight eight zero".split(" ")))
        assertNull(NumberWords.digits("rafi".split(" ")))
    }
}
