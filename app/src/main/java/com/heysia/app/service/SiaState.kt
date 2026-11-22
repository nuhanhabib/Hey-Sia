package com.heysia.app.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Live state of the background service, shown on the home screen. Nothing here is saved to disk. */
object SiaState {

    enum class Phase(val label: String) {
        Off("Sia is off"),
        Starting("Waking up..."),
        WaitingForWakeWord("Say \"Hey Sia\""),
        Listening("Listening..."),
        Thinking("Thinking..."),
        Speaking("Speaking..."),
        Error("Something went wrong")
    }

    data class Line(val fromUser: Boolean, val text: String)

    private val _phase = MutableStateFlow(Phase.Off)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _partial = MutableStateFlow("")
    /** Words heard so far while she is listening to a command. */
    val partial: StateFlow<String> = _partial.asStateFlow()

    private val _conversation = MutableStateFlow<List<Line>>(emptyList())
    /** The last few exchanges, newest last. Kept in memory only. */
    val conversation: StateFlow<List<Line>> = _conversation.asStateFlow()

    internal fun setPhase(phase: Phase, error: String? = null) {
        _phase.value = phase
        _error.value = error
    }

    internal fun setPartial(text: String) {
        _partial.value = text
    }

    internal fun add(fromUser: Boolean, text: String) {
        _conversation.update { (it + Line(fromUser, text)).takeLast(MAX_LINES) }
    }

    private const val MAX_LINES = 20
}
