package com.nexus.ai.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.nexus.ai.MainActivity
import com.nexus.ai.audio.AudioPipelineManager
import com.nexus.ai.audio.AudioState
import com.nexus.ai.ui.OverlayViewManager
import kotlinx.coroutines.*
import java.util.Locale

class AssistantForegroundService : Service(), TextToSpeech.OnInitListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var audio: AudioPipelineManager
    private lateinit var overlay: OverlayViewManager
    private lateinit var tts: TextToSpeech

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nexus")
            .setContentText("Hands-free assistant is listening")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        overlay = OverlayViewManager(this)
        tts = TextToSpeech(this, this)

        audio = AudioPipelineManager(
            context = this,
            scope = serviceScope,
            onPcmAudio = { pcm, sampleRate ->
                // Feed this PCM stream to your local wake-word engine.
                // Keep this callback lightweight; all work is already off the UI thread.
                wakeWordEngine.acceptPcm(pcm, sampleRate)
            },
            onStateChanged = { state ->
                when (state) {
                    AudioState.IDLE_LISTENING -> overlay.update("Listening…")
                    AudioState.RECORDING_USER -> overlay.update("Listening to you…")
                    AudioState.PROCESSING -> overlay.update("Thinking…")
                    AudioState.SPEAKING -> overlay.update("Speaking…")
                }
            }
        )

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                audio.pauseForSpeech()
                overlay.update("Speaking…")
            }

            override fun onDone(utteranceId: String?) {
                // Only resume capture after TTS has fully stopped.
                serviceScope.launch(Dispatchers.Main.immediate) {
                    audio.resumeAfterSpeech()
                    overlay.update("Listening…")
                }
            }

            override fun onError(utteranceId: String?) {
                serviceScope.launch(Dispatchers.Main.immediate) {
                    audio.resumeAfterSpeech()
                    overlay.update("Listening…")
                }
            }
        })

        if (android.provider.Settings.canDrawOverlays(this)) overlay.show("Listening…")
        audio.start()
    }

    /**
     * Call this when the assistant has generated a final answer.
     * pauseForSpeech() is also called immediately by the utterance callback,
     * preventing TTS audio from reaching the wake-word detector.
     */
    fun speak(text: String) {
        audio.pauseForSpeech()
        overlay.update("Speaking…")
        tts.speak(
            text.take(2500),
            TextToSpeech.QUEUE_FLUSH,
            null,
            UTTERANCE_ID
        )
    }

    private val wakeWordEngine = object {
        fun acceptPcm(pcm: ShortArray, sampleRate: Int) {
            // Adapter boundary for openWakeWord / Porcupine.
            // Do not run a second AudioRecord here.
            // The engine must emit onWakeWordDetected() from this same PCM stream.
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        audio.stop()
        serviceScope.cancel()
        tts.stop()
        tts.shutdown()
        overlay.hide()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nexus microphone",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent notification for Nexus hands-free listening"
                }
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "nexus_microphone"
        private const val NOTIFICATION_ID = 7001
        private const val UTTERANCE_ID = "nexus_tts_reply"
    }
}
