package com.heysia.app.brain

/** Understands short answers to Sia's questions. */
object Answers {
    private val yes = setOf("yes", "yeah", "yep", "yup", "sure", "ok", "okay", "correct", "right", "do it", "go ahead", "send", "send it", "call", "please", "of course", "confirm")
    private val no = setOf("no", "nope", "nah", "don't", "dont", "cancel", "stop", "wrong", "never mind", "not")

    fun isYes(text: String): Boolean {
        val t = CommandParser.normalize(text)
        return !isNo(t) && (t in yes || t.split(" ").any { it in yes })
    }

    fun isNo(text: String): Boolean {
        val t = text.lowercase()
        return t.split(" ").any { it in no } || t.contains("never mind")
    }
}
