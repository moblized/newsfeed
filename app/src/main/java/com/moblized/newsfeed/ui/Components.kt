package com.moblized.newsfeed.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moblized.newsfeed.R
import com.moblized.newsfeed.data.Article
import com.moblized.newsfeed.data.Bias
import com.moblized.newsfeed.data.Side
import com.moblized.newsfeed.data.Story
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// ---- actions ---------------------------------------------------------------------------------

fun openArticle(context: Context, url: String, inApp: Boolean) {
    val uri = Uri.parse(url)
    try {
        if (inApp) {
            CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(context, uri)
        } else {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "No browser found to open this story", Toast.LENGTH_SHORT).show()
    }
}

fun shareArticle(context: Context, title: String, link: String) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, "$title\n$link")
    }
    context.startActivity(Intent.createChooser(send, null))
}

private val dayFormat = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

fun relativeTime(ms: Long, now: Long = System.currentTimeMillis()): String {
    if (ms <= 0L) return ""
    val minutes = (now - ms) / 60_000L
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        minutes < 48 * 60 -> "Yesterday"
        else -> dayFormat.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    }
}

// ---- bias visuals ----------------------------------------------------------------------------

object BiasColors {
    val Left = Color(0xFF2D5FC4)
    val LeanLeft = Color(0xFF6E96DE)
    val Center = Color(0xFFC9C3B8)
    val LeanRight = Color(0xFFE0806F)
    val Right = Color(0xFFC0392B)

    fun of(bias: Bias): Color = when (bias) {
        Bias.LEFT -> Left
        Bias.LEAN_LEFT -> LeanLeft
        Bias.CENTER -> Center
        Bias.LEAN_RIGHT -> LeanRight
        Bias.RIGHT -> Right
    }

    fun of(side: Side): Color = when (side) {
        Side.LEFT -> Left
        Side.CENTER -> Center
        Side.RIGHT -> Right
    }

    fun on(side: Side): Color = if (side == Side.CENTER) Color(0xFF1C1B18) else Color.White
}

/** Ground News style Left / Center / Right coverage bar. */
@Composable
fun BiasBar(left: Int, center: Int, right: Int, modifier: Modifier = Modifier, tall: Boolean = false) {
    val total = left + center + right
    if (total == 0) {
        Text(
            "No rated sources yet",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    } else {
        Row(
            modifier
                .fillMaxWidth()
                .height(if (tall) 30.dp else 20.dp)
                .clip(RoundedCornerShape(if (tall) 8.dp else 6.dp)),
        ) {
            listOf(Side.LEFT to left, Side.CENTER to center, Side.RIGHT to right).forEach { (side, count) ->
                if (count > 0) {
                    val pct = (count * 100f / total).roundToInt()
                    Box(
                        Modifier
                            .weight(count.toFloat())
                            .fillMaxHeight()
                            .background(BiasColors.of(side)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (pct >= 14) {
                            Text(
                                text = if (tall) "${side.label} $pct%" else "${side.letter} $pct%",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = BiasColors.on(side),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BiasChip(bias: Bias?, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val base = if (onClick != null) modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClick) else modifier
    Surface(
        shape = RoundedCornerShape(50),
        color = if (bias != null) BiasColors.of(bias).copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant,
        modifier = base,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            if (bias != null) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(BiasColors.of(bias)),
                )
                Spacer(Modifier.width(5.dp))
            }
            Text(
                text = bias?.label ?: "Not rated",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun BlindspotBadge(side: Side, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)) {
            Icon(
                painterResource(R.drawable.ic_blindspot),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(4.dp))
            Text(
                "Blindspot for the ${side.label}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

// ---- stories (grouped coverage) --------------------------------------------------------------

@Composable
private fun StoryMeta(story: Story) {
    val parts = buildList {
        add(story.category.label.uppercase(Locale.getDefault()))
        relativeTime(story.latest).takeIf { it.isNotEmpty() }?.let { add(it) }
        add(if (story.outlets == 1) "1 source" else "${story.outlets} sources")
    }
    Text(
        text = parts.joinToString("  ·  "),
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
        color = MaterialTheme.colorScheme.primary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun titleColor(read: Boolean): Color =
    if (read) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f) else MaterialTheme.colorScheme.onSurface

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeroStoryCard(story: Story, title: String, summary: String, read: Boolean, onOpen: () -> Unit) {
    Card(
        onClick = onOpen,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        if (story.image != null) {
            AsyncImage(
                model = story.image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        Column(Modifier.padding(16.dp)) {
            StoryMeta(story)
            Spacer(Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = titleColor(read),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            BiasBar(story.left, story.center, story.right)
            story.blindspot?.let {
                Spacer(Modifier.height(8.dp))
                BlindspotBadge(it)
            }
        }
    }
}

@Composable
fun StoryCard(story: Story, title: String, read: Boolean, onOpen: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                StoryMeta(story)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor(read),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (story.image != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = story.image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(88.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        BiasBar(story.left, story.center, story.right)
        story.blindspot?.let {
            Spacer(Modifier.height(8.dp))
            BlindspotBadge(it)
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

// ---- single articles -------------------------------------------------------------------------

@Composable
private fun SourceLine(article: Article, bias: Bias?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = article.sourceName,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        BiasChip(bias)
        val time = relativeTime(article.publishedAt)
        if (time.isNotEmpty()) {
            Text(
                text = "  ·  $time",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StoryActions(saved: Boolean, onToggleSave: () -> Unit, onShare: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onToggleSave, modifier = Modifier.size(40.dp)) {
            Icon(
                painter = painterResource(if (saved) R.drawable.ic_bookmark else R.drawable.ic_bookmark_border),
                contentDescription = if (saved) "Remove from saved" else "Save story",
                tint = if (saved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        IconButton(onClick = onShare, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share story",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/** One outlet's article: used in story coverage lists, search results and saved stories. */
@Composable
fun StoryRow(
    article: Article,
    title: String,
    bias: Bias?,
    saved: Boolean,
    read: Boolean,
    onOpen: () -> Unit,
    onToggleSave: () -> Unit,
    onShare: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(start = 16.dp, end = 8.dp, top = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(end = 8.dp)) {
            Column(Modifier.weight(1f)) {
                SourceLine(article, bias)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = titleColor(read),
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (article.imageUrl != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = article.imageUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(80.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
        StoryActions(saved, onToggleSave, onShare)
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
    )
}

// ---- states ----------------------------------------------------------------------------------

@Composable
fun MessageState(title: String, body: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_article),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(32.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(20.dp))
            action()
        }
    }
}
