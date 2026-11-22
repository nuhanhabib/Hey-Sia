package com.heysia.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.heysia.app.listen.SiaEars
import com.heysia.app.voice.Phrases
import com.heysia.app.voice.SiaVoice

/**
 * Step 3 test screen: tap, say something, and Sia repeats what she heard.
 * Later steps replace the button with the "Hey Sia" wake word.
 */
@Composable
fun ListenTestScreen(
    ears: SiaEars,
    voice: SiaVoice,
    openScreen: (Intent) -> Unit,
    onOpenVoiceSettings: () -> Unit
) {
    val context = LocalContext.current
    val status by ears.status.collectAsState()
    val partial by ears.partial.collectAsState()

    var lastHeard by rememberSaveable { mutableStateOf<String?>(null) }
    var problem by rememberSaveable { mutableStateOf<String?>(null) }
    var micDenied by rememberSaveable { mutableStateOf(false) }

    fun hasMic() = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

    fun startListening() {
        problem = null
        voice.stop() // never listen while she is talking, or she would hear herself
        ears.listenOnce { heard ->
            when (heard) {
                is SiaEars.Heard.Text -> {
                    lastHeard = heard.text
                    voice.speak(Phrases.youSaid(heard.text))
                }
                SiaEars.Heard.Silence -> voice.speak(Phrases.HEARD_NOTHING)
                is SiaEars.Heard.Failed -> problem = heard.message
            }
        }
    }

    val askForMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micDenied = !granted
        if (granted) startListening() else voice.speak(Phrases.MIC_NEEDED)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sia", style = MaterialTheme.typography.displayMedium)
        Text(statusLabel(status), style = MaterialTheme.typography.titleMedium)

        when (status) {
            SiaEars.Status.Listening -> {
                Text(
                    partial.ifEmpty { "..." },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(onClick = ears::cancel, modifier = Modifier.fillMaxWidth()) { Text("Stop") }
            }
            else -> Button(
                onClick = { if (hasMic()) startListening() else askForMic.launch(Manifest.permission.RECORD_AUDIO) },
                enabled = status == SiaEars.Status.Ready,
                modifier = Modifier.fillMaxWidth().height(72.dp)
            ) { Text("Tap and talk", style = MaterialTheme.typography.titleLarge) }
        }

        (status as? SiaEars.Status.Error)?.let { Text(it.message, color = MaterialTheme.colorScheme.error) }
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (micDenied) {
            Text(
                "Sia needs the microphone to hear you. Audio stays on your phone.",
                color = MaterialTheme.colorScheme.error
            )
            OutlinedButton(onClick = {
                openScreen(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }) { Text("Open app permissions") }
        }

        lastHeard?.let {
            Text("Last thing I heard:", style = MaterialTheme.typography.labelLarge)
            Text("\"$it\"", style = MaterialTheme.typography.bodyLarge)
        }

        OutlinedButton(onClick = onOpenVoiceSettings) { Text("Voice settings") }
    }
}

private fun statusLabel(status: SiaEars.Status): String = when (status) {
    SiaEars.Status.Loading -> "Getting my ears ready... (first launch takes a few seconds)"
    SiaEars.Status.Ready -> "Ready. Tap the button and talk."
    SiaEars.Status.Listening -> "Listening..."
    is SiaEars.Status.Error -> "My ears aren't working"
}
