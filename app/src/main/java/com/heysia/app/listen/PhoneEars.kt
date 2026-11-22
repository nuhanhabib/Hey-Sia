package com.heysia.app.listen

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Listens with the phone's own speech recognition (on Samsung phones this is usually Google's).
 * Far more accurate than the small built-in model, and it returns several guesses, so Sia can
 * pick the one that makes sense as a command.
 *
 * With the offline English pack installed, recognition runs on the phone. Call from the main thread.
 */
class PhoneEars(private val context: Context) {

    private var recognizer: SpeechRecognizer? = null

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial.asStateFlow()

    val isAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    /** Listens for one sentence. [SiaEars.Heard.Failed] means "try the other engine". */
    suspend fun listen(languageTag: String): SiaEars.Heard = suspendCancellableCoroutine { cont ->
        stop()
        val rec = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (e: Exception) {
            cont.resume(SiaEars.Heard.Failed("Speech recognition isn't available."))
            return@suspendCancellableCoroutine
        }
        recognizer = rec
        _partial.value = ""

        fun finish(result: SiaEars.Heard) {
            if (recognizer === rec) stop()
            if (cont.isActive) cont.resume(result)
        }

        rec.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val guesses = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    .orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
                finish(if (guesses.isEmpty()) SiaEars.Heard.Silence else SiaEars.Heard.Text(guesses.first(), guesses))
            }

            override fun onPartialResults(partialResults: Bundle?) {
                partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                    ?.let { _partial.value = it }
            }

            override fun onError(error: Int) = finish(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SiaEars.Heard.Silence
                    else -> SiaEars.Heard.Failed(errorText(error))
                }
            )

            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, MAX_GUESSES)
            .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        rec.startListening(intent)
        cont.invokeOnCancellation {
            android.os.Handler(android.os.Looper.getMainLooper()).post { if (recognizer === rec) stop() }
        }
    }

    fun stop() {
        recognizer?.let {
            it.cancel()
            it.destroy()
        }
        recognizer = null
        _partial.value = ""
    }

    private fun errorText(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER ->
            "The offline English speech pack isn't installed."
        SpeechRecognizer.ERROR_AUDIO -> "Couldn't use the microphone."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is missing."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognition is busy."
        else -> "Speech recognition error $error."
    }

    private companion object {
        const val MAX_GUESSES = 5
    }
}
