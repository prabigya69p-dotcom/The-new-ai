package com.nexus.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class LocalLlmEngine(private val context: Context) {
    companion object {
        private const val MODEL_NAME = "gemma3-1b-it-int4.task"
        private const val MODEL_URL =
            "https://huggingface.co/nikhil2024/gemma3-1b-it-litert-mirror/resolve/main/gemma3-1b-it-int4.task?download=true"

        @Volatile private var instance: LocalLlmEngine? = null

        fun get(context: Context): LocalLlmEngine =
            instance ?: synchronized(this) {
                instance ?: LocalLlmEngine(context.applicationContext).also { instance = it }
            }
    }

    private var llm: LlmInference? = null
    private val modelFile get() = File(context.filesDir, MODEL_NAME)

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        ensureModel()
        getEngine().generateResponse(prompt).trim()
    }

    private fun getEngine(): LlmInference {
        llm?.let { return it }
        val options = LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(512)
            .setMaxTopK(40)
            .build()
        return LlmInference.createFromOptions(context, options).also { llm = it }
    }

    private fun ensureModel() {
        if (modelFile.exists() && modelFile.length() > 100_000_000L) return
        modelFile.parentFile?.mkdirs()
        val temp = File(context.filesDir, "$MODEL_NAME.part")
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Nexus/0.3 Android")
        }
        try {
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Local model download failed: HTTP ${connection.responseCode}")
            }
            connection.inputStream.use { input ->
                FileOutputStream(temp).use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                    }
                    output.fd.sync()
                }
            }
            if (temp.length() < 100_000_000L) {
                throw IllegalStateException("Downloaded local model is incomplete.")
            }
            if (modelFile.exists()) modelFile.delete()
            if (!temp.renameTo(modelFile)) {
                throw IllegalStateException("Could not finalize local model.")
            }
        } finally {
            connection.disconnect()
            if (temp.exists() && temp.length() < 100_000_000L) temp.delete()
        }
    }

    fun close() {
        llm?.close()
        llm = null
    }
}
