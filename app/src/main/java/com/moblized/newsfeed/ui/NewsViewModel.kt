package com.moblized.newsfeed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.Category
import com.moblized.newsfeed.data.DefaultSources
import com.moblized.newsfeed.data.FeedRepository
import com.moblized.newsfeed.data.Source
import com.moblized.newsfeed.data.Storage
import com.moblized.newsfeed.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AddFeedState {
    data object Idle : AddFeedState
    data object Checking : AddFeedState
    data object Done : AddFeedState
    data class Error(val message: String) : AddFeedState
}

data class UiState(
    val loaded: Boolean = false,
    val articles: List<Article> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long = 0L,
    val failedSources: List<String> = emptyList(),
    val saved: List<Article> = emptyList(),
    val readIds: Set<String> = emptySet(),
    val sources: List<Source> = DefaultSources.all,
    val disabled: Set<String> = DefaultSources.offByDefault,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val openInApp: Boolean = true,
    val searchQuery: String = "",
    val searchLocal: List<Article> = emptyList(),
    val searchResults: List<Article> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val addFeed: AddFeedState = AddFeedState.Idle,
)

class NewsViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = Storage(app)
    private val _state = MutableStateFlow(
        UiState(
            themeMode = storage.themeMode(),
            dynamicColor = storage.dynamicColor(),
            openInApp = storage.openInApp(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private val readIds = LinkedHashSet<String>()

    init {
        viewModelScope.launch {
            val loadedState = withContext(Dispatchers.IO) {
                val cache = storage.loadCache()
                readIds.addAll(storage.readIds())
                _state.value.copy(
                    loaded = true,
                    articles = cache.articles,
                    lastUpdated = cache.time,
                    saved = storage.savedArticles(),
                    readIds = readIds.toSet(),
                    sources = DefaultSources.all + storage.customSources(),
                    disabled = storage.disabledSourceIds(),
                )
            }
            _state.value = loadedState
            val stale = System.currentTimeMillis() - loadedState.lastUpdated > 10 * 60_000L
            if (loadedState.articles.isEmpty() || stale) refresh()
        }
    }

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        viewModelScope.launch {
            val snapshot = _state.value
            val enabled = snapshot.sources.filter { it.id !in snapshot.disabled }
            val results = withContext(Dispatchers.IO) {
                coroutineScope {
                    enabled.map { src -> async { src to runCatching { FeedRepository.load(src) } } }.awaitAll()
                }
            }
            val failed = results.filter { it.second.isFailure }.map { it.first.name }
            val merged = results
                .flatMap { it.second.getOrNull().orEmpty() }
                .distinctBy { it.link }
                .sortedByDescending { it.publishedAt }
                .take(600)
            val now = System.currentTimeMillis()
            if (merged.isNotEmpty()) {
                withContext(Dispatchers.IO) { storage.saveCache(merged, now) }
            }
            _state.update {
                it.copy(
                    articles = if (merged.isNotEmpty()) merged else it.articles,
                    lastUpdated = if (merged.isNotEmpty()) now else it.lastUpdated,
                    failedSources = failed,
                    isRefreshing = false,
                )
            }
        }
    }

    fun dismissFailures() = _state.update { it.copy(failedSources = emptyList()) }

    // ---- reading ----
    fun markRead(article: Article) {
        if (!readIds.add(article.id)) return
        val snapshot = readIds.toSet()
        _state.update { it.copy(readIds = snapshot) }
        viewModelScope.launch(Dispatchers.IO) { storage.setReadIds(snapshot) }
    }

    fun toggleSaved(article: Article) {
        val current = _state.value.saved
        val next = if (current.any { it.id == article.id }) {
            current.filterNot { it.id == article.id }
        } else {
            listOf(article) + current
        }
        _state.update { it.copy(saved = next) }
        viewModelScope.launch(Dispatchers.IO) { storage.setSavedArticles(next) }
    }

    // ---- search ----
    fun search(query: String) {
        val q = query.trim()
        searchJob?.cancel()
        if (q.isEmpty()) {
            _state.update {
                it.copy(searchQuery = "", searchLocal = emptyList(), searchResults = emptyList(), isSearching = false, searchError = null)
            }
            return
        }
        val local = _state.value.articles.filter {
            it.title.contains(q, ignoreCase = true) || it.summary.contains(q, ignoreCase = true)
        }.take(10)
        _state.update { it.copy(searchQuery = q, searchLocal = local, isSearching = true, searchError = null) }
        searchJob = viewModelScope.launch {
            val web = withContext(Dispatchers.IO) { runCatching { FeedRepository.search(q) } }
            val localIds = local.mapTo(HashSet()) { it.id }
            _state.update {
                it.copy(
                    searchResults = web.getOrDefault(emptyList()).filterNot { a -> a.id in localIds }.distinctBy { a -> a.id },
                    isSearching = false,
                    searchError = if (web.isFailure) "Couldn't reach Google News. Check your connection." else null,
                )
            }
        }
    }

    // ---- sources ----
    fun setSourceEnabled(id: String, enabled: Boolean) {
        val next = _state.value.disabled.toMutableSet().apply { if (enabled) remove(id) else add(id) }
        storage.setDisabledSourceIds(next)
        _state.update { it.copy(disabled = next) }
    }

    fun addFeed(name: String, url: String, category: Category) {
        if (url.isBlank()) return
        _state.update { it.copy(addFeed = AddFeedState.Checking) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { FeedRepository.discover(url) } }
            val found = result.getOrNull()
            if (found == null) {
                _state.update { it.copy(addFeed = AddFeedState.Error("Couldn't find a news feed at that address.")) }
                return@launch
            }
            val current = _state.value.sources
            if (current.any { it.url.equals(found.url, ignoreCase = true) }) {
                _state.update { it.copy(addFeed = AddFeedState.Error("That feed is already in your sources.")) }
                return@launch
            }
            val host = runCatching { java.net.URL(found.url).host.removePrefix("www.") }.getOrDefault("Custom feed")
            val source = Source(
                id = "custom-${System.currentTimeMillis()}",
                name = name.trim().ifEmpty { found.title ?: host },
                url = found.url,
                category = category,
                custom = true,
            )
            val custom = current.filter { it.custom } + source
            storage.setCustomSources(custom)
            _state.update { it.copy(sources = DefaultSources.all + custom, addFeed = AddFeedState.Done) }
            refresh()
        }
    }

    fun resetAddFeed() = _state.update { it.copy(addFeed = AddFeedState.Idle) }

    fun removeSource(id: String) {
        val custom = _state.value.sources.filter { it.custom && it.id != id }
        storage.setCustomSources(custom)
        val disabled = _state.value.disabled - id
        storage.setDisabledSourceIds(disabled)
        _state.update { s ->
            s.copy(
                sources = DefaultSources.all + custom,
                disabled = disabled,
                articles = s.articles.filterNot { it.sourceId == id },
            )
        }
    }

    // ---- settings ----
    fun setThemeMode(mode: ThemeMode) {
        storage.setThemeMode(mode)
        _state.update { it.copy(themeMode = mode) }
    }

    fun setDynamicColor(on: Boolean) {
        storage.setDynamicColor(on)
        _state.update { it.copy(dynamicColor = on) }
    }

    fun setOpenInApp(on: Boolean) {
        storage.setOpenInApp(on)
        _state.update { it.copy(openInApp = on) }
    }
}
