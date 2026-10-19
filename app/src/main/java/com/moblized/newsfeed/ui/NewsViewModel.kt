package com.moblized.newsfeed.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.BiasResolver
import com.moblized.newsfeed.data.BriefTopic
import com.moblized.newsfeed.data.Briefing
import com.moblized.newsfeed.data.Category
import com.moblized.newsfeed.data.DefaultSources
import com.moblized.newsfeed.data.FeedRepository
import com.moblized.newsfeed.data.Headlines
import com.moblized.newsfeed.data.Place
import com.moblized.newsfeed.data.Side
import com.moblized.newsfeed.data.Source
import com.moblized.newsfeed.data.Storage
import com.moblized.newsfeed.data.Story
import com.moblized.newsfeed.data.StoryBuilder
import com.moblized.newsfeed.data.ThemeMode
import com.moblized.newsfeed.data.WeatherReport
import com.moblized.newsfeed.data.WeatherService
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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
    val stories: List<Story> = emptyList(),
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
    val declickbait: Boolean = true,
    // bias
    val resolver: BiasResolver = BiasResolver(emptyMap()),
    val readBias: Map<Side, Int> = emptyMap(),
    val enriching: Set<String> = emptySet(),
    // search
    val searchQuery: String = "",
    val searchLocal: List<Article> = emptyList(),
    val searchResults: List<Article> = emptyList(),
    val isSearching: Boolean = false,
    val searchError: String? = null,
    val addFeed: AddFeedState = AddFeedState.Idle,
    // daily brief
    val place: Place? = null,
    val fahrenheit: Boolean = true,
    val weather: WeatherReport? = null,
    val weatherLoading: Boolean = false,
    val weatherError: String? = null,
    val placeResults: List<Place> = emptyList(),
    val placeSearching: Boolean = false,
    val briefTopics: List<BriefTopic> = Briefing.defaultTopics,
    val brief: Map<String, List<Story>> = emptyMap(),
    val briefLoading: Boolean = false,
    val briefUpdated: Long = 0L,
) {
    /** Headline to show for one article, honoring DeArrow mode. */
    fun headline(article: Article): String = if (declickbait) Headlines.clean(article.title) else article.title

    fun headline(story: Story): String = if (declickbait) Headlines.clean(story.consensusTitle) else story.lead.title
}

class NewsViewModel(app: Application) : AndroidViewModel(app) {

    private val storage = Storage(app)
    private val _state = MutableStateFlow(
        UiState(
            themeMode = storage.themeMode(),
            dynamicColor = storage.dynamicColor(),
            openInApp = storage.openInApp(),
            declickbait = storage.declickbait(),
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var searchJob: Job? = null
    private var refreshJob: Job? = null
    private var enrichJob: Job? = null
    private var briefJob: Job? = null
    private var placeJob: Job? = null
    private val readIds = LinkedHashSet<String>()
    private val enrichedAnchors = HashSet<String>()

    init {
        viewModelScope.launch {
            val loadedState = withContext(Dispatchers.IO) {
                val cache = storage.loadCache()
                readIds.addAll(storage.readIds())
                val resolver = BiasResolver(storage.biasOverrides())
                _state.value.copy(
                    loaded = true,
                    articles = cache.articles,
                    stories = StoryBuilder.build(cache.articles, resolver),
                    lastUpdated = cache.time,
                    saved = storage.savedArticles(),
                    readIds = readIds.toSet(),
                    sources = DefaultSources.all + storage.customSources(),
                    disabled = storage.disabledSourceIds(),
                    resolver = resolver,
                    readBias = storage.readBias(),
                    place = storage.place(),
                    fahrenheit = storage.fahrenheit(),
                    briefTopics = storage.briefTopics(),
                )
            }
            _state.value = loadedState
            val stale = System.currentTimeMillis() - loadedState.lastUpdated > 10 * 60_000L
            if (loadedState.articles.isEmpty() || stale) refresh() else enrichTop()
        }
    }

    // ---- feed ----

    fun refresh() {
        if (_state.value.isRefreshing) return
        _state.update { it.copy(isRefreshing = true) }
        enrichJob?.cancel()
        refreshJob = viewModelScope.launch {
            val snapshot = _state.value
            val enabled = snapshot.sources.filter { it.id !in snapshot.disabled }
            val results = withContext(Dispatchers.IO) {
                val gate = Semaphore(8)
                coroutineScope {
                    enabled.map { src ->
                        async { gate.withPermit { src to runCatching { FeedRepository.load(src) } } }
                    }.awaitAll()
                }
            }
            val failed = results.filter { it.second.isFailure }.map { it.first.name }
            val fresh = results.flatMap { it.second.getOrNull().orEmpty() }
            val now = System.currentTimeMillis()
            val merged = if (fresh.isEmpty()) {
                _state.value.articles
            } else {
                // Keep extra coverage found earlier for stories that are still current.
                val carried = _state.value.articles.filter {
                    it.sourceId == StoryBuilder.ENRICH_SOURCE && now - it.publishedAt < 36 * 3_600_000L
                }
                (fresh + carried)
                    .distinctBy { it.link }
                    .sortedByDescending { it.publishedAt }
                    .take(1800)
            }
            val stories = withContext(Dispatchers.Default) { StoryBuilder.build(merged, _state.value.resolver, now) }
            if (fresh.isNotEmpty()) withContext(Dispatchers.IO) { storage.saveCache(merged, now) }
            _state.update {
                it.copy(
                    articles = merged,
                    stories = stories,
                    lastUpdated = if (fresh.isNotEmpty()) now else it.lastUpdated,
                    failedSources = failed,
                    isRefreshing = false,
                )
            }
            enrichTop()
        }
    }

    fun dismissFailures() = _state.update { it.copy(failedSources = emptyList()) }

    // ---- extra coverage (fills the bias bar the way Ground News does) ----

    private fun isEnriched(story: Story) = story.articles.any { it.id in enrichedAnchors }

    private fun enrichTop() {
        enrichJob?.cancel()
        enrichJob = viewModelScope.launch {
            val skip = setOf(Category.SPORTS, Category.ENTERTAINMENT)
            val targets = _state.value.stories
                .filter { it.category !in skip && !isEnriched(it) }
                .take(24)
            if (targets.isEmpty()) return@launch
            val found = withContext(Dispatchers.IO) {
                val gate = Semaphore(4)
                coroutineScope {
                    targets.map { s -> async { gate.withPermit { findCoverage(s) } } }.awaitAll().flatten()
                }
            }
            targets.forEach { enrichedAnchors.add(it.lead.id) }
            mergeCoverage(found)
        }
    }

    /** Called when a story is opened, so its coverage is as complete as possible. */
    fun ensureCoverage(story: Story) {
        if (isEnriched(story) || story.id in _state.value.enriching) return
        _state.update { it.copy(enriching = it.enriching + story.id) }
        viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { findCoverage(story) }
            enrichedAnchors.add(story.lead.id)
            mergeCoverage(found)
            _state.update { it.copy(enriching = it.enriching - story.id) }
        }
    }

    private fun findCoverage(story: Story): List<Article> {
        val query = StoryBuilder.searchQuery(story.lead.title)
        if (query.split(' ').size < 2) return emptyList()
        val results = runCatching { FeedRepository.search("$query when:2d") }.getOrDefault(emptyList())
        val tokens = StoryBuilder.storyTokens(story)
        val known = story.articles.mapTo(HashSet()) { it.link }
        return results
            .filter { it.link !in known && StoryBuilder.matches(tokens, it.title) }
            .map { it.copy(sourceId = StoryBuilder.ENRICH_SOURCE, groupKey = story.lead.id, category = story.category) }
    }

    private suspend fun mergeCoverage(found: List<Article>) {
        if (found.isEmpty()) return
        val s = _state.value
        val merged = (s.articles + found).distinctBy { it.link }
        val stories = withContext(Dispatchers.Default) { StoryBuilder.build(merged, s.resolver) }
        _state.update { it.copy(articles = merged, stories = stories) }
        withContext(Dispatchers.IO) { storage.saveCache(merged, s.lastUpdated) }
    }

    /** Finds a story by id across the feed and the daily brief, following it through regrouping. */
    fun findStory(id: String, state: UiState = _state.value): Story? {
        state.stories.firstOrNull { it.id == id }?.let { return it }
        state.brief.values.flatten().firstOrNull { it.id == id }?.let { return it }
        return state.stories.firstOrNull { s -> s.articles.any { it.id == id } }
            ?: state.brief.values.flatten().firstOrNull { s -> s.articles.any { it.id == id } }
    }

    // ---- reading ----

    fun markRead(article: Article) {
        if (!readIds.add(article.id)) return
        val snapshot = readIds.toSet()
        val side = _state.value.resolver.bias(article)?.side
        val readBias = if (side != null) {
            _state.value.readBias.toMutableMap().apply { this[side] = (this[side] ?: 0) + 1 }
        } else {
            _state.value.readBias
        }
        _state.update { it.copy(readIds = snapshot, readBias = readBias) }
        viewModelScope.launch(Dispatchers.IO) {
            storage.setReadIds(snapshot)
            if (side != null) storage.setReadBias(readBias)
        }
    }

    fun resetReadBias() {
        storage.setReadBias(emptyMap())
        _state.update { it.copy(readBias = emptyMap()) }
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

    // ---- bias ratings ----

    fun setBiasOverride(key: String, value: String?) {
        val next = _state.value.resolver.overrides.toMutableMap()
        if (value == null) next.remove(key) else next[key] = value
        storage.setBiasOverrides(next)
        val resolver = BiasResolver(next)
        _state.update { it.copy(resolver = resolver) }
        viewModelScope.launch {
            val s = _state.value
            val stories = withContext(Dispatchers.Default) { StoryBuilder.build(s.articles, resolver) }
            _state.update { it.copy(stories = stories) }
        }
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
        }.distinctBy { it.id }.take(10)
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

    // ---- daily brief ----

    fun loadBrief(force: Boolean = false) {
        val s = _state.value
        val fresh = System.currentTimeMillis() - s.briefUpdated < 30 * 60_000L
        if (s.place != null && (force || s.weather == null || System.currentTimeMillis() - (s.weather.fetchedAt) > 30 * 60_000L)) {
            loadWeather()
        }
        if (s.briefLoading || (fresh && !force && s.brief.isNotEmpty())) return
        _state.update { it.copy(briefLoading = true) }
        briefJob = viewModelScope.launch {
            val topics = _state.value.briefTopics
            val local = _state.value.articles
            val resolver = _state.value.resolver
            val result = withContext(Dispatchers.IO) {
                coroutineScope {
                    topics.map { topic ->
                        async {
                            val web = runCatching { FeedRepository.search("${topic.query} when:1d") }.getOrDefault(emptyList())
                            val wider = if (web.size < 6) {
                                runCatching { FeedRepository.search("${topic.query} when:3d") }.getOrDefault(emptyList())
                            } else {
                                emptyList()
                            }
                            val mine = local.filter { Briefing.matches(topic.query, it) }
                            topic.id to Briefing.topStories(topic, mine + web + wider, resolver)
                        }
                    }.awaitAll().toMap()
                }
            }
            _state.update { it.copy(brief = result, briefLoading = false, briefUpdated = System.currentTimeMillis()) }
        }
    }

    private fun loadWeather() {
        val place = _state.value.place ?: return
        val fahrenheit = _state.value.fahrenheit
        _state.update { it.copy(weatherLoading = true, weatherError = null) }
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { runCatching { WeatherService.forecast(place, fahrenheit) } }
            _state.update {
                it.copy(
                    weather = report.getOrNull() ?: it.weather,
                    weatherLoading = false,
                    weatherError = if (report.isFailure) "Couldn't load the forecast." else null,
                )
            }
        }
    }

    fun searchPlaces(query: String) {
        placeJob?.cancel()
        if (query.isBlank()) {
            _state.update { it.copy(placeResults = emptyList(), placeSearching = false) }
            return
        }
        _state.update { it.copy(placeSearching = true) }
        placeJob = viewModelScope.launch {
            val found = withContext(Dispatchers.IO) { runCatching { WeatherService.searchPlaces(query) } }
            _state.update { it.copy(placeResults = found.getOrDefault(emptyList()), placeSearching = false) }
        }
    }

    fun setPlace(place: Place) {
        storage.setPlace(place)
        _state.update { it.copy(place = place, placeResults = emptyList(), weather = null) }
        loadWeather()
    }

    fun setFahrenheit(on: Boolean) {
        storage.setFahrenheit(on)
        _state.update { it.copy(fahrenheit = on, weather = null) }
        loadWeather()
    }

    fun setBriefTopics(topics: List<BriefTopic>) {
        storage.setBriefTopics(topics)
        _state.update { it.copy(briefTopics = topics, briefUpdated = 0L) }
        loadBrief(force = true)
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

    fun setDeclickbait(on: Boolean) {
        storage.setDeclickbait(on)
        _state.update { it.copy(declickbait = on) }
    }
}
