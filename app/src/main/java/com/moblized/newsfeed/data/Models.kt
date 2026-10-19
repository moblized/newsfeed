package com.moblized.newsfeed.data

import org.json.JSONObject

enum class Category(val label: String) {
    TOP("Top stories"),
    WORLD("World"),
    US("U.S."),
    POLITICS("Politics"),
    BUSINESS("Business"),
    TECH("Tech"),
    SCIENCE("Science"),
    HEALTH("Health"),
    SPORTS("Sports"),
    ENTERTAINMENT("Culture"),
    OTHER("Other");

    companion object {
        fun fromName(name: String?): Category = entries.firstOrNull { it.name == name } ?: OTHER
    }
}

data class Source(
    val id: String,
    val name: String,
    val url: String,
    val category: Category,
    val custom: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("category", category.name)
        .put("custom", custom)

    companion object {
        fun fromJson(o: JSONObject) = Source(
            id = o.optString("id"),
            name = o.optString("name"),
            url = o.optString("url"),
            category = Category.fromName(o.optString("category")),
            custom = o.optBoolean("custom", true),
        )
    }
}

data class Article(
    val id: String,
    val title: String,
    val link: String,
    val summary: String,
    val imageUrl: String?,
    val sourceName: String,
    val sourceId: String,
    val category: Category,
    val publishedAt: Long,
    /** Publisher home page (Google News gives this for every item). */
    val publisherUrl: String? = null,
    /** Articles sharing a groupKey (or whose groupKey is another article's id) are the same story. */
    val groupKey: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("title", title)
        .put("link", link)
        .put("summary", summary)
        .put("image", imageUrl ?: "")
        .put("source", sourceName)
        .put("sourceId", sourceId)
        .put("category", category.name)
        .put("time", publishedAt)
        .put("pubUrl", publisherUrl ?: "")
        .put("group", groupKey ?: "")

    companion object {
        fun fromJson(o: JSONObject) = Article(
            id = o.optString("id"),
            title = o.optString("title"),
            link = o.optString("link"),
            summary = o.optString("summary"),
            imageUrl = o.optString("image").ifBlank { null },
            sourceName = o.optString("source"),
            sourceId = o.optString("sourceId"),
            category = Category.fromName(o.optString("category")),
            publishedAt = o.optLong("time"),
            publisherUrl = o.optString("pubUrl").ifBlank { null },
            groupKey = o.optString("group").ifBlank { null },
        )
    }
}

enum class ThemeMode(val label: String) { SYSTEM("System"), LIGHT("Light"), DARK("Dark") }
