package com.moblized.newsfeed.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class Storage(context: Context) {
    private val prefs = context.getSharedPreferences("newsfeed", Context.MODE_PRIVATE)
    private val cacheFile = File(context.filesDir, "feed_cache.json")
    private val savedFile = File(context.filesDir, "saved.json")

    // ---- sources ----
    fun disabledSourceIds(): Set<String> =
        prefs.getStringSet(KEY_DISABLED, null)?.toSet() ?: DefaultSources.offByDefault

    fun setDisabledSourceIds(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_DISABLED, HashSet(ids)).apply()
    }

    fun customSources(): List<Source> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_CUSTOM, null) ?: "[]")
        (0 until arr.length()).map { Source.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    fun setCustomSources(list: List<Source>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_CUSTOM, arr.toString()).apply()
    }

    // ---- settings ----
    fun themeMode(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString(KEY_THEME, null) } ?: ThemeMode.SYSTEM

    fun setThemeMode(mode: ThemeMode) = prefs.edit().putString(KEY_THEME, mode.name).apply()

    fun dynamicColor(): Boolean = prefs.getBoolean(KEY_DYNAMIC, false)
    fun setDynamicColor(on: Boolean) = prefs.edit().putBoolean(KEY_DYNAMIC, on).apply()

    fun openInApp(): Boolean = prefs.getBoolean(KEY_IN_APP, true)
    fun setOpenInApp(on: Boolean) = prefs.edit().putBoolean(KEY_IN_APP, on).apply()

    fun declickbait(): Boolean = prefs.getBoolean(KEY_DECLICKBAIT, true)
    fun setDeclickbait(on: Boolean) = prefs.edit().putBoolean(KEY_DECLICKBAIT, on).apply()

    // ---- bias ----
    fun biasOverrides(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(KEY_BIAS_OVERRIDES, null) ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    fun setBiasOverrides(map: Map<String, String>) {
        val o = JSONObject()
        map.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString(KEY_BIAS_OVERRIDES, o.toString()).apply()
    }

    fun readBias(): Map<Side, Int> = Side.entries.associateWith { prefs.getInt(KEY_READ_BIAS + it.name, 0) }

    fun setReadBias(map: Map<Side, Int>) {
        val e = prefs.edit()
        Side.entries.forEach { e.putInt(KEY_READ_BIAS + it.name, map[it] ?: 0) }
        e.apply()
    }

    // ---- daily brief ----
    fun place(): Place? = runCatching {
        prefs.getString(KEY_PLACE, null)?.let { Place.fromJson(JSONObject(it)) }
    }.getOrNull()

    fun setPlace(place: Place?) {
        prefs.edit().putString(KEY_PLACE, place?.toJson()?.toString()).apply()
    }

    fun fahrenheit(): Boolean = prefs.getBoolean(
        KEY_FAHRENHEIT,
        java.util.Locale.getDefault().country in setOf("US", "LR", "MM", "BS", "BZ", "KY", "PW"),
    )

    fun setFahrenheit(on: Boolean) = prefs.edit().putBoolean(KEY_FAHRENHEIT, on).apply()

    fun briefTopics(): List<BriefTopic> = runCatching {
        val raw = prefs.getString(KEY_TOPICS, null) ?: return Briefing.defaultTopics
        val arr = JSONArray(raw)
        (0 until arr.length()).map { BriefTopic.fromJson(arr.getJSONObject(it)) }
    }.getOrDefault(Briefing.defaultTopics)

    fun setBriefTopics(list: List<BriefTopic>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_TOPICS, arr.toString()).apply()
    }

    // ---- read stories ----
    fun readIds(): LinkedHashSet<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY_READ, null) ?: "[]")
        val set = LinkedHashSet<String>()
        for (i in 0 until arr.length()) set.add(arr.getString(i))
        set
    }.getOrDefault(LinkedHashSet())

    fun setReadIds(ids: Collection<String>) {
        val arr = JSONArray()
        ids.toList().takeLast(1500).forEach { arr.put(it) }
        prefs.edit().putString(KEY_READ, arr.toString()).apply()
    }

    // ---- saved stories ----
    fun savedArticles(): List<Article> = readArticles(savedFile)
    fun setSavedArticles(list: List<Article>) = writeArticles(savedFile, list, 0L)

    // ---- offline cache of the last feed ----
    data class Cache(val articles: List<Article>, val time: Long)

    fun loadCache(): Cache = runCatching {
        val o = JSONObject(cacheFile.readText())
        Cache(parseArticles(o.getJSONArray("articles")), o.optLong("time"))
    }.getOrDefault(Cache(emptyList(), 0L))

    fun saveCache(list: List<Article>, time: Long) = writeArticles(cacheFile, list, time)

    private fun readArticles(file: File): List<Article> = runCatching {
        parseArticles(JSONObject(file.readText()).getJSONArray("articles"))
    }.getOrDefault(emptyList())

    private fun parseArticles(arr: JSONArray): List<Article> =
        (0 until arr.length()).map { Article.fromJson(arr.getJSONObject(it)) }

    private fun writeArticles(file: File, list: List<Article>, time: Long) {
        runCatching {
            val arr = JSONArray()
            list.forEach { arr.put(it.toJson()) }
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(JSONObject().put("time", time).put("articles", arr).toString())
            tmp.renameTo(file)
        }
    }

    private companion object {
        const val KEY_DISABLED = "disabled_sources"
        const val KEY_CUSTOM = "custom_sources"
        const val KEY_THEME = "theme"
        const val KEY_DYNAMIC = "dynamic_color"
        const val KEY_IN_APP = "open_in_app"
        const val KEY_READ = "read_ids"
        const val KEY_DECLICKBAIT = "declickbait"
        const val KEY_BIAS_OVERRIDES = "bias_overrides"
        const val KEY_READ_BIAS = "read_bias_"
        const val KEY_PLACE = "weather_place"
        const val KEY_FAHRENHEIT = "fahrenheit"
        const val KEY_TOPICS = "brief_topics"
    }
}
