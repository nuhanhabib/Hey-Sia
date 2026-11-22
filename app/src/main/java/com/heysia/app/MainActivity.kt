package com.heysia.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.edit
import com.heysia.app.service.SiaService
import com.heysia.app.ui.HomeScreen
import com.heysia.app.ui.Setup
import com.heysia.app.ui.VoiceTestScreen
import com.heysia.app.voice.SiaVoice

class MainActivity : ComponentActivity() {

    private var voice: SiaVoice? = null // created only when the voice settings screen opens
    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val enabledState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enabledState.value = prefs.getBoolean(KEY_ENABLED, false)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var page by rememberSaveable { mutableStateOf(Page.Home) }
                    BackHandler(enabled = page != Page.Home) { page = Page.Home }
                    when (page) {
                        Page.Home -> HomeScreen(
                            enabled = enabledState.value,
                            onEnabledChange = ::setEnabled,
                            openScreen = ::openSafely,
                            extraButtons = {
                                OutlinedButton(onClick = { page = Page.Voice }) { Text("Voice settings") }
                            }
                        )
                        Page.Voice -> VoiceTestScreen(voice = voice(), openScreen = ::openSafely, onRetry = ::recreate)
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // After a restart of the phone, opening the app once brings Sia back.
        // (Android doesn't let apps switch the microphone on by themselves at boot.)
        if (enabledState.value && Setup.granted(this, android.Manifest.permission.RECORD_AUDIO)) {
            SiaService.start(this)
        }
    }

    override fun onDestroy() {
        voice?.shutdown()
        super.onDestroy()
    }

    private fun setEnabled(enabled: Boolean) {
        enabledState.value = enabled
        prefs.edit { putBoolean(KEY_ENABLED, enabled) }
        if (enabled) SiaService.start(this) else SiaService.stop(this)
    }

    private fun voice(): SiaVoice = voice ?: SiaVoice(this).also { voice = it }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "That screen isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    }

    private enum class Page { Home, Voice }

    companion object {
        private const val PREFS = "sia_settings"
        private const val KEY_ENABLED = "enabled"
    }
}
