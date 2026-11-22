package com.heysia.app.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.heysia.app.listen.ListenSettings

/** Choose how Sia understands your commands, and which English accent to expect. */
@Composable
fun ListeningScreen(openScreen: (Intent) -> Unit) {
    val context = LocalContext.current
    val settings = remember { ListenSettings(context) }
    var engine by remember { mutableStateOf(settings.engine) }
    var accent by remember { mutableStateOf(settings.accent) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Listening", style = MaterialTheme.typography.headlineMedium)
        Text("\"Hey Sia\" is always detected on the phone. This chooses how your command is understood.")

        ListenSettings.Engine.entries.forEach { option ->
            Row(
                Modifier.fillMaxWidth().clickable { engine = option; settings.engine = option },
                verticalAlignment = Alignment.Top
            ) {
                RadioButton(selected = engine == option, onClick = { engine = option; settings.engine = option })
                Column(Modifier.padding(top = 12.dp)) {
                    Text(option.title, fontWeight = FontWeight.Bold)
                    Text(option.description, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (engine == ListenSettings.Engine.Phone) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Make it work offline (one time, ~50 MB)", fontWeight = FontWeight.Bold)
                    Text(
                        "1. Open the Google app → tap your profile picture → Settings → Voice.\n" +
                            "2. Tap \"Offline speech recognition\" and download English.\n" +
                            "With it installed, your speech is recognised on the phone, even without internet. " +
                            "Without it, the phone may use the internet to recognise speech when you're online.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = {
                        openScreen(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }) { Text("Open voice input settings") }
                }
            }

            Text("Accent", style = MaterialTheme.typography.titleMedium)
            Text("If Sia often mishears you, try the accent closest to yours.", style = MaterialTheme.typography.bodySmall)
            ListenSettings.Accent.entries.forEach { option ->
                Row(
                    Modifier.fillMaxWidth().clickable { accent = option; settings.accent = option },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(selected = accent == option, onClick = { accent = option; settings.accent = option })
                    Text(option.title)
                }
            }
        }
    }
}
