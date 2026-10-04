package com.nexus.ai.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.*
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

enum class AudioState {
    IDLE_LISTENING,
    RECORDING_USER,
    PROCESSING,
    SPEAKING
}

class AudioPipelineManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPcmAudio: suspend (ShortArray, Int) -> Unit,
    private val onStateChanged: (AudioState) -> Unit
) {
    private var audioRecord: AudioRecord? = null
    private var aec: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var recordingJob: Job? = null
    private val running = AtomicBoolean(false)
    private val paused = AtomicBoolean(false)

    @Volatile
    var state: AudioState = AudioState.IDLE_LISTENING
        private set

    fun start() {
        if (running.get()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO permission is not granted")
            return
        }

        running.set(true)
        paused.set(false)
        setState(AudioState.IDLE_LISTENING)

        recordingJob = scope.launch(Dispatchers.IO) {
            runRecordingLoop()
        }
    }

    /**
     * Immediately stops audio capture and wake-word processing.
     * This is called before TTS starts so speaker output cannot retrigger the assistant.
     */
    fun pauseForSpeech() {
        paused.set(true)
        releaseAudioRecord()
        setState(AudioState.SPEAKING)
    }

    /**
     * Recreates the capture chain after TTS has completely finished.
     */
    fun resumeAfterSpeech() {
        if (!running.get()) return
        paused.set(false)
        if (recordingJob?.isActive != true) {
            recordingJob = scope.launch(Dispatchers.IO) { runRecordingLoop() }
        }
        setState(AudioState.IDLE_LISTENING)
    }

    fun setRecordingUser(recording: Boolean) {
        if (!running.get() || paused.get()) return
        setState(if (recording) AudioState.RECORDING_USER else AudioState.IDLE_LISTENING)
    }

    fun setProcessing() {
        if (!paused.get()) setState(AudioState.PROCESSING)
    }

    fun stop() {
        running.set(false)
        paused.set(true)
        recordingJob?.cancel()
        recordingJob = null
        releaseAudioRecord()
        setState(AudioState.IDLE_LISTENING)
    }

    private suspend fun runRecordingLoop() {
        try {
            createAudioRecord()

            val record = audioRecord ?: return
            val buffer = ShortArray(BUFFER_SAMPLES)

            while (currentCoroutineContext().isActive && running.get()) {
                if (paused.get() || state == AudioState.SPEAKING) {
                    delay(20)
                    continue
                }

                val count = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (count > 0 && !paused.get() && state != AudioState.SPEAKING) {
                    onPcmAudio(buffer.copyOf(count), SAMPLE_RATE)
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            Log.e(TAG, "Audio loop stopped", t)
        } finally {
            releaseAudioRecord()
        }
    }

    private fun createAudioRecord() {
        releaseAudioRecord()

        val source = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    CHANNEL_CONFIG,
                    AUDIO_FORMAT
                ) > 0 -> MediaRecorder.AudioSource.VOICE_RECOGNITION
            else -> MediaRecorder.AudioSource.VOICE_RECOGNITION
        }

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT
        ).coerceAtLeast(BUFFER_SAMPLES * 2)

        val record = AudioRecord(
            source,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            minBuffer * 2
        )

        check(record.state == AudioRecord.STATE_INITIALIZED) {
            "AudioRecord failed to initialize"
        }

        audioRecord = record

        // AEC/NS must be attached to the AudioRecord session, not guessed globally.
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

    private fun setState(newState: AudioState) {
        state = newState
        onStateChanged(newState)
    }

    companion object {
        private const val TAG = "NexusAudio"
        const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SAMPLES = 1600
    }
}
