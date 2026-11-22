package com.heysia.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.heysia.app.service.SiaService
import com.heysia.app.service.SiaState

/** Sia's main screen: on/off switch, what she's doing, setup checklist and recent conversation. */
@Composable
fun HomeScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    openScreen: (Intent) -> Unit,
    extraButtons: @Composable () -> Unit = {}
) {
    val context = LocalContext.current
    val phase by SiaState.phase.collectAsState()
    val error by SiaState.error.collectAsState()
    val partial by SiaState.partial.collectAsState()
    val conversation by SiaState.conversation.collectAsState()

    // Re-check permissions whenever you come back from Settings.
    var refresh by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        refresh++
        onPauseOrDispose { }
    }
    val items = remember(refresh) { Setup.items() }
    val missing = remember(refresh) { items.filterNot { it.isDone(context) } }
    val readyToStart = missing.none { it.required }

    var deniedOnce by rememberSaveable { mutableStateOf(setOf<String>()) }
    var asking by remember { mutableStateOf<SetupItem?>(null) }
    val askPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val item = asking
        if (item != null && result.values.any { !it }) deniedOnce = deniedOnce + item.title
        refresh++
    }

    fun fix(item: SetupItem) {
        when {
            item.title in deniedOnce -> openScreen(Setup.appDetails(context))
            item.permissions.isNotEmpty() -> {
                asking = item
                askPermissions.launch(item.permissions.toTypedArray())
            }
            else -> item.settingsScreen?.let { openScreen(it(context)) }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Sia", style = MaterialTheme.typography.displayMedium)
        Text(phase.label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (phase == SiaState.Phase.Listening && partial.isNotEmpty()) {
            Text("\"$partial\"", style = MaterialTheme.typography.headlineSmall)
        }

        if (enabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { SiaService.talkNow(context) },
                    modifier = Modifier.weight(1f).height(64.dp)
                ) { Text("Talk now", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = { onEnabledChange(false) },
                    modifier = Modifier.weight(1f).height(64.dp)
                ) { Text("Turn off", style = MaterialTheme.typography.titleMedium) }
            }
        } else {
            Button(
                onClick = { onEnabledChange(true) },
                enabled = readyToStart,
                modifier = Modifier.fillMaxWidth().height(64.dp)
            ) { Text("Turn on Sia", style = MaterialTheme.typography.titleLarge) }
            if (!readyToStart) Text("Allow the required items below first.")
        }

        if (missing.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Setup (${items.size - missing.size} of ${items.size} done)", style = MaterialTheme.typography.titleMedium)
                    missing.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title + if (item.required) " (required)" else "",
                                    fontWeight = FontWeight.Bold
                                )
                                Text(item.why, style = MaterialTheme.typography.bodySmall)
                            }
                            TextButton(onClick = { fix(item) }) {
                                Text(if (item.title in deniedOnce) "Settings" else "Allow")
                            }
                        }
                    }
                }
            }
        }

        if (conversation.isNotEmpty()) {
            Text("Recent", style = MaterialTheme.typography.titleMedium)
            conversation.forEach { line ->
                Text(
                    (if (line.fromUser) "You: " else "Sia: ") + line.text,
                    color = if (line.fromUser) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                )
            }
        } else if (enabled) {
            Text(
                "Say \"Hey Sia\", wait for the beep, then your command. Try \"what can you do?\"",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        extraButtons()
    }
}
