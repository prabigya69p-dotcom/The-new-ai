package com.nexus.ai.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-owner microphone pipeline.
 *
 * The important rule is that Nexus owns exactly one AudioRecord. Wake-word
 * detection consumes the PCM produced here; it must never create a second
 * microphone capture loop.
 */
enum class AudioStateManager {
    IDLE_LISTENING,
    RECORDING_USER,
    PROCESSING,
    SPEAKING
}

interface WakeWordEngine {
    fun start()
    fun stop()
    fun acceptPcm(pcm: ShortArray, sampleRate: Int)
}

class AudioPipelineManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val wakeWordEngine: WakeWordEngine,
    private val onPcmAudio: suspend (ShortArray, Int) -> Unit = { _, _ -> },
    private val onStateChanged: (AudioStateManager) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @Volatile
    var state: AudioStateManager = AudioStateManager.IDLE_LISTENING
        private set

    fun start() {
        if (running.getAndSet(true)) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            running.set(false)
            return
        }

        paused.set(false)
        wakeWordEngine.start()
        setState(AudioStateManager.IDLE_LISTENING)
        recordingJob = scope.launch(Dispatchers.IO) { runRecordingLoop() }
    }

    /** Immediately tears down capture and wake-word processing before TTS. */
    fun pauseForSpeech() {
        paused.set(true)
        wakeWordEngine.stop()
        releaseAudioRecord()
        setState(AudioStateManager.SPEAKING)
    }

    /** Called only after TTS onDone/onError. */
    fun resumeAfterSpeech() {
        if (!running.get()) return
        paused.set(false)
        wakeWordEngine.start()
        if (recordingJob?.isActive != true) {
            recordingJob = scope.launch(Dispatchers.IO) { runRecordingLoop() }
        }
        setState(AudioStateManager.IDLE_LISTENING)
    }

    fun setRecordingUser(recording: Boolean) {
        if (!running.get() || paused.get()) return
        setState(if (recording) AudioStateManager.RECORDING_USER else AudioStateManager.IDLE_LISTENING)
    }

    fun setProcessing() {
        if (!paused.get()) setState(AudioStateManager.PROCESSING)
    }

    fun stop() {
        running.set(false)
        paused.set(true)
        wakeWordEngine.stop()
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
        setState(AudioStateManager.IDLE_LISTENING)
    }

    private suspend fun runRecordingLoop() {
        try {
            createAudioRecord()
            val record = audioRecord ?: return
            val buffer = ShortArray(BUFFER_SAMPLES)

            while (currentCoroutineContext().isActive && running.get()) {
                if (paused.get() || state == AudioStateManager.SPEAKING) {
                    delay(20)
                    continue
                }

                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0 && !paused.get() && state != AudioStateManager.SPEAKING) {
                    val pcm = buffer.copyOf(count)
                    wakeWordEngine.acceptPcm(pcm, SAMPLE_RATE)
                    onPcmAudio(pcm, SAMPLE_RATE)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // The service can restart the pipeline if Android/audio hardware
            // temporarily revokes the input device.
        } finally {
            releaseAudioRecord()
        }
    }

    private fun createAudioRecord() {
        releaseAudioRecord()

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(BUFFER_SAMPLES * 2)

        // VOICE_RECOGNITION is preferred for assistant input because OEMs may
        // provide voice-oriented DSP. AEC/NS are explicitly attached below.
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBuffer * 2
        )

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }

        audioRecord = record

        if (AcousticEchoCanceler.isAvailable()) {
            aec = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                enabled = true
            }
        }

        if (NoiseSuppressor.isAvailable()) {
            noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                enabled = true
            }
        }

        record.startRecording()
    }

    private fun releaseAudioRecord() {
        try { audioRecord?.stop() } catch (_: Throwable) {}
        try { aec?.enabled = false } catch (_: Throwable) {}
        try { noiseSuppressor?.enabled = false } catch (_: Throwable) {}
        try { aec?.release() } catch (_: Throwable) {}
        try { noiseSuppressor?.release() } catch (_: Throwable) {}
        aec = null
        noiseSuppressor = null
        try { audioRecord?.release() } catch (_: Throwable) {}
        audioRecord = null
    }

    private fun setState(newState: AudioStateManager) {
        state = newState
        onStateChanged(newState)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SAMPLES = 1600
    }
}
