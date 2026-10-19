package com.moblized.newsfeed.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.moblized.newsfeed.data.BriefTopic
import com.moblized.newsfeed.data.Briefing
import com.moblized.newsfeed.data.Story
import com.moblized.newsfeed.data.WeatherReport
import com.moblized.newsfeed.data.WeatherService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefScreen(state: UiState, vm: NewsViewModel, padding: PaddingValues, handlers: StoryHandlers) {
    LaunchedEffect(Unit) { vm.loadBrief() }
    var showPlace by rememberSaveable { mutableStateOf(false) }
    var showTopics by rememberSaveable { mutableStateOf(false) }
    val sections = remember(state.briefTopics) { state.briefTopics.groupBy { it.section } }

    PullToRefreshBox(
        isRefreshing = state.briefLoading,
        onRefresh = { vm.loadBrief(force = true) },
        modifier = Modifier.fillMaxSize().padding(padding),
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "weather") {
                WeatherCard(state, onSetPlace = { showPlace = true })
            }
            sections.forEach { (section, topics) ->
                item(key = "sec-$section") {
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    Text(
                        section,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                topics.forEach { topic ->
                    item(key = "topic-" + topic.id) {
                        TopicBlock(topic, state.brief[topic.id], state, handlers)
                    }
                }
            }
            item(key = "edit") {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    FilledTonalButton(onClick = { showTopics = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Edit brief sections")
                    }
                }
            }
            item(key = "how") {
                Text(
                    "Each topic shows the most widely covered stories from the last day, from Google News and your feeds. " +
                        "Tap a headline to see every outlet's coverage and its bias mix.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
        }
    }

    if (showPlace) PlaceDialog(state, vm, onDismiss = { showPlace = false })
    if (showTopics) {
        TopicsDialog(
            topics = state.briefTopics,
            onSave = {
                vm.setBriefTopics(it)
                showTopics = false
            },
            onReset = {
                vm.setBriefTopics(Briefing.defaultTopics)
                showTopics = false
            },
            onDismiss = { showTopics = false },
        )
    }
}

// ---- weather ---------------------------------------------------------------------------------

@Composable
private fun WeatherCard(state: UiState, onSetPlace: () -> Unit) {
    val context = LocalContext.current
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            val report = state.weather
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${report?.let { WeatherService.emoji(it.condition) } ?: "🌤️"} Weather Forecast",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                if (state.weatherLoading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(4.dp))
            when {
                state.place == null -> {
                    Text(
                        "Set your city to get current conditions and today's outlook.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onSetPlace) { Text("Set my city") }
                }
                report == null -> {
                    Text(
                        state.weatherError ?: "Loading the forecast for ${state.place?.label.orEmpty()}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.weatherError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onSetPlace) { Text("Change city") }
                }
                else -> {
                    Text(report.place.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))
                    BulletLine("Current Conditions", currentLine(report))
                    Spacer(Modifier.height(6.dp))
                    BulletLine("Daily Outlook", outlookLine(report))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("Source: ") }
                                withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append(WeatherService.SOURCE_NAME) }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { openArticle(context, WeatherService.SOURCE_URL, state.openInApp) },
                        )
                        TextButton(onClick = onSetPlace) { Text("Change") }
                    }
                }
            }
        }
    }
}

private fun currentLine(r: WeatherReport): String {
    val wind = if (r.windSpeed > 0) "${r.windSpeed} ${r.windUnit} ${r.windDirection} wind".trim() else "calm wind"
    val feels = r.feelsLike?.takeIf { kotlin.math.abs(it - r.temperature) >= 3 }?.let { " (feels like $it${r.unit})" } ?: ""
    return "${r.temperature}${r.unit}$feels and ${r.condition.lowercase()} with $wind and ${r.humidity}% humidity."
}

private fun outlookLine(r: WeatherReport): String {
    val rain = r.rainChance?.let { ", ${it}% chance of precipitation" } ?: ""
    val wind = if (r.maxWind > 0) ", wind up to ${r.maxWind} ${r.windUnit}" else ""
    return "High of ${r.high}${r.unit} and low of ${r.low}${r.unit}. ${r.dayCondition}$rain$wind."
}

@Composable
private fun BulletLine(label: String, text: String) {
    Text(
        buildAnnotatedString {
            append("•  ")
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label: ") }
            append(text)
        },
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun PlaceDialog(state: UiState, vm: NewsViewModel, onDismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your city") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text("City name") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.searchPlaces(query) }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    Modifier
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    if (state.placeSearching) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(24.dp))
                        }
                    }
                    state.placeResults.forEach { p ->
                        Text(
                            p.label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.setPlace(p)
                                    onDismiss()
                                }
                                .padding(vertical = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Units", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.setFahrenheit(true) }, enabled = !state.fahrenheit) { Text("°F, mph") }
                    TextButton(onClick = { vm.setFahrenheit(false) }, enabled = state.fahrenheit) { Text("°C, km/h") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { vm.searchPlaces(query) }, enabled = query.isNotBlank()) { Text("Search") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

// ---- topics ----------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopicBlock(topic: BriefTopic, stories: List<Story>?, state: UiState, handlers: StoryHandlers) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(topic.title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        when {
            stories == null && state.briefLoading -> Text(
                "Gathering coverage…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            stories.isNullOrEmpty() -> Text(
                "No major coverage in the last day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> stories.orEmpty().forEachIndexed { i, story ->
                val label = if (topic.count > 1) "Key Event ${i + 1}" else "Top story"
                val summary = Briefing.summaryOf(story)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { handlers.openStory(story) }
                        .padding(vertical = 6.dp),
                ) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)) {
                                append("$label: ")
                            }
                            append(state.headline(story))
                        },
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    if (summary.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            summary,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    val outlets = story.articles.distinctBy { it.sourceName }.take(3)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        outlets.forEach { a ->
                            AssistChip(
                                onClick = { handlers.open(a) },
                                label = { Text(a.sourceName, maxLines = 1) },
                            )
                        }
                        if (story.outlets > outlets.size) {
                            AssistChip(
                                onClick = { handlers.openStory(story) },
                                label = { Text("+${story.outlets - outlets.size} more") },
                            )
                        }
                    }
                    if (story.rated > 0) {
                        Spacer(Modifier.height(4.dp))
                        BiasBar(story.left, story.center, story.right)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopicsDialog(
    topics: List<BriefTopic>,
    onSave: (List<BriefTopic>) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val list = remember { mutableStateListOf<BriefTopic>().apply { addAll(topics) } }
    var section by rememberSaveable { mutableStateOf(topics.lastOrNull()?.section ?: Briefing.CONFLICT) }
    var title by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var count by rememberSaveable { mutableStateOf(2) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Brief sections") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                list.forEachIndexed { i, t ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                            Text(t.title, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${t.section}  ·  ${t.count} ${if (t.count == 1) "story" else "stories"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { list.removeAt(i) }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove ${t.title}")
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Text("Add a topic", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = section, onValueChange = { section = it }, singleLine = true,
                    label = { Text("Section (e.g. 🚀 Space & Fundamental Physics)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = title, onValueChange = { title = it }, singleLine = true,
                    label = { Text("Topic name") }, modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("Search words (OR allowed)") }, modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Stories", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    (1..3).forEach { n ->
                        TextButton(onClick = { count = n }, enabled = count != n) { Text("$n") }
                    }
                }
                TextButton(
                    onClick = {
                        list.add(
                            BriefTopic(
                                id = "t-${System.currentTimeMillis()}",
                                section = section.trim().ifEmpty { "Other" },
                                title = title.trim(),
                                query = query.trim(),
                                count = count,
                            )
                        )
                        title = ""
                        query = ""
                    },
                    enabled = title.isNotBlank() && query.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add topic")
                }
                TextButton(onClick = onReset) { Text("Reset to default sections") }
            }
        },
        confirmButton = { Button(onClick = { onSave(list.toList()) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
