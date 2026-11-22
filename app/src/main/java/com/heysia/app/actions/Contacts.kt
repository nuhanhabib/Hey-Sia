package com.heysia.app.actions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.core.content.ContextCompat
import com.heysia.app.brain.Fuzzy

/**
 * Looks up people in your contacts by name. Reads only names and phone numbers, nothing else,
 * and keeps nothing: every search reads fresh and forgets afterwards.
 */
class Contacts(private val context: Context) {

    data class Person(val name: String, val number: String)

    data class Match(val person: Person, val score: Double)

    val allowed: Boolean
        get() = ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Best matches first. One entry per person (their main or first mobile number). */
    fun search(spoken: String): List<Match> {
        if (!allowed) return emptyList()
        return Fuzzy.ranked(spoken, people()) { listOf(it.name) }
            .filter { it.second >= MIN_SCORE }
            .take(3)
            .map { Match(it.first, it.second) }
    }

    private fun people(): List<Person> {
        val byName = LinkedHashMap<String, Pair<Person, Int>>() // name -> (person, priority)
        context.contentResolver.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.IS_SUPER_PRIMARY, Phone.TYPE),
            null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                // Prefer the default number, then mobile, then anything else.
                val priority = when {
                    c.getInt(2) == 1 -> 0
                    c.getInt(3) == Phone.TYPE_MOBILE -> 1
                    else -> 2
                }
                val existing = byName[name]
                if (existing == null || priority < existing.second) byName[name] = Person(name, number) to priority
            }
        }
        return byName.values.map { it.first }
    }

    private companion object {
        const val MIN_SCORE = 0.6
    }
}
