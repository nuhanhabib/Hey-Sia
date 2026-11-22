package com.heysia.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.heysia.app.listen.SiaEars
import com.heysia.app.ui.ListenTestScreen
import com.heysia.app.ui.VoiceTestScreen
import com.heysia.app.voice.Phrases
import com.heysia.app.voice.SiaVoice

class MainActivity : ComponentActivity() {

    private lateinit var voice: SiaVoice
    private lateinit var ears: SiaEars

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = SiaVoice(this)
        ears = SiaEars(this)
        // Greet on a fresh launch only, not when the screen rotates.
        if (savedInstanceState == null) voice.speak(Phrases.GREETING)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var showVoiceSettings by rememberSaveable { mutableStateOf(false) }
                    if (showVoiceSettings) {
                        BackHandler { showVoiceSettings = false }
                        VoiceTestScreen(voice = voice, openScreen = ::openSafely, onRetry = ::recreate)
                    } else {
                        ListenTestScreen(
                            ears = ears,
                            voice = voice,
                            openScreen = ::openSafely,
                            onOpenVoiceSettings = { showVoiceSettings = true }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        // Android blocks the microphone for apps in the background, so stop cleanly instead.
        ears.cancel()
        super.onStop()
    }

    override fun onDestroy() {
        ears.release()
        voice.shutdown()
        super.onDestroy()
    }

    private fun openSafely(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "That screen isn't available on this phone", Toast.LENGTH_SHORT).show()
        }
    }
}
