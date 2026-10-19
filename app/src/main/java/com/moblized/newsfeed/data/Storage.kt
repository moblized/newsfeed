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
    }
}
