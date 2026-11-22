package com.heysia.app.actions

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * Latest headlines from free public RSS feeds (Google News, BBC). No account, no API key.
 * Only used when you're online and ask for news.
 */
class News(private val countryCode: String) {

    /** Top headlines, or headlines about [topic]. Empty if nothing could be fetched. */
    suspend fun headlines(topic: String? = null, count: Int = 5): List<String> = withContext(Dispatchers.IO) {
        withTimeoutOrNull(TIMEOUT_MS) {
            feedsFor(topic).firstNotNullOfOrNull { url ->
                fetch(url)?.let { parseTitles(it) }?.takeIf { it.isNotEmpty() }
            }?.take(count)
        }.orEmpty()
    }

    private fun feedsFor(topic: String?): List<String> {
        val gl = countryCode.uppercase(Locale.US).ifBlank { "US" }
        val locale = "hl=en-$gl&gl=$gl&ceid=$gl:en"
        return if (topic != null) {
            listOf("https://news.google.com/rss/search?q=${URLEncoder.encode(topic, "UTF-8")}&$locale")
        } else {
            listOf("https://news.google.com/rss?$locale", "https://feeds.bbci.co.uk/news/world/rss.xml")
        }
    }

    private fun fetch(url: String): String? = try {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 5000
            readTimeout = 5000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Android) Sia")
        }
        conn.inputStream.bufferedReader().use { it.readText() }.also { conn.disconnect() }
    } catch (e: Exception) {
        null
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L

        private val ITEM_TITLE = Regex("<item\\b[^>]*>.*?<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)

        /** Headline titles from an RSS document, cleaned up for reading aloud. */
        fun parseTitles(xml: String): List<String> =
            ITEM_TITLE.findAll(xml).map { clean(it.groupValues[1]) }.filter { it.isNotBlank() }.distinct().toList()

        private fun clean(raw: String): String = raw
            .removePrefix("<![CDATA[").removeSuffix("]]>")
            .replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
            .replace(Regex("<[^>]+>"), "")
            .replace(Regex("\\s+-\\s+[^-]{2,40}$"), "") // Google News adds " - Source name"
            .trim()
    }
}
