package com.heysia.app.ui

import android.content.Intent
import android.speech.tts.Voice
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.heysia.app.voice.SiaVoice
import com.heysia.app.voice.Phrases
import kotlinx.coroutines.launch

/** Temporary screen for testing Sia's voice. Later steps replace it with the real home screen. */
@Composable
fun VoiceTestScreen(voice: SiaVoice, openScreen: (Intent) -> Unit, onRetry: () -> Unit) {
    val status by voice.status.collectAsState()
    val voices by voice.voices.collectAsState()
    val currentVoice by voice.currentVoice.collectAsState()
    val scope = rememberCoroutineScope()

    var text by rememberSaveable { mutableStateOf("") }
    var rate by remember { mutableFloatStateOf(voice.speechRate) }
    var pitch by remember { mutableFloatStateOf(voice.pitch) }
    var waitResult by remember { mutableStateOf<String?>(null) }
    val ready = status == SiaVoice.Status.Ready || status == SiaVoice.Status.Speaking

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sia", style = MaterialTheme.typography.displayMedium)
        Text(statusLabel(status), style = MaterialTheme.typography.titleMedium)

        val error = status as? SiaVoice.Status.Error
        if (error != null) {
            Text(error.message, color = MaterialTheme.colorScheme.error)
            if (error.missingVoiceData) {
                Button(onClick = { openScreen(SiaVoice.installVoiceDataIntent()) }) { Text("Install voice data") }
            }
            OutlinedButton(onClick = onRetry) { Text("Retry") }
        }

        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Type something for Sia to say") },
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { voice.speak(text, interrupt = true) }, enabled = ready && text.isNotBlank()) {
                Text("Speak")
            }
            OutlinedButton(onClick = voice::stop) { Text("Stop") }
        }

        HorizontalDivider()
        Text("Try a phrase", style = MaterialTheme.typography.titleMedium)
        Phrases.samples.forEach { phrase ->
            OutlinedButton(
                onClick = { voice.speak(phrase, interrupt = true) },
                enabled = ready,
                modifier = Modifier.fillMaxWidth()
            ) { Text(phrase) }
        }
        OutlinedButton(
            onClick = {
                scope.launch {
                    waitResult = "Speaking..."
                    val finished = voice.speakAndWait("One. Two. Three. Done counting.", interrupt = true)
                    waitResult = if (finished) "Finished speaking" else "Cut off before the end"
                }
            },
            enabled = ready,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Test: count, then report when finished") }
        waitResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        HorizontalDivider()
        Text("Voice", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { voice.cycleVoice(-1); voice.speak(Phrases.VOICE_SAMPLE, interrupt = true) },
                enabled = ready && voices.size > 1
            ) { Text("<") }
            Text(voiceLabel(currentVoice, voices), modifier = Modifier.weight(1f))
            OutlinedButton(
                onClick = { voice.cycleVoice(+1); voice.speak(Phrases.VOICE_SAMPLE, interrupt = true) },
                enabled = ready && voices.size > 1
            ) { Text(">") }
        }

        Text("Speed: %.1fx".format(rate))
        Slider(
            value = rate,
            onValueChange = { rate = it },
            onValueChangeFinished = {
                voice.speechRate = rate
                voice.speak("This is my new speed.", interrupt = true)
            },
            valueRange = SiaVoice.MIN_SETTING..SiaVoice.MAX_SETTING,
            steps = 14
        )
        Text("Pitch: %.1f".format(pitch))
        Slider(
            value = pitch,
            onValueChange = { pitch = it },
            onValueChangeFinished = {
                voice.pitch = pitch
                voice.speak("This is my new pitch.", interrupt = true)
            },
            valueRange = SiaVoice.MIN_SETTING..SiaVoice.MAX_SETTING,
            steps = 14
        )

        OutlinedButton(onClick = { openScreen(SiaVoice.ttsSettingsIntent()) }) {
            Text("Phone's text-to-speech settings")
        }
    }
}

private fun statusLabel(status: SiaVoice.Status): String = when (status) {
    SiaVoice.Status.Starting -> "Waking up my voice..."
    SiaVoice.Status.Ready -> "Ready to talk"
    SiaVoice.Status.Speaking -> "Speaking..."
    is SiaVoice.Status.Error -> "My voice isn't working"
}

private fun voiceLabel(current: Voice?, all: List<Voice>): String {
    if (current == null) return "Engine default voice"
    val position = all.indexOfFirst { it.name == current.name }
    val counter = if (position >= 0) " (${position + 1} of ${all.size})" else ""
    return "${current.name}\n${current.locale.displayName}, ${qualityLabel(current.quality)}$counter"
}

private fun qualityLabel(quality: Int): String = when {
    quality >= Voice.QUALITY_VERY_HIGH -> "very high quality"
    quality >= Voice.QUALITY_HIGH -> "high quality"
    quality >= Voice.QUALITY_NORMAL -> "normal quality"
    else -> "low quality"
}
