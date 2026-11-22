package com.heysia.app.listen

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.vosk.Model
import org.vosk.android.StorageService
import java.io.IOException

/**
 * The offline English speech model, loaded once and shared by everything that listens.
 * It ships inside the APK and is unpacked to private app storage on first launch (a few seconds).
 */
object SpeechModel {

    sealed interface State {
        data object NotLoaded : State
        data object Loading : State
        data class Ready(val model: Model) : State
        data class Failed(val message: String) : State
    }

    private const val ASSET_MODEL = "model-en-us" // folder packed into the APK by Gradle
    private const val STORAGE_MODEL = "model"     // unpacked copy in private app storage

    private val _state = MutableStateFlow<State>(State.NotLoaded)
    val state: StateFlow<State> = _state.asStateFlow()

    val model: Model? get() = (_state.value as? State.Ready)?.model

    /** Start loading if needed. Safe to call many times; must be called on the main thread. */
    fun load(context: Context) {
        val current = _state.value
        if (current is State.Loading || current is State.Ready) return
        _state.value = State.Loading
        StorageService.unpack(
            context.applicationContext, ASSET_MODEL, STORAGE_MODEL,
            { loaded -> _state.value = State.Ready(loaded) },
            { e: IOException -> _state.value = State.Failed("Couldn't load my speech model: ${e.message}") }
        )
    }
}
