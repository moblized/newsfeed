package com.moblized.newsfeed.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import com.moblized.newsfeed.R
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.Story
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Shared callbacks so every list behaves the same way. */
class StoryHandlers(
    val open: (Article) -> Unit,
    val toggleSave: (Article) -> Unit,
    val share: (Article) -> Unit,
    val openStory: (Story) -> Unit,
)

private enum class Tab(val label: String) {
    TODAY("Today"), BRIEF("Brief"), BLINDSPOT("Blindspot"), SEARCH("Search"), SAVED("Saved")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsApp(vm: NewsViewModel, state: UiState) {
    val context = LocalContext.current
    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    var openStoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val tab = Tab.entries[tabIndex]
    val savedIds = remember(state.saved) { state.saved.mapTo(HashSet()) { it.id } }
    val handlers = remember(vm, state.openInApp, state.declickbait) {
        StoryHandlers(
            open = { a ->
                vm.markRead(a)
                openArticle(context, a.link, state.openInApp)
            },
            toggleSave = { a -> vm.toggleSaved(a) },
            share = { a -> shareArticle(context, state.headline(a), a.link) },
            openStory = { s -> openStoryId = s.id },
        )
    }

    val story = openStoryId?.let { vm.findStory(it, state) }
    if (openStoryId != null && story == null) {
        LaunchedEffect(openStoryId) { openStoryId = null }
    }
    if (openStoryId != null && story != null) {
        BackHandler { openStoryId = null }
        StoryScreen(story, state, vm, savedIds, handlers, onBack = { openStoryId = null })
        return
    }
    if (showSettings) {
        BackHandler { showSettings = false }
        SettingsScreen(state, vm, onBack = { showSettings = false })
        return
    }

    BackHandler(enabled = tab != Tab.TODAY) { tabIndex = 0 }

    val barColors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    val settingsAction: @Composable () -> Unit = {
        IconButton(onClick = { showSettings = true }) {
            Icon(Icons.Filled.Settings, contentDescription = "Sources & settings")
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            when (tab) {
                Tab.TODAY -> TopAppBar(
                    title = {
                        Column {
                            Text("NewsFeed", style = MaterialTheme.typography.headlineMedium)
                            val today = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault()))
                            val updated = relativeTime(state.lastUpdated)
                            Text(
                                text = if (updated.isEmpty()) today else "$today  ·  Updated ${updated.lowercase(Locale.getDefault())}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.refresh() }, enabled = !state.isRefreshing) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                        settingsAction()
                    },
                    colors = barColors,
                )
                Tab.BRIEF -> TopAppBar(
                    title = {
                        Column {
                            Text("Daily Brief", style = MaterialTheme.typography.headlineMedium)
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.getDefault())),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { vm.loadBrief(force = true) }, enabled = !state.briefLoading) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh brief")
                        }
                        settingsAction()
                    },
                    colors = barColors,
                )
                Tab.BLINDSPOT -> TopAppBar(title = { Text("Blindspot") }, actions = { settingsAction() }, colors = barColors)
                Tab.SEARCH -> Unit
                Tab.SAVED -> TopAppBar(title = { Text("Saved stories") }, actions = { settingsAction() }, colors = barColors)
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                Tab.entries.forEachIndexed { i, t ->
                    NavigationBarItem(
                        selected = tabIndex == i,
                        onClick = { tabIndex = i },
                        label = { Text(t.label, maxLines = 1) },
                        icon = {
                            when (t) {
                                Tab.TODAY -> Icon(painterResource(R.drawable.ic_article), contentDescription = null)
                                Tab.BRIEF -> Icon(painterResource(R.drawable.ic_brief), contentDescription = null)
                                Tab.BLINDSPOT -> Icon(painterResource(R.drawable.ic_blindspot), contentDescription = null)
                                Tab.SEARCH -> Icon(Icons.Filled.Search, contentDescription = null)
                                Tab.SAVED -> Icon(
                                    painterResource(if (tabIndex == i) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                                    contentDescription = null,
                                )
                            }
                        },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.TODAY -> TodayScreen(state, vm, padding, handlers)
            Tab.BRIEF -> BriefScreen(state, vm, padding, handlers)
            Tab.BLINDSPOT -> BlindspotScreen(state, vm, padding, handlers)
            Tab.SEARCH -> SearchScreen(state, vm, padding, savedIds, handlers)
            Tab.SAVED -> SavedScreen(state, padding, savedIds, handlers)
        }
    }
}
