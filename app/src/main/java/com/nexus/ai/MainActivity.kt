package com.nexus.ai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.core.content.ContextCompat
import com.nexus.ai.service.AssistantForegroundService
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var researchMode = false
    private var speechRecognizer: SpeechRecognizer? = null

    private val bg = Color.rgb(7,17,31)
    private val panel = Color.rgb(13,30,51)
    private val textColor = Color.rgb(235,242,252)
    private val muted = Color.rgb(160,180,204)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nexus", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        buildUi()
        addMessage("Nexus", "I'm ready. Add your Gemini API key in Settings, then ask me anything.")
        ensureAssistantPermissions()
    }

    private fun ensureAssistantPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
        } else {
            startAssistantService()
        }
    }

    private fun startAssistantService() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "OVERLAY PERMISSION"
            addMessage("Nexus", "Allow 'Display over other apps' for Nexus to show its hands-free popup.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }

        val intent = Intent(this, AssistantForegroundService::class.java)
        ContextCompat.startForegroundService(this, intent)
        status.text = "LISTENING…"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_AUDIO &&
            results.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startAssistantService()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized &&
            checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED &&
            Settings.canDrawOverlays(this)) {
            startAssistantService()
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(20,14,20,14)
        }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply {
            text = "NEXUS"
            textSize = 24f
            setTextColor(textColor)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        top.addView(title, LinearLayout.LayoutParams(0,56,1f))
        status = TextView(this).apply {
            text = "READY"
            textSize = 12f
            setTextColor(muted)
            gravity = Gravity.CENTER_VERTICAL
        }
        top.addView(status, LinearLayout.LayoutParams(100,56))
        top.addView(Button(this).apply {
            text = "⚙"
            setOnClickListener { showSettings() }
        }, LinearLayout.LayoutParams(64,56))
        root.addView(top)

        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8,8,8,8)
        }
        val scroll = ScrollView(this).apply {
            addView(messages)
            isFillViewport = true
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))

        val mode = Button(this).apply {
            text = "Research: OFF"
            setOnClickListener {
                researchMode = !researchMode
                text = if (researchMode) "Research: ON" else "Research: OFF"
                status.text = if (researchMode) "WEB MODE" else "READY"
            }
        }
        root.addView(mode, LinearLayout.LayoutParams(170,52))

        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply {
            hint = "Talk to Nexus…"
            hintTextColor = muted
            setTextColor(textColor)
            setSingleLine(false)
            maxLines = 3
            setBackgroundColor(panel)
            setPadding(18,10,18,10)
        }
        composer.addView(input, LinearLayout.LayoutParams(0,64,1f))
        composer.addView(Button(this).apply {
            text = "🎙"
            setOnClickListener { startSpeechInput() }
        }, LinearLayout.LayoutParams(68,64))
        send = Button(this).apply {
            text = "SEND"
            setOnClickListener { sendMessage() }
        }
        composer.addView(send, LinearLayout.LayoutParams(92,64))
        root.addView(composer)
        setContentView(root)
    }

    private fun addMessage(who: String, body: String) {
        val card = TextView(this).apply {
            text = "$who\n$body"
            textSize = 16f
            setTextColor(textColor)
            setPadding(18,14,18,14)
            setBackgroundColor(if (who == "Nexus") panel else Color.rgb(20,45,72))
        }
        messages.addView(card, LinearLayout.LayoutParams(-1,-2).apply {
            setMargins(0,6,0,6)
        })
        card.post { (messages.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        val key = prefs.getString("gemini_key","")?.trim().orEmpty()
        if (key.isEmpty()) {
            addMessage("Nexus","No Gemini API key is configured. Open Settings and add your key.")
            showSettings()
            return
        }
        addMessage("You",prompt)
        input.text.clear()
        send.isEnabled = false
        status.text = "THINKING…"
        val model = prefs.getString("model","gemini-2.5-flash") ?: "gemini-2.5-flash"
        thread {
            try {
                val reply = GeminiClient(key,model).generate(prompt,researchMode)
                runOnUiThread {
                    addMessage("Nexus",reply)
                    status.text = if (researchMode) "WEB MODE" else "READY"
                    send.isEnabled = true
                    speak(reply)
                }
            } catch (e: Exception) {
                runOnUiThread {
                    addMessage("Nexus","I couldn't complete that request: \${e.message ?: "network error"}")
                    status.text = "ERROR"
                    send.isEnabled = true
                }
            }
        }
    }

    private fun showSettings() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32,12,32,4)
        }
        val key = EditText(this).apply {
            hint = "Gemini API key"
            inputType = 0x81
            setText(prefs.getString("gemini_key",""))
        }
        val model = EditText(this).apply {
            hint = "Model"
            setText(prefs.getString("model","gemini-2.5-flash"))
        }
        box.addView(key)
        box.addView(model)
        AlertDialog.Builder(this)
            .setTitle("Nexus Settings")
            .setMessage("The key is stored only in this app's private preferences. Never commit it to GitHub.")
            .setView(box)
            .setNegativeButton("Cancel",null)
            .setPositiveButton("Save") { _,_ ->
                prefs.edit()
                    .putString("gemini_key",key.text.toString().trim())
                    .putString("model",model.text.toString().trim().ifEmpty { "gemini-2.5-flash" })
                    .apply()
                addMessage("Nexus","Settings saved.")
            }.show()
    }

    private fun startSpeechInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            addMessage("Nexus","Speech recognition is not available on this device.")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        speechRecognizer?.destroy()
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "LISTENING…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "PROCESSING…" }
            override fun onError(error: Int) {
                status.text = "VOICE ERROR"
                addMessage("Nexus","Client speech recognition error has occurred (code $error).")
            }
            override fun onResults(results: Bundle?) {
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                if (!spoken.isNullOrBlank()) {
                    input.setText(spoken)
                    input.setSelection(input.length())
                    sendMessage()
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault())
        })
    }

    private fun speak(message: String) {
        if (prefs.getBoolean("tts_enabled",true))
            tts.speak(message.take(1200),TextToSpeech.QUEUE_FLUSH,null,"nexus_reply")
    }

    override fun onInit(statusCode: Int) {
        if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.getDefault()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }

    private class GeminiClient(private val apiKey: String, private val model: String) {
        fun generate(prompt: String,research: Boolean): String {
            val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 60000
                doOutput = true
                setRequestProperty("Content-Type","application/json")
                setRequestProperty("x-goog-api-key",apiKey)
            }
            val instruction = if (research)
                "Use Google Search grounding when useful. Answer clearly and mention source names or URLs when available.\n\n$prompt"
            else
                "You are Nexus, a concise, natural Android AI assistant. Be useful and honest about limits.\n\n$prompt"
            val body = JSONObject().apply {
                put("contents",JSONArray().put(
                    JSONObject().put("role","user").put(
                        "parts",JSONArray().put(JSONObject().put("text",instruction))
                    )
                ))
                if (research) put("tools",JSONArray().put(JSONObject().put("google_search",JSONObject())))
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Gemini HTTP $code: \${extractError(raw)}")
            val candidates = JSONObject(raw).optJSONArray("candidates")
                ?: throw IllegalStateException("No response candidate returned")
            if (candidates.length() == 0) throw IllegalStateException("Empty response")
            val parts = candidates.getJSONObject(0).optJSONObject("content")?.optJSONArray("parts")
                ?: throw IllegalStateException("No response text returned")
            val out = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.optJSONObject(i) ?: continue
                if (part.has("text")) out.append(part.optString("text"))
            }
            return out.toString().trim().ifEmpty { "The model returned no text." }
        }
        private fun extractError(raw: String): String = try {
            JSONObject(raw).optJSONObject("error")?.optString("message").orEmpty()
                .ifEmpty { raw.take(300) }
        } catch (_: Exception) { raw.take(300) }
    }

    companion object {
        private const val REQUEST_AUDIO = 40
    }
}
