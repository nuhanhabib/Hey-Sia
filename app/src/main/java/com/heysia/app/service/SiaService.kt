package com.heysia.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.heysia.app.MainActivity
import com.heysia.app.R
import com.heysia.app.brain.Reply
import com.heysia.app.brain.SiaBrain
import com.heysia.app.listen.SiaEars
import com.heysia.app.listen.SpeechModel
import com.heysia.app.voice.Phrases
import com.heysia.app.voice.SiaVoice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps Sia ready in the background: waits for "Hey Sia", listens to the command, acts, replies.
 *
 * Runs as a foreground service with a permanent notification, which Android requires for any app
 * that uses the microphone while you're not looking at it. Stop it from the notification or the app.
 */
class SiaService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var voice: SiaVoice
    private lateinit var brain: SiaBrain
    private val ears = SiaEars()
    private var loop: Job? = null
    private var talkNowRequested = false
    private val beeper by lazy { ToneGenerator(AudioManager.STREAM_NOTIFICATION, BEEP_VOLUME) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        voice = SiaVoice(this)
        brain = SiaBrain(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (!goForeground()) return START_NOT_STICKY

        if (loop?.isActive != true) loop = scope.launch { runLoop() }
        if (intent?.action == ACTION_TALK_NOW) talkNow()
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        ears.cancel()
        voice.shutdown()
        beeper.release()
        SiaState.setPhase(SiaState.Phase.Off)
        SiaState.setPartial("")
        super.onDestroy()
    }

    /** Skip the wake word and start listening for a command right away. */
    private fun talkNow() {
        talkNowRequested = true
        ears.cancel() // ends waitForWakeWord(); the loop then sees the request
        voice.stop()
    }

    private suspend fun runLoop() {
        SiaState.setPhase(SiaState.Phase.Starting)
        SpeechModel.load(this)
        val loaded = SpeechModel.state.first { it is SpeechModel.State.Ready || it is SpeechModel.State.Failed }
        if (loaded is SpeechModel.State.Failed) {
            SiaState.setPhase(SiaState.Phase.Error, loaded.message)
            return
        }
        scope.launch { ears.partial.collectLatest { SiaState.setPartial(it) } }

        while (scope.isActive) {
            SiaState.setPhase(SiaState.Phase.WaitingForWakeWord)
            val heard = if (talkNowRequested) SiaEars.Heard.Silence else ears.waitForWakeWord()
            when {
                talkNowRequested -> {
                    talkNowRequested = false
                    converse()
                }
                heard is SiaEars.Heard.Text -> converse()
                heard is SiaEars.Heard.Failed -> {
                    SiaState.setPhase(SiaState.Phase.Error, heard.message)
                    delay(RETRY_DELAY_MS) // e.g. another app holds the microphone; try again shortly
                }
            }
        }
    }

    /** One conversation: listen, act, reply, and keep going while Sia has asked a question. */
    private suspend fun converse() {
        beep()
        var handler: suspend (String) -> Reply = brain::handle
        repeat(MAX_TURNS) {
            SiaState.setPhase(SiaState.Phase.Listening)
            when (val heard = ears.listen()) {
                is SiaEars.Heard.Text -> {
                    SiaState.add(fromUser = true, heard.text)
                    SiaState.setPhase(SiaState.Phase.Thinking)
                    val reply = try {
                        handler(heard.text)
                    } catch (e: Exception) {
                        Reply(Phrases.SOMETHING_WRONG)
                    }
                    say(reply.speech)
                    handler = reply.followUp ?: return
                }
                SiaEars.Heard.Silence -> return // nothing said: go back to sleep quietly
                is SiaEars.Heard.Failed -> {
                    say(Phrases.SOMETHING_WRONG)
                    return
                }
            }
        }
    }

    private suspend fun say(text: String) {
        SiaState.add(fromUser = false, text)
        SiaState.setPhase(SiaState.Phase.Speaking)
        voice.speakAndWait(text)
    }

    private suspend fun beep() {
        beeper.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_MS)
        delay(BEEP_MS + 100L) // don't let the microphone hear the beep
    }

    /** Returns false if Android refused (e.g. the microphone permission was taken away). */
    private fun goForeground(): Boolean = try {
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
        true
    } catch (e: Exception) {
        SiaState.setPhase(SiaState.Phase.Error, "Android didn't let me start listening. Open the app and try again.")
        stopSelf()
        false
    }

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "Sia is ready", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Shown while Sia listens for \"Hey Sia\"."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val talk = PendingIntent.getService(
            this, 1, Intent(this, SiaService::class.java).setAction(ACTION_TALK_NOW), PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, SiaService::class.java).setAction(ACTION_STOP), PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_sia_notification)
            .setContentTitle("Sia is ready")
            .setContentText("Say \"Hey Sia\". Everything stays on your phone.")
            .setContentIntent(openApp)
            .addAction(0, "Talk now", talk)
            .addAction(0, "Stop", stop)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val ACTION_STOP = "com.heysia.app.STOP"
        private const val ACTION_TALK_NOW = "com.heysia.app.TALK_NOW"
        private const val CHANNEL_ID = "sia_ready"
        private const val NOTIFICATION_ID = 1
        private const val MAX_TURNS = 4
        private const val RETRY_DELAY_MS = 3000L
        private const val BEEP_MS = 150
        private const val BEEP_VOLUME = 80

        /** Start Sia. Call while the app is on screen: Android doesn't allow starting the mic from the background. */
        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, SiaService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, SiaService::class.java))
        }

        /** Start listening for a command now, without the wake word. Starts Sia if she's off. */
        fun talkNow(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, SiaService::class.java).setAction(ACTION_TALK_NOW)
            )
        }
    }
}
