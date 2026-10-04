package com.nexus.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
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
import kotlinx.coroutines.*
import java.util.Locale

class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private lateinit var prefs: SharedPreferences
    private lateinit var messages: LinearLayout
    private lateinit var input: EditText
    private lateinit var send: Button
    private lateinit var status: TextView
    private lateinit var tts: TextToSpeech
    private var researchMode = false
    private var speechRecognizer: SpeechRecognizer? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bg = Color.rgb(7,17,31)
    private val panel = Color.rgb(13,30,51)
    private val textColor = Color.rgb(235,242,252)
    private val muted = Color.rgb(160,180,204)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("nexus", MODE_PRIVATE)
        tts = TextToSpeech(this, this)
        buildUi()
        addMessage("Nexus", "I'm ready. Gemini is no longer required. I can run local commands and search the web.")
        ensureAssistantPermissions()
    }

    private fun ensureAssistantPermissions() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO) else startAssistantService()
    }

    private fun startAssistantService() {
        if (!Settings.canDrawOverlays(this)) {
            status.text = "OVERLAY PERMISSION"
            addMessage("Nexus", "Allow 'Display over other apps' for the hands-free popup.")
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, AssistantForegroundService::class.java))
        status.text = "LISTENING…"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == REQUEST_AUDIO && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startAssistantService()
    }

    override fun onResume() {
        super.onResume()
        if (::status.isInitialized && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED && Settings.canDrawOverlays(this)) startAssistantService()
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg); setPadding(20,14,20,14) }
        val top = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply { text = "NEXUS"; textSize = 24f; setTextColor(textColor); typeface = android.graphics.Typeface.DEFAULT_BOLD }, LinearLayout.LayoutParams(0,56,1f))
        status = TextView(this).apply { text = "READY"; textSize = 12f; setTextColor(muted); gravity = Gravity.CENTER_VERTICAL }
        top.addView(status, LinearLayout.LayoutParams(120,56))
        top.addView(Button(this).apply { text = "⚙"; setOnClickListener { showSettings() } }, LinearLayout.LayoutParams(64,56))
        root.addView(top)
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(8,8,8,8) }
        val scroll = ScrollView(this).apply { addView(messages); isFillViewport = true }
        root.addView(scroll, LinearLayout.LayoutParams(-1,0,1f))
        root.addView(Button(this).apply {
            text = "Research: OFF"
            setOnClickListener { researchMode = !researchMode; text = if (researchMode) "Research: ON" else "Research: OFF"; status.text = if (researchMode) "WEB MODE" else "READY" }
        }, LinearLayout.LayoutParams(170,52))
        val composer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        input = EditText(this).apply { hint = "Talk to Nexus…"; hintTextColor = muted; setTextColor(textColor); maxLines = 3; setBackgroundColor(panel); setPadding(18,10,18,10) }
        composer.addView(input, LinearLayout.LayoutParams(0,64,1f))
        composer.addView(Button(this).apply { text = "🎙"; setOnClickListener { startSpeechInput() } }, LinearLayout.LayoutParams(68,64))
        send = Button(this).apply { text = "SEND"; setOnClickListener { sendMessage() } }
        composer.addView(send, LinearLayout.LayoutParams(92,64))
        root.addView(composer)
        setContentView(root)
    }

    private fun addMessage(who: String, body: String) {
        val card = TextView(this).apply { text = "$who\n$body"; textSize = 16f; setTextColor(textColor); setPadding(18,14,18,14); setBackgroundColor(if (who == "Nexus") panel else Color.rgb(20,45,72)) }
        messages.addView(card, LinearLayout.LayoutParams(-1,-2).apply { setMargins(0,6,0,6) })
        card.post { (messages.parent as? ScrollView)?.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendMessage() {
        val prompt = input.text.toString().trim()
        if (prompt.isEmpty()) return
        addMessage("You", prompt); input.text.clear(); send.isEnabled = false; status.text = "THINKING…"
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { NexusResponseEngine(this@MainActivity).handle(prompt, researchMode) }
                result.action?.let { executeAction(it) }
                addMessage("Nexus", result.text); status.text = if (researchMode) "WEB MODE" else "READY"; speak(result.text)
            } catch (e: Exception) {
                addMessage("Nexus", "I couldn't complete that request: " + (e.message ?: "unknown error")); status.text = "ERROR"
            } finally { send.isEnabled = true }
        }
    }

    private fun executeAction(action: NexusAction) {
        try {
            when (action) {
                is NexusAction.OpenUrl -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(action.url)))
                is NexusAction.OpenApp -> startActivity(packageManager.getLaunchIntentForPackage(action.packageName) ?: throw IllegalStateException("That app is not installed."))
            }
        } catch (e: Exception) { addMessage("Nexus", "I couldn't open that: " + (e.message ?: "Android rejected the action")) }
    }

    private fun showSettings() {
        val voice = CheckBox(this).apply { text = "Voice replies"; isChecked = prefs.getBoolean("tts_enabled", true) }
        AlertDialog.Builder(this).setTitle("Nexus Settings").setMessage("Gemini is removed. Nexus now uses local routing/commands and direct web search.").setView(voice).setNegativeButton("Cancel",null).setPositiveButton("Save") { _,_ -> prefs.edit().putBoolean("tts_enabled",voice.isChecked).apply(); addMessage("Nexus","Settings saved.") }.show()
    }

    private fun startSpeechInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { addMessage("Nexus","Speech recognition is not available on this device."); return }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO); return }
        speechRecognizer?.destroy(); speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { status.text = "LISTENING…" }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() { status.text = "PROCESSING…" }
            override fun onError(error: Int) { status.text = "VOICE ERROR"; addMessage("Nexus","Client speech recognition error has occurred (code $error).") }
            override fun onResults(results: Bundle?) { val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull(); if (!spoken.isNullOrBlank()) { input.setText(spoken); input.setSelection(input.length()); sendMessage() } }
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply { putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM); putExtra(RecognizerIntent.EXTRA_LANGUAGE,Locale.getDefault()) })
    }

    private fun speak(message: String) { if (prefs.getBoolean("tts_enabled",true)) tts.speak(message.take(1200),TextToSpeech.QUEUE_FLUSH,null,"nexus_reply") }
    override fun onInit(statusCode: Int) { if (statusCode == TextToSpeech.SUCCESS) tts.language = Locale.getDefault() }
    override fun onDestroy() { speechRecognizer?.destroy(); tts.stop(); tts.shutdown(); scope.cancel(); super.onDestroy() }
    companion object { private const val REQUEST_AUDIO = 40 }
}
