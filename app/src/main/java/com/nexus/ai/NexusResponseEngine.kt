package com.nexus.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NexusResponse(val text: String, val action: NexusAction? = null)

class NexusResponseEngine(private val context: android.content.Context) {
    private val search = WebSearchClient()
    private val localLlm = LocalLlmEngine.get(context)

    suspend fun handle(input: String, forceResearch: Boolean = false): NexusResponse {
        val q = input.trim()
        val lower = q.lowercase(Locale.ROOT)

        localCommand(lower)?.let { return it }

        if (lower in setOf("hi", "hello", "hey", "hey nexus", "good morning", "good afternoon", "good evening")) {
            return NexusResponse("Hey! I'm Nexus. What do you need?")
        }

        if (forceResearch || shouldSearch(lower)) {
            val results = search.search(q)
            if (results.isEmpty()) {
                return NexusResponse("I couldn't get web results right now. Check your internet connection and try again.")
            }

            val contextText = results.take(8).joinToString("\n\n") { result ->
                "TITLE: ${result.title}\nURL: ${result.url}\n${result.snippet}"
            }

            return try {
                val answer = localLlm.generate(
                    """
                    You are Nexus, a concise Android voice assistant.
                    Answer the user's question using ONLY the web results below.
                    If the results do not contain enough information, say that clearly.
                    Do not invent facts. Do not mention that you are a local model.
                    Keep the answer natural and useful for speech.

                    USER QUESTION:
                    $q

                    WEB RESULTS:
                    $contextText
                    """.trimIndent()
                )
                NexusResponse(if (answer.isBlank()) "I found web results, but I couldn't summarize them." else answer)
            } catch (e: Exception) {
                NexusResponse("I found web results, but the local AI model could not process them: ${e.message ?: "unknown error"}")
            }
        }

        return try {
            val answer = localLlm.generate(
                """
                You are Nexus, a helpful Android voice assistant.
                Answer naturally and directly.
                Keep normal answers concise enough to speak aloud.
                Do not claim to have searched the web unless web results were provided.
                Do not invent current facts.

                USER:
                $q
                """.trimIndent()
            )
            NexusResponse(if (answer.isBlank()) "I couldn't generate a response." else answer)
        } catch (e: Exception) {
            NexusResponse("The local AI model isn't ready yet: ${e.message ?: "unknown error"}")
        }
    }

    private fun localCommand(q: String): NexusResponse? {
        if (q.contains("open youtube")) return NexusResponse("Opening YouTube.", NexusAction.OpenUrl("https://www.youtube.com/"))
        if (q.contains("open chrome") || q.contains("open google chrome")) return NexusResponse("Opening Chrome.", NexusAction.OpenApp("com.android.chrome"))
        if (q == "what time is it" || q.contains("current time")) return NexusResponse("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + ".")
        if (q == "what date is it" || q.contains("today's date") || q.contains("todays date")) return NexusResponse("Today is " + SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()) + ".")
        return null
    }

    private fun shouldSearch(q: String): Boolean =
        listOf("search", "look up", "latest", "current", "today", "news", "weather", "who is", "what is", "what are", "when is", "where is", "how much", "price", "score", "stock", "update", "recent")
            .any { q.contains(it) } || q.endsWith("?")
}
