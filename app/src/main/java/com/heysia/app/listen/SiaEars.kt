package com.heysia.app.listen

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import java.io.IOException
import kotlin.coroutines.resume

/**
 * Sia's ears: offline speech-to-text with Vosk.
 *
 * - 100% on the phone. Audio never leaves the device.
 * - Two modes: [waitForWakeWord] listens only for "Hey Sia" (cheap and strict),
 *   [listen] understands any English sentence (used right after the wake word).
 * - Only one session at a time. The microphone is closed between sessions, so Sia never
 *   hears herself while she talks.
 *
 * Call from the main thread.
 */
class SiaEars {

    sealed interface Heard {
        /** [alternatives]: other guesses of what you said, best first (includes [text]). */
        data class Text(val text: String, val alternatives: List<String> = listOf(text)) : Heard
        data object Silence : Heard
        data class Failed(val message: String) : Heard
    }

    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var onHeard: ((Heard) -> Unit)? = null
    private var accept: (String) -> Boolean = { true }
    private var acceptPartial = false
    private var sessionId = 0

    private val _partial = MutableStateFlow("")
    /** Words recognised so far in the current sentence, updated live. */
    val partial: StateFlow<String> = _partial.asStateFlow()

    val isListening: Boolean get() = onHeard != null

    /** Listen for one full sentence. Returns when you pause, or [Heard.Silence] after [timeoutMs]. */
    suspend fun listen(timeoutMs: Int = COMMAND_TIMEOUT_MS): Heard =
        session(grammar = null, timeoutMs = timeoutMs, accept = { it !in NOISE_WORDS }, partialCounts = false)

    /**
     * Wait (forever, until cancelled) until someone says "Hey Sia".
     * Reacts as soon as the words are heard, without waiting for you to pause.
     */
    suspend fun waitForWakeWord(): Heard =
        session(grammar = WAKE_GRAMMAR, timeoutMs = NO_TIMEOUT, accept = ::isWakePhrase, partialCounts = true)

    /** Stop the current session; it reports [Heard.Silence]. */
    fun cancel() = finish(Heard.Silence)

    private suspend fun session(grammar: String?, timeoutMs: Int, accept: (String) -> Boolean, partialCounts: Boolean): Heard =
        suspendCancellableCoroutine { cont ->
            acceptPartial = partialCounts
            val id = start(grammar, timeoutMs, accept) { heard -> if (cont.isActive) cont.resume(heard) }
            cont.invokeOnCancellation { finishOnMain(id) }
        }

    /** Opens the microphone and returns this session's id. */
    private fun start(grammar: String?, timeoutMs: Int, accept: (String) -> Boolean, onResult: (Heard) -> Unit): Int {
        cancel() // never two microphone sessions at once
        val id = ++sessionId
        val model = SpeechModel.model
        if (model == null) {
            onResult(Heard.Failed("My speech model isn't loaded yet."))
            return id
        }
        val rec = try {
            if (grammar == null) Recognizer(model, SAMPLE_RATE) else Recognizer(model, SAMPLE_RATE, grammar)
        } catch (e: IOException) {
            onResult(Heard.Failed("Couldn't start listening: ${e.message}"))
            return id
        }
        val svc = try {
            SpeechService(rec, SAMPLE_RATE)
        } catch (e: IOException) {
            rec.close()
            onResult(Heard.Failed("I can't open the microphone. Is another app using it?"))
            return id
        }
        recognizer = rec
        service = svc
        this.accept = accept
        onHeard = onResult
        _partial.value = ""
        svc.startListening(listener, timeoutMs)
        return id
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (onHeard == null) return
            val text = field(hypothesis, "partial").replace(UNK, "").trim()
            _partial.value = text
            // Early reaction only for the full phrase ("hey sia"); a lone "sia" waits for the pause.
            if (acceptPartial && isFullWakePhrase(text)) finish(Heard.Text(text))
        }

        // Called each time you pause. Empty or rejected results are ignored and listening continues.
        override fun onResult(hypothesis: String?) {
            val text = field(hypothesis, "text").replace(UNK, "").trim()
            if (text.isNotEmpty() && accept(text)) finish(Heard.Text(text))
        }

        override fun onFinalResult(hypothesis: String?) = Unit // only after stop(); already reported

        override fun onError(exception: Exception?) =
            finish(Heard.Failed("Listening failed: ${exception?.message ?: "unknown error"}"))

        override fun onTimeout() = finish(Heard.Silence)
    }

    /** Report exactly once per session, then close the microphone. */
    private fun finish(result: Heard) {
        val callback = onHeard ?: return
        onHeard = null
        closeSession()
        _partial.value = ""
        callback(result)
    }

    // Coroutine cancellation can arrive on any thread; the Vosk objects belong to the main thread.
    // The id check stops a late cancel from closing a newer session.
    private fun finishOnMain(id: Int) {
        android.os.Handler(android.os.Looper.getMainLooper()).post { if (id == sessionId) finish(Heard.Silence) }
    }

    private fun closeSession() {
        service?.let {
            it.stop()
            it.shutdown()
        }
        service = null
        recognizer?.close()
        recognizer = null
    }

    private fun field(json: String?, key: String): String =
        try {
            json?.let { JSONObject(it).optString(key) }.orEmpty().trim()
        } catch (e: JSONException) {
            ""
        }

    companion object {
        private const val SAMPLE_RATE = 16000f
        private const val NO_TIMEOUT = -1
        const val COMMAND_TIMEOUT_MS = 7000
        private const val UNK = "[unk]"

        /**
         * Restricting the recogniser to these phrases makes wake-word detection strict and light.
         * "[unk]" soaks up every other sound, so normal conversation doesn't wake her.
         */
        private val WAKE_PHRASES = listOf("hey sia", "hi sia", "hey see a", "okay sia", "sia")
        private val WAKE_GRAMMAR = JSONArray(WAKE_PHRASES + UNK).toString()

        /** The small model sometimes hears a lone word like "the" or "huh" in background noise. */
        private val NOISE_WORDS = setOf("the", "huh", "a", "uh", "um", "hmm", "oh", "ah")

        fun isWakePhrase(text: String): Boolean = WAKE_PHRASES.any { text.contains(it) }

        private fun isFullWakePhrase(text: String): Boolean = WAKE_PHRASES.any { it.contains(' ') && text.contains(it) }
    }
}
