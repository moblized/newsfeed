package com.moblized.newsfeed.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moblized.newsfeed.data.Side
import com.moblized.newsfeed.data.Story

private enum class CoverageFilter(val label: String) { ALL("All"), LEFT("Left"), CENTER("Center"), RIGHT("Right"), UNRATED("Not rated") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryScreen(
    story: Story,
    state: UiState,
    vm: NewsViewModel,
    savedIds: Set<String>,
    handlers: StoryHandlers,
    onBack: () -> Unit,
) {
    LaunchedEffect(story.id) { vm.ensureCoverage(story) }
    var filter by rememberSaveable { mutableStateOf(CoverageFilter.ALL) }
    val withBias = remember(story, state.resolver) { story.articles.map { it to state.resolver.bias(it) } }
    val counts = remember(withBias) {
        mapOf(
            CoverageFilter.ALL to withBias.size,
            CoverageFilter.LEFT to withBias.count { it.second?.side == Side.LEFT },
            CoverageFilter.CENTER to withBias.count { it.second?.side == Side.CENTER },
            CoverageFilter.RIGHT to withBias.count { it.second?.side == Side.RIGHT },
            CoverageFilter.UNRATED to withBias.count { it.second == null },
        )
    }
    val shown = remember(withBias, filter) {
        when (filter) {
            CoverageFilter.ALL -> withBias
            CoverageFilter.LEFT -> withBias.filter { it.second?.side == Side.LEFT }
            CoverageFilter.CENTER -> withBias.filter { it.second?.side == Side.CENTER }
            CoverageFilter.RIGHT -> withBias.filter { it.second?.side == Side.RIGHT }
            CoverageFilter.UNRATED -> withBias.filter { it.second == null }
        }
    }
    val title = state.headline(story)
    val summary = remember(story) { com.moblized.newsfeed.data.Briefing.summaryOf(story) }
    val loadingMore = story.id in state.enriching

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(story.category.label) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { handlers.share(story.lead) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            if (story.image != null) {
                item(key = "image") {
                    AsyncImage(
                        model = story.image,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .padding(horizontal = 16.dp)
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    )
                }
            }
            item(key = "head") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text(title, style = MaterialTheme.typography.headlineSmall)
                    if (state.declickbait && title != story.lead.title) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Original: ${story.lead.title}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text(summary, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item(key = "coverage") {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Coverage", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            "${story.outlets} outlets  ·  ${story.left} left  ·  ${story.center} center  ·  ${story.right} right",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(10.dp))
                        BiasBar(story.left, story.center, story.right, tall = true)
                        story.blindspot?.let {
                            Spacer(Modifier.height(10.dp))
                            BlindspotBadge(it)
                        }
                        if (loadingMore) {
                            Spacer(Modifier.height(10.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("Finding more coverage…", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            item(key = "filters") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(CoverageFilter.entries.filter { it == CoverageFilter.ALL || (counts[it] ?: 0) > 0 }) { f ->
                        FilterChip(
                            selected = filter == f,
                            onClick = { filter = f },
                            label = { Text("${f.label} ${counts[f] ?: 0}") },
                        )
                    }
                }
            }
            items(shown, key = { "c-" + it.first.id }) { (a, bias) ->
                StoryRow(
                    article = a,
                    title = state.headline(a),
                    bias = bias,
                    saved = a.id in savedIds,
                    read = a.id in state.readIds,
                    onOpen = { handlers.open(a) },
                    onToggleSave = { handlers.toggleSave(a) },
                    onShare = { handlers.share(a) },
                )
            }
            item(key = "note") {
                Text(
                    "Bias ratings describe each outlet as a whole, not this article.",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}
