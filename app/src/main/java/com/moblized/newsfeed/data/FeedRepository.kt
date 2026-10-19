package com.moblized.newsfeed.data

import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Http {
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/126.0 Mobile Safari/537.36 NewsFeed/1.0"

    fun get(url: String): ByteArray {
        var current = url
        repeat(6) {
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 12_000
            conn.readTimeout = 15_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty(
                "Accept",
                "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5",
            )
            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Redirect with no address")
                    current = URL(URL(current), location).toString()
                } else if (code in 200..299) {
                    return conn.inputStream.use { it.readBytes() }
                } else {
                    throw IOException("HTTP $code")
                }
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }
}

object FeedRepository {
    private const val PER_SOURCE = 30

    fun load(source: Source): List<Article> {
        val bytes = Http.get(source.url)
        val feed = FeedParser.parse(ByteArrayInputStream(bytes), source, PER_SOURCE)
        if (feed.articles.isEmpty()) throw IOException("No stories found")
        return feed.articles
    }

    fun search(query: String): List<Article> {
        val url = "https://news.google.com/rss/search?q=" + URLEncoder.encode(query, "UTF-8") +
            "&hl=en-US&gl=US&ceid=US:en"
        val source = Source("search", "Google News", url, Category.OTHER)
        return FeedParser.parse(ByteArrayInputStream(Http.get(url)), source, 60).articles
    }

    data class Discovered(val url: String, val title: String?)

    private val linkTagRe = Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE)
    private val feedTypeRe = Regex("application/(rss|atom)\\+xml", RegexOption.IGNORE_CASE)
    private val hrefRe = Regex("href\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    /** Accepts a feed link or a normal website link and finds the feed behind it. */
    fun discover(input: String): Discovered {
        var url = input.trim()
        if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) {
            url = "https://$url"
        }
        val bytes = Http.get(url)
        tryParse(bytes, url)?.let { return it }

        val html = String(bytes, Charsets.UTF_8)
        val advertised = linkTagRe.findAll(html)
            .map { it.value }
            .filter { feedTypeRe.containsMatchIn(it) }
            .mapNotNull { tag -> hrefRe.find(tag)?.groupValues?.get(1) }
            .mapNotNull { href -> runCatching { URL(URL(url), href.replace("&amp;", "&")).toString() }.getOrNull() }
            .toList()
        val guesses = listOf("/feed", "/rss", "/feed.xml", "/rss.xml", "/atom.xml", "/index.xml")
            .mapNotNull { path -> runCatching { URL(URL(url), path).toString() }.getOrNull() }

        for (candidate in (advertised + guesses).distinct().take(8)) {
            val b = runCatching { Http.get(candidate) }.getOrNull() ?: continue
            tryParse(b, candidate)?.let { return it }
        }
        throw IOException("No feed found")
    }

    private fun tryParse(bytes: ByteArray, url: String): Discovered? = runCatching {
        val probe = Source("probe", "", url, Category.OTHER)
        val feed = FeedParser.parse(ByteArrayInputStream(bytes), probe, 5)
        if (feed.articles.isNotEmpty()) Discovered(url, feed.title) else null
    }.getOrNull()
}
