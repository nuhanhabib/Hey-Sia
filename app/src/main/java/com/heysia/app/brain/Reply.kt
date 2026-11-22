package com.heysia.app.brain

/**
 * What Sia says back after a command.
 *
 * @param speech what she says out loud.
 * @param followUp if set, she listens again right after speaking and hands your answer to this
 *   function. Used for questions like "Should I send it?" or "Who should I call?".
 */
data class Reply(
    val speech: String,
    val followUp: (suspend (String) -> Reply)? = null
)
