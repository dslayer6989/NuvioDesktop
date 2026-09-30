package com.nuvio.app.features.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.features.livetv.LiveTvChannel
import com.nuvio.app.features.livetv.LiveTvChannelLogo
import com.nuvio.app.features.livetv.LiveTvClock
import com.nuvio.app.features.livetv.LiveTvFavoritesGroupId
import com.nuvio.app.features.livetv.LiveTvLiveBadge
import com.nuvio.app.features.livetv.LiveTvNeon
import com.nuvio.app.features.livetv.LiveTvPlayRequest
import com.nuvio.app.features.livetv.LiveTvPlayerSession
import com.nuvio.app.features.livetv.LiveTvProgressBar
import com.nuvio.app.features.livetv.LiveTvRecentGroupId
import com.nuvio.app.features.livetv.LiveTvRepository
import com.nuvio.app.features.livetv.LiveTvTuneOutcome
import com.nuvio.app.features.livetv.LiveTvTuner
import com.nuvio.app.features.livetv.LiveTvUiState
import com.nuvio.app.features.livetv.isLiveTvContentType
import com.nuvio.app.features.livetv.liveSourceLabel
import com.nuvio.app.features.livetv.neighborOf
import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_live_badge
import nuvio.composeapp.generated.resources.live_tv_player_all_sources_failed
import nuvio.composeapp.generated.resources.live_tv_player_channel_down
import nuvio.composeapp.generated.resources.live_tv_player_channel_up
import nuvio.composeapp.generated.resources.live_tv_player_guide
import nuvio.composeapp.generated.resources.live_tv_player_sources
import nuvio.composeapp.generated.resources.live_tv_player_switching_backup
import nuvio.composeapp.generated.resources.live_tv_player_tuning
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** How long a live source may sit on "loading" before we try the next backup. */
internal const val LiveTvStartupTimeoutMs = 20_000L

internal val PlayerScreenRuntime.isLiveTvPlayback: Boolean
    get() = contentType.isLiveTvContentType() || parentMetaType.isLiveTvContentType()

private fun PlayerScreenRuntime.showPlayerNotice(message: String) {
    playerNotificationMessage = message
    playerNotificationToken += 1L
}

/**
 * Called when the active live source fails (playback error or startup timeout).
 * Moves to the next backup source of the same channel; returns false once every source failed.
 */
internal fun PlayerScreenRuntime.tryLiveTvFailover(): Boolean {
    if (!isLiveTvPlayback) return false
    val session = LiveTvPlayerSession.state.value ?: return false
    if (session.channel.id != activeVideoId) return false
    val next = LiveTvPlayerSession.advanceToBackup()
    if (next == null) {
        scope.launch {
            showPlayerNotice(getString(Res.string.live_tv_player_all_sources_failed, session.channel.name))
        }
        return false
    }
    val index = LiveTvPlayerSession.state.value?.streamIndex ?: 0
    errorMessage = null
    scope.launch {
        showPlayerNotice(getString(Res.string.live_tv_player_switching_backup, next.liveSourceLabel(index)))
    }
    applyLiveStream(next)
    return true
}

/** Switches the player to [stream] as a live source (no resume position). */
internal fun PlayerScreenRuntime.applyLiveStream(stream: StreamItem) {
    switchToSource(stream)
    activeInitialPositionMs = 0L
    activeInitialProgressFraction = null
    initialSeekApplied = true
}

/** Re-tunes the player to another channel without leaving full screen. */
internal fun PlayerScreenRuntime.switchLiveChannel(
    channel: LiveTvChannel,
    groupId: String?,
    onTuning: (String?) -> Unit,
) {
    if (channel.id == activeVideoId) return
    scope.launch {
        onTuning(channel.id)
        showPlayerNotice(getString(Res.string.live_tv_player_tuning, channel.name))
        try {
            when (val outcome = LiveTvTuner.tune(LiveTvPlayRequest(channelId = channel.id, groupId = groupId))) {
                is LiveTvTuneOutcome.Ready -> {
                    val result = outcome.result
                    LiveTvPlayerSession.start(result)
                    liveTitleOverride = result.channel.name
                    liveLogoOverride = result.channel.logo
                    activeVideoId = result.channel.id
                    activeEpisodeTitle = result.program?.title
                    activeEpisodeThumbnail = result.program?.thumbnail
                    activePauseDescription = result.program?.description
                    errorMessage = null
                    applyLiveStream(result.streams.first())
                }
                is LiveTvTuneOutcome.Failed -> showPlayerNotice(outcome.message)
            }
        } finally {
            onTuning(null)
        }
    }
}

private fun LiveTvUiState.channelsForSession(groupId: String?): List<LiveTvChannel> {
    val guide = guide ?: return emptyList()
    return when (groupId) {
        LiveTvFavoritesGroupId -> favoriteIds.mapNotNull(guide::channel)
        LiveTvRecentGroupId -> recentIds.mapNotNull(guide::channel)
        else -> guide.channelsFor(groupId)
    }.ifEmpty { guide.lineup }
}

/**
 * Live TV additions to the player: startup watchdog + automatic failover, programme title
 * updates, channel up/down rail, and a slide-out mini guide. Renders nothing for movies/series.
 */
@Composable
internal fun PlayerScreenRuntime.LiveTvPlayerOverlay() {
    if (!isLiveTvPlayback) return
    val session by LiveTvPlayerSession.state.collectAsStateWithLifecycle()
    val liveState by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    var guideOpen by remember { mutableStateOf(false) }
    var tuningChannelId by remember { mutableStateOf<String?>(null) }
    var nowMs by remember { mutableLongStateOf(LiveTvClock.nowEpochMs()) }
    val focusRequester = remember { FocusRequester() }
    val isInPip = rememberIsInPictureInPicture()

    // Startup watchdog: a live source that never finishes loading is treated as failed.
    LaunchedEffect(activeSourceUrl, session?.generation) {
        delay(LiveTvStartupTimeoutMs)
        if (!initialLoadCompleted && errorMessage == null && !playbackSnapshot.isPlaying) {
            tryLiveTvFailover()
        }
    }

    // Keep the "now playing" programme line current as shows change.
    val latestGuide by rememberUpdatedState(liveState.guide)
    val latestChannelId by rememberUpdatedState(session?.channel?.id)
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = LiveTvClock.nowEpochMs()
            val program = latestChannelId?.let { latestGuide?.programAt(it, nowMs) }
            if (program != null && program.title != activeEpisodeTitle) {
                activeEpisodeTitle = program.title
                activePauseDescription = program.description
            }
            delay(30_000L)
        }
    }

    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    val currentSession = session ?: return
    val channels = remember(liveState.guide, liveState.favoriteIds, liveState.recentIds, currentSession.groupId) {
        liveState.channelsForSession(currentSession.groupId)
    }
    fun zap(delta: Int) {
        val target = channels.neighborOf(currentSession.channel.id, delta) ?: return
        switchLiveChannel(target, currentSession.groupId) { tuningChannelId = it }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.PageUp, Key.ChannelUp -> { zap(-1); true }
                    Key.PageDown, Key.ChannelDown -> { zap(1); true }
                    Key.G, Key.Guide -> { guideOpen = !guideOpen; true }
                    Key.Escape -> if (guideOpen) { guideOpen = false; true } else false
                    else -> false
                }
            },
    ) {
        val compact = maxWidth < 600.dp
        if (isInPip) return@BoxWithConstraints

        AnimatedVisibility(
            visible = controlsVisible && !playerControlsLocked && !guideOpen,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = if (compact) 8.dp else 20.dp),
        ) {
            LiveTvChannelRail(
                channel = currentSession.channel,
                tuning = tuningChannelId != null,
                onUp = { zap(-1) },
                onDown = { zap(1) },
                onGuide = { guideOpen = true },
            )
        }

        if (guideOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { guideOpen = false },
                    ),
            )
        }
        AnimatedVisibility(
            visible = guideOpen,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            LiveTvMiniGuide(
                channels = channels,
                currentChannelId = currentSession.channel.id,
                streams = currentSession.streams,
                streamIndex = currentSession.streamIndex,
                liveState = liveState,
                nowMs = nowMs,
                tuningChannelId = tuningChannelId,
                width = if (compact) maxWidth * 0.88f else 400.dp,
                onChannel = { channel ->
                    switchLiveChannel(channel, currentSession.groupId) { tuningChannelId = it }
                },
                onStream = { index ->
                    LiveTvPlayerSession.selectStream(index)?.let(::applyLiveStream)
                },
                onClose = { guideOpen = false },
            )
        }
    }
}

@Composable
private fun LiveTvChannelRail(
    channel: LiveTvChannel,
    tuning: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onGuide: () -> Unit,
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(LiveTvNeon.Panel)
            .border(1.dp, LiveTvNeon.AccentGradient, shape)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveTvRailButton(Icons.Rounded.KeyboardArrowUp, stringResource(Res.string.live_tv_player_channel_up), onUp)
        Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onGuide)) {
            LiveTvChannelLogo(channel = channel, size = 44.dp, selected = tuning)
        }
        if (channel.number > 0) {
            Text(
                text = channel.number.toString(),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Black, color = LiveTvNeon.CyanSoft),
            )
        }
        LiveTvRailButton(Icons.Rounded.KeyboardArrowDown, stringResource(Res.string.live_tv_player_channel_down), onDown)
        LiveTvRailButton(Icons.Rounded.LiveTv, stringResource(Res.string.live_tv_player_guide), onGuide)
    }
}

@Composable
private fun LiveTvRailButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = description, tint = LiveTvNeon.TextPrimary, modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun LiveTvMiniGuide(
    channels: List<LiveTvChannel>,
    currentChannelId: String,
    streams: List<StreamItem>,
    streamIndex: Int,
    liveState: LiveTvUiState,
    nowMs: Long,
    tuningChannelId: String?,
    width: androidx.compose.ui.unit.Dp,
    onChannel: (LiveTvChannel) -> Unit,
    onStream: (Int) -> Unit,
    onClose: () -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentChannelId) {
        val index = channels.indexOfFirst { it.id == currentChannelId }
        if (index > 2) listState.scrollToItem(index - 2)
    }
    Column(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(LiveTvNeon.Background.copy(alpha = 0.94f))
            .border(1.dp, LiveTvNeon.GridLineStrong)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
            .padding(top = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.live_tv_player_guide).uppercase(),
                style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp, color = LiveTvNeon.TextPrimary),
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50)).clickable(onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Close, contentDescription = null, tint = LiveTvNeon.TextSecondary)
            }
        }
        if (streams.size > 1) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(Res.string.live_tv_player_sources).uppercase(),
                style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = LiveTvNeon.TextMuted),
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(streams) { index, stream ->
                    val selected = index == streamIndex
                    val shape = RoundedCornerShape(50)
                    Box(
                        modifier = Modifier
                            .clip(shape)
                            .then(if (selected) Modifier.background(LiveTvNeon.AccentGradient) else Modifier.border(1.dp, LiveTvNeon.GridLineStrong, shape))
                            .clickable { onStream(index) }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stream.liveSourceLabel(index),
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF05040A) else LiveTvNeon.TextSecondary,
                            ),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 10.dp, end = 10.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(channels, key = { it.id }) { channel ->
                val current = channel.id == currentChannelId
                val program = liveState.guide?.programAt(channel.id, nowMs)
                val shape = RoundedCornerShape(14.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(if (current) LiveTvNeon.AiringCell else Color.Transparent)
                        .then(if (current) Modifier.border(1.dp, LiveTvNeon.AccentGradient, shape) else Modifier)
                        .clickable { onChannel(channel) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    LiveTvChannelLogo(channel = channel, size = 44.dp, selected = channel.id == tuningChannelId)
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = (if (channel.number > 0) "${channel.number}  " else "") + channel.name,
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.CyanSoft),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (current) LiveTvLiveBadge(label = stringResource(Res.string.live_tv_live_badge), compact = true)
                        }
                        Text(
                            text = program?.title ?: "—",
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LiveTvNeon.TextPrimary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (program != null) {
                            Spacer(Modifier.height(4.dp))
                            LiveTvProgressBar(progress = program.progressAt(nowMs), modifier = Modifier.fillMaxWidth().height(3.dp))
                        }
                    }
                }
            }
        }
    }
}
