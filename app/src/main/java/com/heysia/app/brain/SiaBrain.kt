package com.heysia.app.brain

import android.content.Context
import com.heysia.app.voice.Phrases

/** Turns what you said into an action and a reply. Step 4: she simply repeats what she heard. */
class SiaBrain(private val context: Context) {
    suspend fun handle(text: String): Reply = Reply(Phrases.youSaid(text))
}
