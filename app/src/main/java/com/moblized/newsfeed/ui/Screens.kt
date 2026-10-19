package com.moblized.newsfeed.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.Category
import com.moblized.newsfeed.data.Side
import com.moblized.newsfeed.data.Story

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

private fun storyRead(story: Story, readIds: Set<String>) = story.articles.any { it.id in readIds }

// ---- Today -----------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(state: UiState, vm: NewsViewModel, padding: PaddingValues, handlers: StoryHandlers) {
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    val categories = remember(state.sources, state.disabled) {
        state.sources.filter { it.id !in state.disabled }.map { it.category }.distinct().sortedBy { it.ordinal }
    }
    val visible = remember(state.stories, category) {
        val c = category
        if (c == null) state.stories else state.stories.filter { it.category == c }
    }
    val hero = remember(visible) { visible.take(5).firstOrNull { it.image != null } }
    val rest = remember(visible, hero) { if (hero == null) visible else visible.filter { it.id != hero.id } }
    val listState = rememberLazyListState()
    LaunchedEffect(category) { listState.scrollToItem(0) }

    Column(Modifier.fillMaxSize().padding(padding)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(selected = category == null, onClick = { category = null }, label = { Text("All") })
            }
            items(categories) { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
            }
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { vm.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visible.isEmpty() && state.isRefreshing -> MessageState(
                    title = "Fetching today's stories",
                    body = "Pulling the latest headlines from across the spectrum…",
                )
                visible.isEmpty() -> MessageState(
                    title = "No stories yet",
                    body = if (state.failedSources.isNotEmpty()) {
                        "Couldn't reach your sources. Check your connection and pull down to try again."
                    } else {
                        "Pull down to refresh, or switch on more sources in settings."
                    },
                    action = { FilledTonalButton(onClick = { vm.refresh() }) { Text("Try again") } },
                )
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                ) {
                    if (state.failedSources.isNotEmpty()) {
                        item(key = "failed") { FailedBanner(state.failedSources) { vm.dismissFailures() } }
                    }
                    if (hero != null) {
                        item(key = "hero-" + hero.id) {
                            HeroStoryCard(
                                story = hero,
                                title = state.headline(hero),
                                summary = hero.lead.summary,
                                read = storyRead(hero, state.readIds),
                                onOpen = { handlers.openStory(hero) },
                            )
                        }
                    }
                    items(rest, key = { "s-" + it.id }) { s ->
                        StoryCard(
                            story = s,
                            title = state.headline(s),
                            read = storyRead(s, state.readIds),
                            onOpen = { handlers.openStory(s) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FailedBanner(names: List<String>, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp)) {
            Text(
                text = "Couldn't load " + names.take(3).joinToString(", ") +
                    if (names.size > 3) " and ${names.size - 3} more" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", modifier = Modifier.size(18.dp))
            }
        }
    }
}

// ---- Blindspot -------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlindspotScreen(state: UiState, vm: NewsViewModel, padding: PaddingValues, handlers: StoryHandlers) {
    val forLeft = remember(state.stories) { state.stories.filter { it.blindspot == Side.LEFT } }
    val forRight = remember(state.stories) { state.stories.filter { it.blindspot == Side.RIGHT } }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.refresh() },
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "intro") {
                Text(
                    "Stories one side of the political spectrum is barely covering. A story lands here when at least five rated " +
                        "outlets cover it and no more than 15% of them lean the other way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            item(key = "h-left") { SectionHeader("Blindspot for the Left") }
            item(key = "d-left") { BlindspotHint("Covered mostly by right-leaning outlets.") }
            if (forLeft.isEmpty()) {
                item(key = "e-left") { EmptyLine("Nothing right now.") }
            } else {
                items(forLeft, key = { "l-" + it.id }) { s ->
                    StoryCard(s, state.headline(s), storyRead(s, state.readIds)) { handlers.openStory(s) }
                }
            }
            item(key = "h-right") { SectionHeader("Blindspot for the Right") }
            item(key = "d-right") { BlindspotHint("Covered mostly by left-leaning outlets.") }
            if (forRight.isEmpty()) {
                item(key = "e-right") { EmptyLine("Nothing right now.") }
            } else {
                items(forRight, key = { "r-" + it.id }) { s ->
                    StoryCard(s, state.headline(s), storyRead(s, state.readIds)) { handlers.openStory(s) }
                }
            }
            if (state.stories.isNotEmpty() && forLeft.isEmpty() && forRight.isEmpty()) {
                item(key = "note") {
                    EmptyLine("Blindspots show up as more coverage loads. Pull down to refresh in a few minutes.")
                }
            }
        }
    }
}

@Composable
private fun BlindspotHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
fun EmptyLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(16.dp),
    )
}

// ---- Search ----------------------------------------------------------------------------------

private val suggestions = listOf(
    "Artificial intelligence", "Climate", "Space", "Stock market", "Elections",
    "NFL", "NBA", "Movies", "Health", "Gaming", "Electric cars", "Weather",
)

@Composable
private fun ArticleItem(a: Article, state: UiState, savedIds: Set<String>, handlers: StoryHandlers) {
    StoryRow(
        article = a,
        title = state.headline(a),
        bias = state.resolver.bias(a),
        saved = a.id in savedIds,
        read = a.id in state.readIds,
        onOpen = { handlers.open(a) },
        onToggleSave = { handlers.toggleSave(a) },
        onShare = { handlers.share(a) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    state: UiState,
    vm: NewsViewModel,
    padding: PaddingValues,
    savedIds: Set<String>,
    handlers: StoryHandlers,
) {
    var text by rememberSaveable { mutableStateOf(state.searchQuery) }
    val focus = LocalFocusManager.current

    Column(Modifier.fillMaxSize().padding(padding)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            placeholder = { Text("Search any topic, person or place") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        text = ""
                        vm.search("")
                    }) { Icon(Icons.Filled.Clear, contentDescription = "Clear") }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                focus.clearFocus()
                vm.search(text)
            }),
        )

        when {
            state.searchQuery.isEmpty() -> Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    "Popular topics",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { s ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                text = s
                                focus.clearFocus()
                                vm.search(s)
                            },
                            label = { Text(s) },
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Search covers thousands of outlets through Google News, plus the stories already in your feed. " +
                        "Each result shows its outlet's bias rating.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.searchLocal.isNotEmpty()) {
                    item(key = "h-local") { SectionHeader("In your feed") }
                    items(state.searchLocal, key = { "l-" + it.id }) { a -> ArticleItem(a, state, savedIds, handlers) }
                }
                if (state.isSearching) {
                    item(key = "loading") {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                } else {
                    val error = state.searchError
                    if (error != null) {
                        item(key = "error") {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    if (state.searchResults.isNotEmpty()) {
                        item(key = "h-web") { SectionHeader("Across the web") }
                        items(state.searchResults, key = { "w-" + it.id }) { a -> ArticleItem(a, state, savedIds, handlers) }
                    } else if (error == null && state.searchLocal.isEmpty()) {
                        item(key = "empty") { EmptyLine("Nothing found. Try different or fewer words.") }
                    }
                }
            }
        }
    }
}

// ---- Saved -----------------------------------------------------------------------------------

@Composable
fun SavedScreen(state: UiState, padding: PaddingValues, savedIds: Set<String>, handlers: StoryHandlers) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        if (state.saved.isEmpty()) {
            MessageState(
                title = "Nothing saved yet",
                body = "Tap the bookmark on any article to keep it here for later, even offline.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.saved, key = { it.id }) { a -> ArticleItem(a, state, savedIds, handlers) }
            }
        }
    }
}
