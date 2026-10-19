package com.moblized.newsfeed.data

import android.text.Html
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ParsedFeed(val title: String?, val articles: List<Article>)

/** Reads RSS 2.0, RSS 1.0 (RDF) and Atom feeds. */
object FeedParser {

    fun parse(input: InputStream, source: Source, limit: Int = 40): ParsedFeed {
        val parser = Xml.newPullParser()
        runCatching { parser.setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) }
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var feedTitle: String? = null
        val out = ArrayList<Article>()
        var item: ItemBuilder? = null
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT && out.size < limit) {
            if (event == XmlPullParser.START_TAG) {
                val name = parser.name.lowercase(Locale.ROOT)
                val cur = item
                if (name == "item" || name == "entry") {
                    item = ItemBuilder()
                } else if (cur == null) {
                    if (name == "title" && feedTitle == null) {
                        feedTitle = readText(parser).trim().ifEmpty { null }
                    }
                } else {
                    handleItemTag(parser, name, cur)
                }
            } else if (event == XmlPullParser.END_TAG) {
                val name = parser.name.lowercase(Locale.ROOT)
                val cur = item
                if ((name == "item" || name == "entry") && cur != null) {
                    cur.build(source)?.let { out.add(it) }
                    item = null
                }
            }
            event = parser.next()
        }
        return ParsedFeed(feedTitle?.let { clean(it) }?.ifBlank { null }, out)
    }

    private fun handleItemTag(parser: XmlPullParser, name: String, cur: ItemBuilder) {
        when (name) {
            "title" -> cur.title = readText(parser)
            "link" -> {
                val href = parser.getAttributeValue(null, "href")
                if (href != null) {
                    val rel = parser.getAttributeValue(null, "rel")
                    val type = parser.getAttributeValue(null, "type")
                    if (rel == null || rel == "alternate") {
                        if (cur.link.isNullOrBlank()) cur.link = href
                    } else if (rel == "enclosure" && type?.startsWith("image") == true) {
                        cur.offerImage(href, 0)
                    }
                } else {
                    val text = readText(parser).trim()
                    if (text.isNotEmpty() && cur.link.isNullOrBlank()) cur.link = text
                }
            }
            "guid", "id" -> cur.guid = readText(parser).trim()
            "description", "summary", "media:description" -> {
                val text = readText(parser)
                if (cur.summary.isNullOrBlank()) cur.summary = text
            }
            "content:encoded", "content" -> cur.content = readText(parser)
            "pubdate", "published", "dc:date", "updated", "a10:updated" -> {
                val text = readText(parser)
                if (cur.date.isNullOrBlank() || name == "pubdate" || name == "published") cur.date = text
            }
            "media:content", "media:thumbnail" -> {
                val url = parser.getAttributeValue(null, "url")
                val medium = parser.getAttributeValue(null, "medium")
                val type = parser.getAttributeValue(null, "type")
                val isImage = name == "media:thumbnail" ||
                    medium == "image" ||
                    type?.startsWith("image") == true ||
                    (medium == null && type == null && looksLikeImage(url))
                if (url != null && isImage) {
                    cur.offerImage(url, parser.getAttributeValue(null, "width")?.toIntOrNull() ?: 0)
                }
            }
            "enclosure" -> {
                val url = parser.getAttributeValue(null, "url")
                val type = parser.getAttributeValue(null, "type")
                if (url != null && (type?.startsWith("image") == true || (type == null && looksLikeImage(url)))) {
                    cur.offerImage(url, 0)
                }
            }
            "itunes:image" -> parser.getAttributeValue(null, "href")?.let { cur.offerImage(it, 0) }
            "source" -> cur.publisher = readText(parser).trim()
        }
    }

    /** Reads everything up to this element's end tag. Keeps <img> tags so we can find pictures later. */
    private fun readText(parser: XmlPullParser): String {
        val sb = StringBuilder()
        val depth = parser.depth
        var ev = parser.next()
        while (!(ev == XmlPullParser.END_TAG && parser.depth == depth)) {
            when (ev) {
                XmlPullParser.TEXT -> sb.append(parser.text)
                XmlPullParser.START_TAG -> {
                    val tag = parser.name.lowercase(Locale.ROOT)
                    if (tag == "img") {
                        parser.getAttributeValue(null, "src")?.let { sb.append("<img src=\"").append(it).append("\">") }
                    } else if (tag == "p" || tag == "br" || tag == "div") {
                        sb.append(' ')
                    }
                }
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
            ev = parser.next()
        }
        return sb.toString()
    }

    private class ItemBuilder {
        var title: String? = null
        var link: String? = null
        var guid: String? = null
        var summary: String? = null
        var content: String? = null
        var date: String? = null
        var publisher: String? = null
        private var image: String? = null
        private var imageWidth = -1

        fun offerImage(url: String, width: Int) {
            if (url.isBlank() || FeedParser.isJunkImage(url)) return
            if (image == null || width > imageWidth) {
                image = url
                imageWidth = width
            }
        }

        fun build(source: Source): Article? {
            val url = link?.trim()?.takeIf { it.startsWith("http") }
                ?: guid?.trim()?.takeIf { it.startsWith("http") }
                ?: return null

            var cleanTitle = FeedParser.clean(title ?: "")
            if (cleanTitle.isBlank()) return null

            val pub = publisher?.let { FeedParser.clean(it) }?.takeIf { it.isNotBlank() && it.length <= 60 }
            if (pub != null && cleanTitle.endsWith(" - $pub")) {
                cleanTitle = cleanTitle.removeSuffix(" - $pub").trim()
            }

            val rawImage = image
                ?: FeedParser.findImage(content)
                ?: FeedParser.findImage(summary)
            val imageUrl = rawImage?.let { FeedParser.fixUrl(it, url) }

            val rawSummary = summary?.takeIf { it.isNotBlank() } ?: content ?: ""
            var text = FeedParser.clean(rawSummary)
            val host = runCatching { URL(url).host }.getOrDefault("")
            if (host.contains("news.google.") ||
                text.startsWith("Article URL:") ||
                text.equals(cleanTitle, ignoreCase = true)
            ) {
                text = ""
            }
            if (text.length > 280) text = text.take(277).trimEnd() + "…"

            return Article(
                id = url,
                title = cleanTitle,
                link = url,
                summary = text,
                imageUrl = imageUrl,
                sourceName = pub ?: source.name,
                sourceId = source.id,
                category = source.category,
                publishedAt = FeedParser.parseDate(date),
            )
        }
    }

    // ---- helpers -------------------------------------------------------------------------

    private val scriptRe = Regex("(?is)<(script|style)[^>]*>.*?</\\1>")
    private val tagRe = Regex("(?s)<[^>]*>")
    private val wsRe = Regex("\\s+")
    private val imgRe = Regex("<img[^>]+src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun clean(html: String): String {
        if (html.isBlank()) return ""
        var s = scriptRe.replace(html, " ")
        s = tagRe.replace(s, " ")
        s = Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString()
        s = s.replace('\uFFFC', ' ').replace('\u00A0', ' ')
        return wsRe.replace(s, " ").trim()
    }

    private fun findImage(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return imgRe.findAll(html)
            .map { it.groupValues[1].replace("&amp;", "&") }
            .firstOrNull { !isJunkImage(it) }
    }

    private fun fixUrl(url: String, base: String): String? {
        val u = url.trim().replace("&amp;", "&")
        return when {
            u.startsWith("http://") || u.startsWith("https://") -> u
            u.startsWith("//") -> "https:$u"
            else -> runCatching { URL(URL(base), u).toString() }.getOrNull()
        }
    }

    private fun looksLikeImage(url: String?): Boolean {
        val path = url?.lowercase(Locale.ROOT)?.substringBefore('?') ?: return false
        return path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
            path.endsWith(".webp") || path.endsWith(".gif") || path.endsWith(".avif")
    }

    private fun isJunkImage(url: String): Boolean {
        val l = url.lowercase(Locale.ROOT)
        return l.contains("feedburner") || l.contains("pixel") || l.contains("1x1") ||
            l.contains("spacer") || l.contains("tracking") || l.contains("doubleclick") ||
            l.contains("gravatar.com") || l.startsWith("data:")
    }

    // ---- dates ---------------------------------------------------------------------------

    private val zonedFormats = listOf(
        "d MMM yyyy HH:mm:ss Z",
        "d MMM yyyy HH:mm:ss zzz",
        "d MMM yyyy HH:mm:ss XXX",
        "d MMM yyyy HH:mm Z",
        "d MMM yyyy HH:mm zzz",
        "d MMMM yyyy HH:mm:ss Z",
        "d MMMM yyyy HH:mm:ss zzz",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private val localFormats = listOf(
        "d MMM yyyy HH:mm:ss",
        "d MMM yyyy HH:mm",
        "yyyy-MM-dd HH:mm:ss",
    ).map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

    private val weekdayRe = Regex("^[A-Za-z]{2,9},?\\s+")

    fun parseDate(raw: String?): Long {
        val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return 0L
        runCatching { return OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        runCatching { return ZonedDateTime.parse(s, DateTimeFormatter.ISO_ZONED_DATE_TIME).toInstant().toEpochMilli() }
        runCatching { return LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }

        val cleaned = weekdayRe.replace(wsRe.replace(s, " "), "")
            .let {
                when {
                    it.endsWith(" UT") -> it.dropLast(3) + " GMT"
                    it.endsWith(" Z") -> it.dropLast(2) + " GMT"
                    else -> it
                }
            }
        for (f in zonedFormats) {
            runCatching { return ZonedDateTime.parse(cleaned, f).toInstant().toEpochMilli() }
        }
        for (f in localFormats) {
            runCatching { return LocalDateTime.parse(cleaned, f).toInstant(ZoneOffset.UTC).toEpochMilli() }
        }
        return 0L
    }
}
