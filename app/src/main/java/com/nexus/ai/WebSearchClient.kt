package com.nexus.ai

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.regex.Pattern

data class WebResult(val title: String, val snippet: String, val url: String)

class WebSearchClient {
    fun search(query: String): List<WebResult> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val connection = (URL("https://html.duckduckgo.com/html/?q=$encoded").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Nexus/0.3")
            setRequestProperty("Accept", "text/html")
        }
        val code = connection.responseCode
        if (code !in 200..299) throw IllegalStateException("Search HTTP $code")
        val html = BufferedReader(InputStreamReader(connection.inputStream, Charsets.UTF_8)).use { it.readText() }
        return parse(html)
    }

    private fun parse(html: String): List<WebResult> {
        val out = mutableListOf<WebResult>()
        val pattern = Pattern.compile("(?s)<a[^>]*class=[\\\"'][^\\\"']*result__a[^\\\"']*[\\\"'][^>]*href=[\\\"']([^\\\"']+)[\\\"'][^>]*>(.*?)</a>")
        val matcher = pattern.matcher(html)
        while (matcher.find() && out.size < 8) {
            val url = decode(matcher.group(1))
            val title = clean(matcher.group(2))
            if (title.isBlank() || url.isBlank()) continue
            out += WebResult(title, "", url)
        }
        return out
    }

    private fun clean(value: String): String = decode(value.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ")).trim()
    private fun decode(value: String): String = value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#x27;", "'").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
