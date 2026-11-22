package com.heysia.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.heysia.app.actions.AppLauncher
import com.heysia.app.notify.PrivateApps

/** Choose apps whose notifications Sia must never read aloud. */
@Composable
fun PrivateAppsScreen() {
    val context = LocalContext.current
    val privateApps = remember { PrivateApps(context) }
    val apps = remember { AppLauncher(context).apps().sortedBy { it.label.lowercase() } }
    var marked by remember {
        mutableStateOf(apps.filter { privateApps.isPrivate(it.packageName, it.label) }.map { it.packageName }.toSet())
    }

    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Private apps", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Sia never reads notifications from these apps. One-time codes are skipped from every app.",
            style = MaterialTheme.typography.bodyMedium
        )
        LazyColumn {
            items(apps, key = { it.packageName }) { app ->
                val checked = app.packageName in marked
                fun toggle() {
                    marked = if (checked) marked - app.packageName else marked + app.packageName
                    privateApps.save(marked) // saves exactly what you see, defaults included
                }
                Row(
                    Modifier.fillMaxWidth().clickable { toggle() }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = checked, onCheckedChange = { toggle() })
                    Text(app.label)
                }
            }
        }
    }
}
