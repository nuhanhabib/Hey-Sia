package com.heysia.app.listen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService
import java.io.IOException

/**
 * Sia's ears: offline speech-to-text with Vosk.
 *
 * - 100% on the phone. Audio never leaves the device and no internet is needed.
 * - The English model ships inside the APK and is unpacked to private app storage on first launch.
 * - The microphone is only open while [listenOnce] is running, never in the background (yet).
 *
 * All public functions must be called on the main thread. Callbacks also arrive on the main thread.
 */
class SiaEars(context: Context) {

    sealed interface Status {
        data object Loading : Status
        data object Ready : Status
        data object Listening : Status
        data class Error(val message: String) : Status
    }

    /** What happened after one round of listening. */
    sealed interface Heard {
        data class Text(val text: String) : Heard
        data object Silence : Heard
        data class Failed(val message: String) : Heard
    }

    private val appContext = context.applicationContext
    private var model: Model? = null
    private var recognizer: Recognizer? = null
    private var service: SpeechService? = null
    private var onHeard: ((Heard) -> Unit)? = null
    private var released = false

    private val _status = MutableStateFlow<Status>(Status.Loading)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _partial = MutableStateFlow("")
    /** Words recognised so far in the current round, updated live while you speak. */
    val partial: StateFlow<String> = _partial.asStateFlow()

    init {
        // Copies the model out of the APK once (a few seconds), then reuses it on later launches.
        StorageService.unpack(
            appContext, ASSET_MODEL, STORAGE_MODEL,
            { loaded ->
                if (released) {
                    loaded.close()
                } else {
                    model = loaded
                    _status.value = Status.Ready
                }
            },
            { e: IOException ->
                if (!released) _status.value = Status.Error("Couldn't load my speech model: ${e.message}")
            }
        )
    }

    /**
     * Listen until you finish one sentence (a short pause ends it), then report once through [onResult].
     * Needs the RECORD_AUDIO permission. Does nothing unless [status] is Ready.
     */
    fun listenOnce(timeoutMs: Int = DEFAULT_TIMEOUT_MS, onResult: (Heard) -> Unit) {
        val loaded = model ?: return
        if (_status.value != Status.Ready) return

        val rec = Recognizer(loaded, SAMPLE_RATE)
        val svc = try {
            SpeechService(rec, SAMPLE_RATE)
        } catch (e: IOException) {
            rec.close()
            onResult(Heard.Failed("I can't open the microphone. Is another app using it?"))
            return
        }
        recognizer = rec
        service = svc
        onHeard = onResult
        _partial.value = ""
        _status.value = Status.Listening
        svc.startListening(listener, timeoutMs)
    }

    /** Stop listening now. The pending [listenOnce] callback is told it heard nothing. */
    fun cancel() = finish(Heard.Silence)

    /** Free the microphone and the model. The object can't be used afterwards. */
    fun release() {
        if (released) return
        released = true
        onHeard = null
        closeSession()
        model?.close()
        model = null
        _status.value = Status.Error("Listening is switched off.")
    }

    private val listener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String?) {
            if (onHeard != null) _partial.value = field(hypothesis, "partial")
        }

        // Vosk calls this each time you pause. Empty results (just silence or noise) are ignored.
        override fun onResult(hypothesis: String?) {
            val text = field(hypothesis, "text")
            if (text.isNotEmpty() && !isNoise(text)) finish(Heard.Text(text))
        }

        // Only arrives after stop(); by then finish() has already reported, so this is ignored.
        override fun onFinalResult(hypothesis: String?) = Unit

        override fun onError(exception: Exception?) =
            finish(Heard.Failed("Listening failed: ${exception?.message ?: "unknown error"}"))

        override fun onTimeout() = finish(Heard.Silence)
    }

    /** Report exactly once per round, then close the microphone. */
    private fun finish(result: Heard) {
        val callback = onHeard ?: return
        onHeard = null
        closeSession()
        _partial.value = ""
        if (!released) _status.value = Status.Ready
        callback(result)
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
        } catch (e: org.json.JSONException) {
            ""
        }

    // The small model sometimes hears a lone "the" or "huh" in background noise.
    private fun isNoise(text: String) = text in NOISE_WORDS

    companion object {
        private const val ASSET_MODEL = "model-en-us" // folder packed into the APK by Gradle
        private const val STORAGE_MODEL = "model"     // unpacked copy in private app storage
        private const val SAMPLE_RATE = 16000f
        const val DEFAULT_TIMEOUT_MS = 8000
        private val NOISE_WORDS = setOf("the", "huh", "a", "uh", "um", "hmm")
    }
}
