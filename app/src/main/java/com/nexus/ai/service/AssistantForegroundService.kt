package com.nexus.ai.service

import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import com.nexus.ai.MainActivity
import com.nexus.ai.audio.AudioPipelineManager
import com.nexus.ai.audio.AudioStateManager
import com.nexus.ai.audio.WakeWordEngine
import com.nexus.ai.ui.OverlayViewManager
import kotlinx.coroutines.*
import java.util.Locale

class AssistantForegroundService : Service(), TextToSpeech.OnInitListener {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var audio: AudioPipelineManager
    private lateinit var overlay: OverlayViewManager
    private lateinit var tts: TextToSpeech

    // Adapter boundary for your actual local engine (openWakeWord/Porcupine).
    // It receives PCM from the single AudioRecord owned by AudioPipelineManager.
    private val wakeWordEngine = object : WakeWordEngine {
        private var enabled = false
        override fun start() { enabled = true }
        override fun stop() { enabled = false }
        override fun acceptPcm(pcm: ShortArray, sampleRate: Int) {
            if (!enabled) return
            // Plug the chosen local wake-word SDK here.
            // Never instantiate another AudioRecord in this callback.
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Nexus")
            .setContentText("Hands-free assistant is active")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        overlay = OverlayViewManager(this)
        tts = TextToSpeech(this, this)

        audio = AudioPipelineManager(
            context = this,
            scope = serviceScope,
            wakeWordEngine = wakeWordEngine,
            onStateChanged = { state ->
                when (state) {
                    AudioStateManager.IDLE_LISTENING -> overlay.update("Listening…")
                    AudioStateManager.RECORDING_USER -> overlay.update("Listening to you…")
                    AudioStateManager.PROCESSING -> overlay.update("Thinking…")
                    AudioStateManager.SPEAKING -> overlay.update("Speaking…")
                }
            }
        )

        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                // Belt-and-suspenders: capture is already paused before speak().
                audio.pauseForSpeech()
                overlay.update("Speaking…")
            }

            override fun onDone(utteranceId: String?) {
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

        if (Settings.canDrawOverlays(this)) overlay.show("Listening…")
        audio.start()
    }

    fun speak(text: String) {
        // Stop the microphone BEFORE TTS starts. This is the critical
        // anti-feedback transition.
        audio.pauseForSpeech()
        overlay.update("Speaking…")
        tts.speak(text.take(2500), TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.getDefault()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

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
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Nexus microphone",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "nexus_microphone"
        private const val NOTIFICATION_ID = 7001
        private const val UTTERANCE_ID = "nexus_tts_reply"
    }
}
