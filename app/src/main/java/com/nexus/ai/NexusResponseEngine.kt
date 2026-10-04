package com.nexus.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class NexusResponse(val text: String, val action: NexusAction? = null)

class NexusResponseEngine(private val context: android.content.Context) {
    private val search = WebSearchClient()
    fun handle(input: String, forceResearch: Boolean = false): NexusResponse {
        val q = input.trim()
        val lower = q.lowercase(Locale.ROOT)
        localCommand(lower)?.let { return it }
        if (lower in setOf("hi", "hello", "hey", "hey nexus", "good morning", "good afternoon", "good evening")) return NexusResponse("Hey! I'm Nexus. What do you need?")
        if (forceResearch || shouldSearch(lower)) {
            val results = search.search(q)
            if (results.isEmpty()) return NexusResponse("I couldn't get web results right now. Check your internet connection and try again.")
            return NexusResponse(buildString {
                append("I searched the web for: ").append(q).append("\n\n")
                results.take(5).forEachIndexed { i, r ->
                    append(i + 1).append(". ").append(r.title).append("\n")
                    if (r.snippet.isNotBlank()) append(r.snippet).append("\n")
                    append(r.url).append("\n\n")
                }
            }.trim())
        }
        return NexusResponse("I can handle Android commands and web research without Gemini. For open-ended conversations, Nexus needs an actual language model; I won't fake an AI answer.")
    }
    private fun localCommand(q: String): NexusResponse? {
        if (q.contains("open youtube")) return NexusResponse("Opening YouTube.", NexusAction.OpenUrl("https://www.youtube.com/"))
        if (q.contains("open chrome") || q.contains("open google chrome")) return NexusResponse("Opening Chrome.", NexusAction.OpenApp("com.android.chrome"))
        if (q == "what time is it" || q.contains("current time")) return NexusResponse("It's " + SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()) + ".")
        if (q == "what date is it" || q.contains("today's date") || q.contains("todays date")) return NexusResponse("Today is " + SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.getDefault()).format(Date()) + ".")
        return null
    }
    private fun shouldSearch(q: String): Boolean = listOf("search", "look up", "latest", "current", "today", "news", "weather", "who is", "what is", "what are", "when is", "where is", "how much", "price", "score", "stock", "update", "recent").any { q.contains(it) } || q.endsWith("?")
}
