package com.heysia.app.voice

/** Everything Sia says in more than one place lives here, so her wording stays consistent. */
object Phrases {
    const val GREETING = "Hi, I'm Sia. I can talk now."
    const val OFFLINE = "I'm offline, say 'turn on data' first."
    const val NOT_UNDERSTOOD = "Sorry, I didn't catch that."
    const val VOICE_SAMPLE = "Hi, this is how I sound."
    const val HEARD_NOTHING = "I didn't hear anything."
    const val MIC_NEEDED = "I need the microphone to hear you."
    const val SOMETHING_WRONG = "Sorry, something went wrong."
    const val TRY_HELP = "Say \"what can you do\" to hear examples."
    const val NEED_OPEN_PERMISSION = "I need permission to open apps. Please open Sia and allow \"Open apps\"."
    const val NEED_CONTACTS = "I need access to your contacts to call people by name. Please open Sia and allow it."

    fun youSaid(text: String) = "You said: $text"

    /** Lines offered on the test screen. */
    val samples = listOf(GREETING, OFFLINE, NOT_UNDERSTOOD)
}
