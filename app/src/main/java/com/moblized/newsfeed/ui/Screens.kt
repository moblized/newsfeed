package com.moblized.newsfeed.ui

import android.os.Build
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.Category
import com.moblized.newsfeed.data.Source
import com.moblized.newsfeed.data.ThemeMode

// ---- Today -----------------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedScreen(
    state: UiState,
    vm: NewsViewModel,
    padding: PaddingValues,
    savedIds: Set<String>,
    handlers: StoryHandlers,
) {
    var category by rememberSaveable { mutableStateOf<Category?>(null) }
    val categories = remember(state.sources, state.disabled) {
        state.sources.filter { it.id !in state.disabled }.map { it.category }.distinct().sortedBy { it.ordinal }
    }
    val visible = remember(state.articles, category) {
        val c = category
        if (c == null) state.articles else state.articles.filter { it.category == c }
    }
    val hero = remember(visible) { visible.take(6).firstOrNull { it.imageUrl != null } }
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
                    body = "Pulling the latest headlines from your sources…",
                )
                visible.isEmpty() -> MessageState(
                    title = "No stories yet",
                    body = if (state.failedSources.isNotEmpty()) {
                        "Couldn't reach your sources. Check your connection and pull down to try again."
                    } else {
                        "Pull down to refresh, or switch on more sources in the Sources tab."
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
                            HeroStory(
                                article = hero,
                                saved = hero.id in savedIds,
                                read = hero.id in state.readIds,
                                onOpen = { handlers.open(hero) },
                                onToggleSave = { handlers.toggleSave(hero) },
                                onShare = { handlers.share(hero) },
                            )
                        }
                    }
                    items(rest, key = { it.id }) { a ->
                        StoryRow(
                            article = a,
                            saved = a.id in savedIds,
                            read = a.id in state.readIds,
                            onOpen = { handlers.open(a) },
                            onToggleSave = { handlers.toggleSave(a) },
                            onShare = { handlers.share(a) },
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

// ---- Search ----------------------------------------------------------------------------------

private val suggestions = listOf(
    "Artificial intelligence", "Climate", "Space", "Stock market", "Elections",
    "NFL", "NBA", "Movies", "Health", "Gaming", "Electric cars", "Weather",
)

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
                    "Search covers thousands of outlets through Google News, plus the stories already in your feed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                if (state.searchLocal.isNotEmpty()) {
                    item(key = "h-local") { SectionHeader("In your feed") }
                    items(state.searchLocal, key = { "l-" + it.id }) { a ->
                        StoryRow(a, a.id in savedIds, a.id in state.readIds, { handlers.open(a) }, { handlers.toggleSave(a) }, { handlers.share(a) })
                    }
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
                        items(state.searchResults, key = { "w-" + it.id }) { a ->
                            StoryRow(a, a.id in savedIds, a.id in state.readIds, { handlers.open(a) }, { handlers.toggleSave(a) }, { handlers.share(a) })
                        }
                    } else if (error == null && state.searchLocal.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                "Nothing found. Try different or fewer words.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

// ---- Saved -----------------------------------------------------------------------------------

@Composable
fun SavedScreen(state: UiState, padding: PaddingValues, handlers: StoryHandlers) {
    Box(Modifier.fillMaxSize().padding(padding)) {
        if (state.saved.isEmpty()) {
            MessageState(
                title = "Nothing saved yet",
                body = "Tap the bookmark on any story to keep it here for later, even offline.",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                items(state.saved, key = { it.id }) { a ->
                    StoryRow(a, true, a.id in state.readIds, { handlers.open(a) }, { handlers.toggleSave(a) }, { handlers.share(a) })
                }
            }
        }
    }
}

// ---- Sources & settings ----------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(state: UiState, vm: NewsViewModel, padding: PaddingValues) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    val onCount = state.sources.count { it.id !in state.disabled }

    LaunchedEffect(state.addFeed) {
        if (state.addFeed is AddFeedState.Done) {
            showAdd = false
            vm.resetAddFeed()
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
        item(key = "h-look") { SectionHeader("Appearance") }
        item(key = "theme") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = state.themeMode == mode,
                            onClick = { vm.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(index = i, count = ThemeMode.entries.size),
                        ) { Text(mode.label) }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item(key = "dynamic") {
                SettingSwitch("Wallpaper colors", "Use Material You colors instead of the newsprint look", state.dynamicColor) {
                    vm.setDynamicColor(it)
                }
            }
        }
        item(key = "inapp") {
            SettingSwitch("Open stories in the app", "Turn off to open stories in your browser", state.openInApp) {
                vm.setOpenInApp(it)
            }
        }

        item(key = "h-sources") { SectionHeader("Sources  ·  $onCount on") }
        item(key = "add") {
            FilledTonalButton(
                onClick = { showAdd = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Add your own feed")
            }
        }

        Category.entries.forEach { cat ->
            val list = state.sources.filter { it.category == cat }
            if (list.isNotEmpty()) {
                item(key = "cat-" + cat.name) {
                    Text(
                        cat.label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 2.dp),
                    )
                }
                items(list, key = { "src-" + it.id }) { src ->
                    SourceItem(
                        source = src,
                        enabled = src.id !in state.disabled,
                        onToggle = { vm.setSourceEnabled(src.id, it) },
                        onDelete = if (src.custom) ({ vm.removeSource(src.id) }) else null,
                    )
                }
            }
        }

        item(key = "about") {
            Text(
                "Every story comes straight from each publisher's free public feed. No account, no ads, no tracking.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
    }

    if (showAdd) {
        AddFeedDialog(
            addState = state.addFeed,
            onAdd = { name, url, cat -> vm.addFeed(name, url, cat) },
            onDismiss = {
                showAdd = false
                vm.resetAddFeed()
            },
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onChange(!checked) },
    )
}

@Composable
private fun SourceItem(source: Source, enabled: Boolean, onToggle: (Boolean) -> Unit, onDelete: (() -> Unit)?) {
    val host = remember(source.url) {
        runCatching { java.net.URL(source.url).host.removePrefix("www.") }.getOrDefault(source.url)
    }
    ListItem(
        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(if (source.custom) "$host  ·  added by you" else host, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Remove ${source.name}")
                    }
                }
                Switch(checked = enabled, onCheckedChange = onToggle)
            }
        },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
        modifier = Modifier.clickable { onToggle(!enabled) },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddFeedDialog(
    addState: AddFeedState,
    onAdd: (String, String, Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(Category.OTHER) }
    val checking = addState is AddFeedState.Checking

    AlertDialog(
        onDismissRequest = { if (!checking) onDismiss() },
        title = { Text("Add a feed") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Paste a website or RSS link. NewsFeed will find the feed for you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Website or feed link") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Category.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                if (addState is AddFeedState.Error) {
                    Text(addState.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = { onAdd(name, url, category) }, enabled = url.isNotBlank() && !checking) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !checking) { Text("Cancel") }
        },
    )
}
