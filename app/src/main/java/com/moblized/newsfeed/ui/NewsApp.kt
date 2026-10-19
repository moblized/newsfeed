package com.moblized.newsfeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import com.moblized.newsfeed.R
import com.moblized.newsfeed.data.Article
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared story callbacks so every list behaves the same way. */
class StoryHandlers(
    val open: (Article) -> Unit,
    val toggleSave: (Article) -> Unit,
    val share: (Article) -> Unit,
)

private enum class Tab(val label: String) { FEED("Today"), SEARCH("Search"), SAVED("Saved"), SOURCES("Sources") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(vm: NewsViewModel, state: UiState) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = Tab.entries[tabIndex]
    val savedIds = remember(state.saved) { state.saved.mapTo(HashSet()) { it.id } }
    val handlers = remember(vm, state.openInApp) {
        StoryHandlers(
            open = { a ->
                vm.markRead(a)
                openArticle(context, a.link, state.openInApp)
            },
            toggleSave = { a -> vm.toggleSaved(a) },
            share = { a -> shareArticle(context, a) },
        )
    }

    BackHandler(enabled = tab != Tab.FEED) { tabIndex = 0 }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when (tab) {
                Tab.FEED -> TopAppBar(
                    title = {
                        Column {
                            Text("NewsFeed", style = MaterialTheme.typography.headlineMedium)
                            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
                            val updated = relativeTime(state.lastUpdated)
                            Text(
                                text = if (updated.isEmpty()) today else "$today  ·  Updated ${updated.lowercase(Locale.getDefault())}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }, enabled = !state.isRefreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Tab.SEARCH -> Unit
                Tab.SAVED -> TopAppBar(
                    title = { Text("Saved stories") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
                Tab.SOURCES -> TopAppBar(
                    title = { Text("Sources & settings") },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        label = { Text(t.label) },
                        icon = {
                            when (t) {
                                Tab.FEED -> Icon(painterResource(R.drawable.ic_article), contentDescription = null)
                                Tab.SEARCH -> Icon(Icons.Filled.Search, contentDescription = null)
                                Tab.SAVED -> Icon(
                                    painterResource(if (tabIndex == i) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                                    contentDescription = null,
                                )
                                Tab.SOURCES -> Icon(painterResource(R.drawable.ic_rss), contentDescription = null)
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.FEED -> FeedScreen(state, vm, padding, savedIds, handlers)
            Tab.SEARCH -> SearchScreen(state, vm, padding, savedIds, handlers)
            Tab.SAVED -> SavedScreen(state, padding, handlers)
            Tab.SOURCES -> SourcesScreen(state, vm, padding)
        }
    }
}
