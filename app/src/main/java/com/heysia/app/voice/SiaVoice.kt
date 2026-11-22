package com.heysia.app.voice

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Sia's voice: a small, thread-safe wrapper around Android's built-in TextToSpeech.
 *
 * - Offline only: just the installed voices that don't need the network are offered.
 * - Anything said before the engine is ready is queued and spoken once it is.
 * - Other audio (music) is ducked while she talks; she stops if a call takes the speaker.
 * - [status] lets later steps pause the microphone while she speaks, so she never hears herself.
 * - Her name is always pronounced "See-a", whichever engine or voice is used.
 */
class SiaVoice(context: Context) {

    sealed interface Status {
        data object Starting : Status
        data object Ready : Status
        data object Speaking : Status
        data class Error(val message: String, val missingVoiceData: Boolean = false) : Status
    }

    private class Utterance(val id: String, val text: String)

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private val nextId = AtomicLong()

    // All guarded by [lock].
    private val pending = ArrayDeque<Utterance>()               // asked for before the engine was ready
    private val active = LinkedHashSet<String>()                // utterance ids handed to the engine
    private val waiters = HashMap<String, (Boolean) -> Unit>()  // speakAndWait() callers
    private var shutDown = false

    private val _status = MutableStateFlow<Status>(Status.Starting)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _voices = MutableStateFlow<List<Voice>>(emptyList())
    /** Installed offline English voices, best match first. */
    val voices: StateFlow<List<Voice>> = _voices.asStateFlow()

    private val _currentVoice = MutableStateFlow<Voice?>(null)
    val currentVoice: StateFlow<Voice?> = _currentVoice.asStateFlow()

    val isSpeaking: Boolean get() = _status.value == Status.Speaking

    /** 1.0 is normal speed. Saved across app restarts. */
    var speechRate: Float = prefs.getFloat(KEY_RATE, 1.0f)
        set(value) {
            field = value.coerceIn(MIN_SETTING, MAX_SETTING)
            prefs.edit { putFloat(KEY_RATE, field) }
            tts.setSpeechRate(field)
        }

    /** 1.0 is the voice's natural pitch. Saved across app restarts. */
    var pitch: Float = prefs.getFloat(KEY_PITCH, 1.0f)
        set(value) {
            field = value.coerceIn(MIN_SETTING, MAX_SETTING)
            prefs.edit { putFloat(KEY_PITCH, field) }
            tts.setPitch(field)
        }

    private val speechAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANT)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(speechAttributes)
        .setOnAudioFocusChangeListener { change ->
            // A phone call (or similar) took the speaker away: stop talking.
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stop()
        }
        .build()

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = ended(utteranceId, ok = true)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = ended(utteranceId, ok = false)
        override fun onError(utteranceId: String?, errorCode: Int) = ended(utteranceId, ok = false)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = ended(utteranceId, ok = false)

        private fun ended(utteranceId: String?, ok: Boolean) {
            utteranceId ?: return
            synchronized(lock) { endLocked(utteranceId, ok) }
        }
    }

    // Must stay the last property: if no engine can be bound at all, TextToSpeech calls
    // onInit() synchronously from its constructor, and everything above must exist by then.
    private val tts: TextToSpeech = TextToSpeech(appContext) { onInit(it) }

    /** Say [text]. With [interrupt] she drops whatever she was saying first. Never blocks. */
    fun speak(text: String, interrupt: Boolean = false) {
        enqueue(text, interrupt, onDone = null)
    }

    /** Say [text] and suspend until she has finished. Returns false if it was cut off or failed. */
    suspend fun speakAndWait(text: String, interrupt: Boolean = false): Boolean =
        suspendCancellableCoroutine { cont ->
            val id = enqueue(text, interrupt) { ok -> if (cont.isActive) cont.resume(ok) }
            cont.invokeOnCancellation { synchronized(lock) { waiters.remove(id) } }
        }

    /** Stop talking immediately and forget anything queued. */
    fun stop() {
        synchronized(lock) {
            if (shutDown) return
            if (_status.value == Status.Speaking) tts.stop()
            clearAllLocked()
            idleLocked()
        }
    }

    fun selectVoice(voice: Voice) {
        synchronized(lock) {
            if (shutDown || _status.value == Status.Starting || _status.value is Status.Error) return
            if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                prefs.edit { putString(KEY_VOICE, voice.name) }
                _currentVoice.value = voice
            }
        }
    }

    /** Move to the next (+1) or previous (-1) installed voice. */
    fun cycleVoice(step: Int) {
        val list = _voices.value
        if (list.isEmpty()) return
        val index = list.indexOfFirst { it.name == _currentVoice.value?.name }
        selectVoice(list[if (index < 0) 0 else Math.floorMod(index + step, list.size)])
    }

    /** Release the engine. The object can't be used afterwards. */
    fun shutdown() {
        synchronized(lock) {
            if (shutDown) return
            shutDown = true
            clearAllLocked()
            audioManager.abandonAudioFocusRequest(focusRequest)
            _status.value = Status.Error("Voice is switched off.")
        }
        tts.shutdown()
    }

    private fun onInit(initStatus: Int) {
        if (initStatus != TextToSpeech.SUCCESS) {
            // Don't touch `tts` here: on this path it may not be assigned yet.
            fail(Status.Error("No text-to-speech engine found. Install Speech Services by Google or Samsung TTS."))
            return
        }
        synchronized(lock) {
            if (shutDown) return
            tts.setAudioAttributes(speechAttributes)
            tts.setOnUtteranceProgressListener(progressListener)
            tts.setSpeechRate(speechRate)
            tts.setPitch(pitch)

            val locale = pickLocale()
            if (locale == null || tts.setLanguage(locale) < TextToSpeech.LANG_AVAILABLE) {
                failLocked(Status.Error("English voice data isn't installed on this phone.", missingVoiceData = true))
                return
            }
            loadVoices(locale)
            _status.value = Status.Ready
            while (pending.isNotEmpty()) speakLocked(pending.removeFirst(), TextToSpeech.QUEUE_ADD)
        }
    }

    /** The phone's own English variant if it uses one, otherwise the first English the engine has. */
    private fun pickLocale(): Locale? {
        val device = Locale.getDefault()
        val candidates = buildList {
            if (device.language == "en") add(device)
            add(Locale.US)
            add(Locale.UK)
            add(Locale.forLanguageTag("en-IN"))
        }
        return candidates.firstOrNull { tts.isLanguageAvailable(it) >= TextToSpeech.LANG_AVAILABLE }
    }

    private fun loadVoices(locale: Locale) {
        val offline = tts.voices.orEmpty()
            .filter { voice ->
                voice.locale.language == "en" &&
                    !voice.isNetworkConnectionRequired &&
                    TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty()
            }
            .sortedWith(
                compareByDescending<Voice> { it.locale == locale }
                    .thenByDescending { it.quality }
                    .thenBy { it.name }
            )
        _voices.value = offline

        val saved = prefs.getString(KEY_VOICE, null)?.let { name -> offline.firstOrNull { it.name == name } }
        if (saved != null) tts.setVoice(saved)
        _currentVoice.value = tts.voice
    }

    private fun enqueue(text: String, interrupt: Boolean, onDone: ((Boolean) -> Unit)?): String {
        val id = "sia-${nextId.incrementAndGet()}"
        val clean = text.trim().replace(NAME_PATTERN, SPOKEN_NAME)
        synchronized(lock) {
            if (clean.isEmpty() || shutDown || _status.value is Status.Error) {
                onDone?.let { callback -> mainHandler.post { callback(clean.isEmpty()) } }
                return id
            }
            if (onDone != null) waiters[id] = onDone
            if (interrupt) clearAllLocked() // QUEUE_FLUSH below silences the engine itself

            val utterance = Utterance(id, clean)
            if (_status.value == Status.Starting) {
                pending.addLast(utterance)
            } else {
                speakLocked(utterance, if (interrupt) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD)
            }
        }
        return id
    }

    private fun speakLocked(utterance: Utterance, queueMode: Int) {
        val chunks = splitForEngine(utterance.text)
        if (active.isEmpty()) audioManager.requestAudioFocus(focusRequest)
        _status.value = Status.Speaking
        chunks.forEachIndexed { i, chunk ->
            // Only the last chunk carries the real id, so waiters fire when she is fully done.
            val chunkId = if (i == chunks.lastIndex) utterance.id else "${utterance.id}#$i"
            active += chunkId
            val mode = if (i == 0) queueMode else TextToSpeech.QUEUE_ADD
            if (tts.speak(chunk, mode, null, chunkId) != TextToSpeech.SUCCESS) endLocked(chunkId, ok = false)
        }
    }

    private fun endLocked(id: String, ok: Boolean) {
        if (!active.remove(id)) return // already cleared by stop() or an interrupt
        finishLocked(id, ok)
        if (active.isEmpty()) idleLocked()
    }

    private fun idleLocked() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (_status.value == Status.Speaking) _status.value = Status.Ready
    }

    /** Forget every queued and playing utterance, telling their waiters they were cut off. */
    private fun clearAllLocked() {
        pending.forEach { finishLocked(it.id, ok = false) }
        pending.clear()
        active.forEach { finishLocked(it, ok = false) }
        active.clear()
    }

    // Callbacks run on the main thread, never while holding the lock.
    private fun finishLocked(id: String, ok: Boolean) {
        waiters.remove(id)?.let { callback -> mainHandler.post { callback(ok) } }
    }

    private fun fail(error: Status.Error) = synchronized(lock) { failLocked(error) }

    private fun failLocked(error: Status.Error) {
        clearAllLocked()
        _status.value = error
    }

    /** Engines reject text over a size limit, so long text is split at sentence or word breaks. */
    private fun splitForEngine(text: String): List<String> {
        val max = TextToSpeech.getMaxSpeechInputLength() - 1
        if (text.length <= max) return listOf(text)
        val chunks = mutableListOf<String>()
        var rest = text
        while (rest.length > max) {
            val window = rest.substring(0, max)
            var cut = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n')) + 1
            if (cut <= max / 2) cut = window.lastIndexOf(' ').takeIf { it > 0 } ?: max
            chunks += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trimStart()
        }
        if (rest.isNotBlank()) chunks += rest
        return chunks.filter { it.isNotEmpty() }
    }

    companion object {
        private const val PREFS_NAME = "sia_voice"
        private const val KEY_RATE = "speech_rate"
        private const val KEY_PITCH = "pitch"
        private const val KEY_VOICE = "voice_name"

        // Written "Sia", spoken "See-a". Some engines would otherwise say "sigh-a" or spell it out.
        // If your chosen voice still says it oddly, try "Seeah" or "See uh" here.
        private val NAME_PATTERN = Regex("\\bSia\\b", RegexOption.IGNORE_CASE)
        private const val SPOKEN_NAME = "See-uh"
        const val MIN_SETTING = 0.5f
        const val MAX_SETTING = 2.0f

        /** Opens the engine's screen for downloading offline voice data. */
        fun installVoiceDataIntent(): Intent =
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        /** Opens Android's text-to-speech settings (pick engine, download voices). */
        fun ttsSettingsIntent(): Intent =
            Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
