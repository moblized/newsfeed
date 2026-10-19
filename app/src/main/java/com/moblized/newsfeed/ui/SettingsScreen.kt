package com.moblized.newsfeed.ui

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.moblized.newsfeed.data.Bias
import com.moblized.newsfeed.data.BiasDirectory
import com.moblized.newsfeed.data.BiasResolver
import com.moblized.newsfeed.data.Category
import com.moblized.newsfeed.data.Side
import com.moblized.newsfeed.data.Source
import com.moblized.newsfeed.data.ThemeMode

private data class RatingTarget(val key: String, val name: String, val current: Bias?, val default: Bias?, val overridden: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(state: UiState, vm: NewsViewModel, onBack: () -> Unit) {
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var rating by remember { mutableStateOf<RatingTarget?>(null) }
    val onCount = state.sources.count { it.id !in state.disabled }

    LaunchedEffect(state.addFeed) {
        if (state.addFeed is AddFeedState.Done) {
            showAdd = false
            vm.resetAddFeed()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Sources & settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 32.dp)) {
            item(key = "mybias") { MyBiasCard(state, onReset = { vm.resetReadBias() }) }

            item(key = "h-reading") { SectionHeader("Reading") }
            item(key = "dearrow") {
                SettingSwitch(
                    "Clean up clickbait headlines",
                    "DeArrow-style: strips \"BREAKING:\", teasers, shouting and emoji, and picks the plainest headline when several outlets cover a story",
                    state.declickbait,
                ) { vm.setDeclickbait(it) }
            }
            item(key = "inapp") {
                SettingSwitch("Open stories in the app", "Turn off to open stories in your browser", state.openInApp) {
                    vm.setOpenInApp(it)
                }
            }

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
                        val resolved = state.resolver.resolve(src)
                        SourceItem(
                            source = src,
                            bias = resolved.bias,
                            enabled = src.id !in state.disabled,
                            onToggle = { vm.setSourceEnabled(src.id, it) },
                            onRate = {
                                rating = RatingTarget(
                                    key = resolved.key,
                                    name = resolved.name,
                                    current = resolved.bias,
                                    default = resolved.defaultBias,
                                    overridden = state.resolver.overrides.containsKey(resolved.key),
                                )
                            },
                            onDelete = if (src.custom) ({ vm.removeSource(src.id) }) else null,
                        )
                    }
                }
            }

            item(key = "about") {
                Text(
                    BiasDirectory.ATTRIBUTION + " Every story comes from free public feeds and Google News. No account, no ads, no tracking.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
            }
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
    rating?.let { target ->
        RatingDialog(
            target = target,
            onPick = { value ->
                vm.setBiasOverride(target.key, value)
                rating = null
            },
            onDismiss = { rating = null },
        )
    }
}

@Composable
private fun MyBiasCard(state: UiState, onReset: () -> Unit) {
    val left = state.readBias[Side.LEFT] ?: 0
    val center = state.readBias[Side.CENTER] ?: 0
    val right = state.readBias[Side.RIGHT] ?: 0
    val total = left + center + right
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Your news bias", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                if (total == 0) {
                    "Open a few stories and this shows which way your reading leans."
                } else {
                    "Based on the $total articles you've opened from rated outlets."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (total > 0) {
                Spacer(Modifier.height(12.dp))
                BiasBar(left, center, right, tall = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onReset) { Text("Reset") }
                }
            }
        }
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
private fun SourceItem(
    source: Source,
    bias: Bias?,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    onRate: () -> Unit,
    onDelete: (() -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                BiasChip(bias, onClick = onRate)
                if (source.custom) {
                    Text(
                        "  ·  added by you",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
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

@Composable
private fun RatingDialog(target: RatingTarget, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    var choice by remember { mutableStateOf(target.current?.name ?: BiasResolver.NOT_RATED) }
    val options = Bias.entries.map { it.name to it.label } + (BiasResolver.NOT_RATED to "Not rated")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bias rating for ${target.name}") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    "Default: ${target.default?.label ?: "not rated"}. Your choice changes coverage bars and Blindspot for every story.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                options.forEach { (value, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { choice = value },
                    ) {
                        RadioButton(selected = choice == value, onClick = { choice = value })
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val defaultValue = target.default?.name ?: BiasResolver.NOT_RATED
                onPick(if (choice == defaultValue) null else choice)
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (target.overridden) TextButton(onClick = { onPick(null) }) { Text("Use default") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
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
