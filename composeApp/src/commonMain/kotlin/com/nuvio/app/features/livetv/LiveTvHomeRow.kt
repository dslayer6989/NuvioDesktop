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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.NuvioAsyncImage
import kotlinx.coroutines.delay
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_home_row_open
import nuvio.composeapp.generated.resources.live_tv_home_row_title
import nuvio.composeapp.generated.resources.live_tv_live_badge
import org.jetbrains.compose.resources.stringResource

private const val LiveTvHomeRowKey = "home_live_tv_now"

/** Adds the "Live Now" row (what's airing on the user's favorite channels) to the Home feed. */
internal fun LazyListScope.liveTvHomeRow(sectionPadding: Dp) {
    item(key = LiveTvHomeRowKey, contentType = "live_tv_now") {
        LiveTvHomeRow(sectionPadding = sectionPadding)
    }
}

@Composable
private fun LiveTvHomeRow(sectionPadding: Dp) {
    LaunchedEffect(Unit) { LiveTvRepository.ensureLoaded() }
    val state by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val tuningChannelId by LiveTvLauncher.tuningChannelId.collectAsStateWithLifecycle()
    var nowMs by remember { mutableLongStateOf(LiveTvClock.nowEpochMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L)
            nowMs = LiveTvClock.nowEpochMs()
        }
    }
    val guide = state.guide ?: return
    val favorites = state.favoriteIds.mapNotNull(guide::channel)
    if (favorites.isEmpty()) return
    val entries = guide.onNow(favorites, nowMs, LiveTvOnNowSort.Channel)

    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = sectionPadding),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(Res.string.live_tv_home_row_title),
                style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = LiveTvNeon.TextPrimary),
            )
            LiveTvLiveBadge(label = stringResource(Res.string.live_tv_live_badge), compact = true)
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.live_tv_home_row_open),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.Cyan),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { LiveTvScreenEvents.requestOpenTab() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = sectionPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(entries, key = { it.channel.id }) { entry ->
                LiveTvHomeCard(
                    entry = entry,
                    nowMs = nowMs,
                    tuning = entry.channel.id == tuningChannelId,
                    onClick = { LiveTvLauncher.play(entry.channel.id, LiveTvFavoritesGroupId) },
                )
            }
        }
    }
}

@Composable
private fun LiveTvHomeCard(
    entry: LiveTvOnNowEntry,
    nowMs: Long,
    tuning: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val program = entry.current
    Box(
        modifier = Modifier
            .width(280.dp)
            .height(158.dp)
            .clip(shape)
            .background(LiveTvNeon.Surface)
            .border(1.dp, if (tuning) LiveTvNeon.AccentGradient else Brush.linearGradient(listOf(LiveTvNeon.GridLineStrong, LiveTvNeon.GridLine)), shape)
            .clickable(onClick = onClick),
    ) {
        val art = program?.thumbnail
        if (art.isNullOrBlank()) {
            Box(modifier = Modifier.fillMaxSize().background(LiveTvNeon.AiringGradient))
        } else {
            NuvioAsyncImage(
                model = art,
                contentDescription = program.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.15f), LiveTvNeon.Background.copy(alpha = 0.95f)))),
        )
        LiveTvChannelLogo(
            channel = entry.channel,
            size = 40.dp,
            modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
        )
        Column(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp),
        ) {
            Text(
                text = entry.channel.name,
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.CyanSoft, letterSpacing = 0.6.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = program?.title ?: entry.channel.name,
                style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (program != null) {
                Spacer(Modifier.height(6.dp))
                LiveTvProgressBar(progress = program.progressAt(nowMs), modifier = Modifier.fillMaxWidth().height(3.dp))
            }
        }
    }
}
