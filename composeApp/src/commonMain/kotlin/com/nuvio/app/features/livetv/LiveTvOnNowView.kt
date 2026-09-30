package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_ends_in
import nuvio.composeapp.generated.resources.live_tv_hours_minutes_short
import nuvio.composeapp.generated.resources.live_tv_live_badge
import nuvio.composeapp.generated.resources.live_tv_minutes_short
import nuvio.composeapp.generated.resources.live_tv_no_guide_data
import nuvio.composeapp.generated.resources.live_tv_up_next

/** The "On Now" card view: a big card per channel with what's airing, a progress bar and up-next. */
@Composable
internal fun LiveTvOnNowGrid(
    entries: List<LiveTvOnNowEntry>,
    nowMs: Long,
    favoriteIds: Set<String>,
    tuningChannelId: String?,
    columns: Int,
    contentPadding: PaddingValues,
    onPlay: (LiveTvChannel) -> Unit,
    onFavoriteToggle: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.channel.id }) { entry ->
            LiveTvOnNowCard(
                entry = entry,
                nowMs = nowMs,
                isFavorite = entry.channel.id in favoriteIds,
                isTuning = entry.channel.id == tuningChannelId,
                onPlay = { onPlay(entry.channel) },
                onFavoriteToggle = { onFavoriteToggle(entry.channel) },
            )
        }
    }
}

@Composable
private fun LiveTvOnNowCard(
    entry: LiveTvOnNowEntry,
    nowMs: Long,
    isFavorite: Boolean,
    isTuning: Boolean,
    onPlay: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val current = entry.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(LiveTvNeon.Surface)
            .border(1.dp, if (current != null) LiveTvNeon.GridLineStrong else LiveTvNeon.GridLine, shape)
            .clickable(onClick = onPlay)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            LiveTvChannelLogo(channel = entry.channel, size = 48.dp)
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.channel.name,
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.TextPrimary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (current != null) {
                    Spacer(Modifier.height(3.dp))
                    LiveTvLiveBadge(label = stringResource(Res.string.live_tv_live_badge), compact = true)
                }
            }
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = if (isFavorite) LiveTvNeon.Magenta else LiveTvNeon.TextMuted.copy(alpha = 0.4f),
                modifier = Modifier.size(20.dp).clip(RoundedCornerShape(50)).clickable(onClick = onFavoriteToggle),
            )
        }
        Spacer(Modifier.height(12.dp))
        if (current != null) {
            Text(
                text = current.title,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = LiveTvNeon.TextPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            LiveTvProgressBar(progress = current.progressAt(nowMs), modifier = Modifier.fillMaxWidth().height(4.dp))
            Spacer(Modifier.height(6.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = liveTvTimeRange(current),
                    style = TextStyle(fontSize = 11.sp, color = LiveTvNeon.TextMuted),
                )
                Text(
                    text = stringResource(Res.string.live_tv_ends_in, remainingLabel(current.endMs - nowMs)),
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = LiveTvNeon.CyanSoft),
                )
            }
            entry.next?.let { next ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(Res.string.live_tv_up_next) + " · " + next.title,
                    style = TextStyle(fontSize = 12.sp, color = LiveTvNeon.TextSecondary),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                text = stringResource(Res.string.live_tv_no_guide_data),
                style = TextStyle(fontSize = 13.sp, color = LiveTvNeon.TextMuted),
            )
        }
        if (isTuning) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "…",
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.Cyan),
            )
        }
    }
}

@Composable
private fun remainingLabel(remainingMs: Long): String {
    val (hours, minutes) = liveTvDurationParts(remainingMs.coerceAtLeast(0))
    return if (hours > 0) {
        stringResource(Res.string.live_tv_hours_minutes_short, hours, minutes)
    } else {
        stringResource(Res.string.live_tv_minutes_short, minutes)
    }
}
