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

    fun youSaid(text: String) = "You said: $text"

    /** Lines offered on the test screen. */
    val samples = listOf(GREETING, OFFLINE, NOT_UNDERSTOOD)
}
