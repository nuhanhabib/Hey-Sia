package com.heysia.app

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import com.heysia.app.ui.VoiceTestScreen
import com.heysia.app.voice.SiaVoice
import com.heysia.app.voice.Phrases

class MainActivity : ComponentActivity() {

    private lateinit var voice: SiaVoice

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voice = SiaVoice(this)
        // Greet on a fresh launch only, not when the screen rotates.
        if (savedInstanceState == null) voice.speak(Phrases.GREETING)

        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VoiceTestScreen(voice = voice, openScreen = ::openSafely, onRetry = ::recreate)
                }
            }
        }
    }

    override fun onDestroy() {
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
