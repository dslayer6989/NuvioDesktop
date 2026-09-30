package com.nuvio.app.features.livetv

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LiveTv
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nuvio.app.core.ui.DialogButton
import com.nuvio.app.core.ui.DialogButtonStyle
import com.nuvio.app.core.ui.DialogButtons
import com.nuvio.app.core.ui.DialogSurface
import com.nuvio.app.core.ui.LocalScreenActive
import com.nuvio.app.core.ui.NuvioAsyncImage
import com.nuvio.app.core.ui.NuvioInputField
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import com.nuvio.app.core.ui.NuvioPrimaryButton
import com.nuvio.app.core.ui.nuvioPlatformExtraTopPadding
import com.nuvio.app.core.ui.nuvioSafeBottomPadding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_add_favorite
import nuvio.composeapp.generated.resources.live_tv_cancel
import nuvio.composeapp.generated.resources.live_tv_channel_count
import nuvio.composeapp.generated.resources.live_tv_earlier
import nuvio.composeapp.generated.resources.live_tv_empty_favorites
import nuvio.composeapp.generated.resources.live_tv_empty_group
import nuvio.composeapp.generated.resources.live_tv_error_title
import nuvio.composeapp.generated.resources.live_tv_keyboard_hint
import nuvio.composeapp.generated.resources.live_tv_last_channel
import nuvio.composeapp.generated.resources.live_tv_later
import nuvio.composeapp.generated.resources.live_tv_live_badge
import nuvio.composeapp.generated.resources.live_tv_loading
import nuvio.composeapp.generated.resources.live_tv_manifest_placeholder
import nuvio.composeapp.generated.resources.live_tv_now
import nuvio.composeapp.generated.resources.live_tv_recently_watched
import nuvio.composeapp.generated.resources.live_tv_refresh
import nuvio.composeapp.generated.resources.live_tv_remove_favorite
import nuvio.composeapp.generated.resources.live_tv_retry
import nuvio.composeapp.generated.resources.live_tv_save
import nuvio.composeapp.generated.resources.live_tv_settings
import nuvio.composeapp.generated.resources.live_tv_setup_message
import nuvio.composeapp.generated.resources.live_tv_setup_title
import nuvio.composeapp.generated.resources.live_tv_sort_channel
import nuvio.composeapp.generated.resources.live_tv_sort_ending_soon
import nuvio.composeapp.generated.resources.live_tv_sort_just_started
import nuvio.composeapp.generated.resources.live_tv_starts_at
import nuvio.composeapp.generated.resources.live_tv_title
import nuvio.composeapp.generated.resources.live_tv_view_guide
import nuvio.composeapp.generated.resources.live_tv_view_on_now
import nuvio.composeapp.generated.resources.live_tv_watch
import nuvio.composeapp.generated.resources.live_tv_watch_now
import org.jetbrains.compose.resources.stringResource

private const val PageJumpMs = 2 * LiveTvTime.HOUR_MS
private const val NowLeadFraction = 0.12f

@Composable
fun LiveTvScreen(
    modifier: Modifier = Modifier,
    topChromePadding: Dp? = null,
) {
    LaunchedEffect(Unit) { LiveTvRepository.ensureLoaded() }
    val state by LiveTvRepository.uiState.collectAsStateWithLifecycle()
    val tuningChannelId by LiveTvLauncher.tuningChannelId.collectAsStateWithLifecycle()
    val active = LocalScreenActive.current
    var showSettings by remember { mutableStateOf(false) }

    var nowMs by remember { mutableLongStateOf(LiveTvClock.nowEpochMs()) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            nowMs = LiveTvClock.nowEpochMs()
            LiveTvRepository.refreshIfStale(nowMs)
            delay(30_000L)
        }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topPadding = topChromePadding ?: (statusTop + nuvioPlatformExtraTopPadding + 12.dp)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(LiveTvNeon.ScreenGradient),
    ) {
        val screenWidth = maxWidth
        val compact = screenWidth < 720.dp
        val horizontalPadding = if (compact) 14.dp else 28.dp
        Column(modifier = Modifier.fillMaxSize().padding(top = topPadding)) {
            LiveTvHeader(
                state = state,
                compact = compact,
                horizontalPadding = horizontalPadding,
                onViewModeChange = LiveTvRepository::setViewMode,
                onLastChannel = {
                    LiveTvRepository.previousChannelId(null)?.let { id -> LiveTvLauncher.play(id, state.selectedGroupId) }
                },
                onRefresh = { LiveTvRepository.refresh(forceRefresh = true) },
                onSettings = { showSettings = true },
            )
            val guide = state.guide
            when {
                !state.isConfigured -> LiveTvSetupPanel(
                    initialUrl = state.manifestUrl,
                    onSave = LiveTvRepository::setManifestUrl,
                )

                guide == null && state.errorMessage != null -> LiveTvMessagePanel(
                    title = stringResource(Res.string.live_tv_error_title),
                    message = state.errorMessage,
                    primaryLabel = stringResource(Res.string.live_tv_retry),
                    onPrimary = { LiveTvRepository.refresh(forceRefresh = true) },
                    secondaryLabel = stringResource(Res.string.live_tv_settings),
                    onSecondary = { showSettings = true },
                )

                guide == null -> LiveTvLoadingPanel()

                else -> {
                    Spacer(Modifier.height(10.dp))
                    LiveTvChipRow(
                        state = state,
                        onSelect = LiveTvRepository::selectGroup,
                        contentPadding = PaddingValues(horizontal = horizontalPadding),
                    )
                    Spacer(Modifier.height(10.dp))
                    when (state.viewMode) {
                        LiveTvViewMode.Guide -> LiveTvGuideContent(
                            state = state,
                            guide = guide,
                            nowMs = nowMs,
                            compact = compact,
                            horizontalPadding = horizontalPadding,
                            active = active,
                            tuningChannelId = tuningChannelId,
                        )

                        LiveTvViewMode.OnNow -> LiveTvOnNowContent(
                            state = state,
                            guide = guide,
                            nowMs = nowMs,
                            compact = compact,
                            maxWidth = screenWidth,
                            horizontalPadding = horizontalPadding,
                            tuningChannelId = tuningChannelId,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        LiveTvSettingsDialog(
            state = state,
            onDismiss = { showSettings = false },
            onSave = { url ->
                showSettings = false
                if (normalizeManifestUrl(url) != normalizeManifestUrlOrBlank(state.manifestUrl)) {
                    LiveTvRepository.setManifestUrl(url)
                }
            },
        )
    }
}

private fun normalizeManifestUrlOrBlank(url: String): String = if (url.isBlank()) "" else normalizeManifestUrl(url)

@Composable
private fun LiveTvHeader(
    state: LiveTvUiState,
    compact: Boolean,
    horizontalPadding: Dp,
    onViewModeChange: (LiveTvViewMode) -> Unit,
    onLastChannel: () -> Unit,
    onRefresh: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(Res.string.live_tv_title).uppercase(),
                    style = TextStyle(
                        brush = Brush.horizontalGradient(listOf(LiveTvNeon.CyanSoft, LiveTvNeon.Magenta)),
                        fontSize = if (compact) 24.sp else 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp,
                    ),
                )
                if (!compact) LiveTvLiveBadge(label = stringResource(Res.string.live_tv_live_badge))
                if (state.isRefreshing) {
                    NuvioLoadingIndicator(size = 18.dp, color = LiveTvNeon.Cyan, active = true)
                }
            }
            state.guide?.let { guide ->
                Text(
                    text = guide.addonName + " · " + stringResource(Res.string.live_tv_channel_count, guide.lineupIds.size),
                    style = TextStyle(fontSize = 12.sp, color = LiveTvNeon.TextMuted),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (state.guide != null) {
            LiveTvViewToggle(
                mode = state.viewMode,
                compact = compact,
                onChange = onViewModeChange,
            )
            Spacer(Modifier.width(8.dp))
            if (state.recentIds.isNotEmpty()) {
                LiveTvIconButton(Icons.Rounded.History, stringResource(Res.string.live_tv_last_channel), onLastChannel)
            }
            LiveTvIconButton(Icons.Rounded.Refresh, stringResource(Res.string.live_tv_refresh), onRefresh)
        }
        LiveTvIconButton(Icons.Rounded.Settings, stringResource(Res.string.live_tv_settings), onSettings)
    }
}

@Composable
private fun LiveTvViewToggle(
    mode: LiveTvViewMode,
    compact: Boolean,
    onChange: (LiveTvViewMode) -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(LiveTvNeon.SurfaceRaised)
            .border(1.dp, LiveTvNeon.GridLineStrong, shape)
            .padding(3.dp),
    ) {
        LiveTvViewToggleItem(
            icon = Icons.Rounded.Tv,
            label = if (compact) null else stringResource(Res.string.live_tv_view_guide),
            selected = mode == LiveTvViewMode.Guide,
            onClick = { onChange(LiveTvViewMode.Guide) },
        )
        LiveTvViewToggleItem(
            icon = Icons.Rounded.GridView,
            label = if (compact) null else stringResource(Res.string.live_tv_view_on_now),
            selected = mode == LiveTvViewMode.OnNow,
            onClick = { onChange(LiveTvViewMode.OnNow) },
        )
    }
}

@Composable
private fun LiveTvViewToggleItem(
    icon: ImageVector,
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(if (selected) Modifier.background(LiveTvNeon.AccentGradient) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = if (label == null) 10.dp else 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val tint = if (selected) Color(0xFF05040A) else LiveTvNeon.TextSecondary
        Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.size(16.dp))
        if (label != null) {
            Text(text = label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = tint))
        }
    }
}

@Composable
internal fun LiveTvIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LiveTvNeon.TextSecondary,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun LiveTvGuideContent(
    state: LiveTvUiState,
    guide: LiveTvGuide,
    nowMs: Long,
    compact: Boolean,
    horizontalPadding: Dp,
    active: Boolean,
    tuningChannelId: String?,
) {
    val channels = remember(state.guide, state.selectedGroupId, state.favoriteIds, state.recentIds) { state.visibleChannels() }
    val favoriteSet = remember(state.favoriteIds) { state.favoriteIds.toSet() }
    val metrics = if (compact) LiveTvGridMetrics.Compact else LiveTvGridMetrics.Desktop
    val density = LocalDensity.current
    val halfHourBucket = LiveTvTime.floorToHalfHour(nowMs)
    val window = remember(guide, halfHourBucket) { liveTvGuideWindow(guide, nowMs) }
    val pxPerMs = with(density) { metrics.halfHourWidth.toPx() } / LiveTvTime.HALF_HOUR_MS.toFloat()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    var scrollX by remember { mutableFloatStateOf(0f) }
    var viewportPx by remember { mutableFloatStateOf(0f) }
    var userScrolled by remember { mutableStateOf(false) }
    var cursor by remember { mutableStateOf<LiveTvGuideCursor?>(null) }
    var selected by remember { mutableStateOf<Pair<LiveTvChannel, LiveTvProgram>?>(null) }

    val totalPx = (window.endMs - window.startMs) * pxPerMs
    val maxScroll = (totalPx - viewportPx).coerceAtLeast(0f)
    val horizontalState = rememberScrollableState { delta ->
        val old = scrollX
        scrollX = (scrollX - delta).coerceIn(0f, maxScroll)
        if (old != scrollX) userScrolled = true
        old - scrollX
    }

    fun nowTarget(): Float = ((nowMs - window.startMs) * pxPerMs - viewportPx * NowLeadFraction).coerceIn(0f, maxScroll)
    fun scrollToTime(timeMs: Long, animated: Boolean = true) {
        val target = ((timeMs - window.startMs) * pxPerMs - viewportPx * NowLeadFraction).coerceIn(0f, maxScroll)
        if (!animated) {
            scrollX = target
            return
        }
        scope.launch {
            animate(scrollX, target, animationSpec = tween(320)) { value, _ -> scrollX = value }
        }
    }

    // Start at "now", and follow it until the user scrolls away.
    LaunchedEffect(window.startMs, viewportPx) {
        if (viewportPx > 0f && !userScrolled) scrollX = nowTarget()
    }
    val jumpToNow by rememberUpdatedState {
        userScrolled = false
        scrollToTime(nowMs)
    }
    LaunchedEffect(Unit) {
        LiveTvScreenEvents.jumpToNow.collect {
            jumpToNow()
            listState.animateScrollToItem(0)
        }
    }
    LaunchedEffect(active, channels.isNotEmpty()) {
        if (active && channels.isNotEmpty()) runCatching { focusRequester.requestFocus() }
    }

    // Coarse visible range (one-slot buckets) so rows only recompose when a new slot scrolls in.
    val slotPx = with(density) { metrics.halfHourWidth.toPx() }
    val scrollBucket by remember(slotPx) { derivedStateOf { (scrollX / slotPx).toInt() } }
    val viewportSlots = (viewportPx / slotPx).toInt() + 2
    val visibleStartMs = window.startMs + (scrollBucket - 1) * LiveTvTime.HALF_HOUR_MS
    val visibleEndMs = window.startMs + (scrollBucket + viewportSlots + 1) * LiveTvTime.HALF_HOUR_MS

    val detail = selected ?: cursor?.let { c ->
        channels.getOrNull(c.row)?.let { channel -> guide.programAt(channel.id, c.timeMs)?.let { channel to it } }
    } ?: channels.firstOrNull()?.let { channel -> guide.programAt(channel.id, nowMs)?.let { channel to it } }

    fun play(channel: LiveTvChannel) = LiveTvLauncher.play(channel.id, state.selectedGroupId)

    fun moveCursor(row: Int, timeMs: Long) {
        val clampedRow = row.coerceIn(0, (channels.size - 1).coerceAtLeast(0))
        val clampedTime = timeMs.coerceIn(window.startMs, window.endMs - 1)
        cursor = LiveTvGuideCursor(clampedRow, clampedTime)
        selected = null
        val leftEdge = window.startMs + (scrollX / pxPerMs).toLong()
        val rightEdge = leftEdge + (viewportPx / pxPerMs).toLong()
        if (clampedTime < leftEdge + LiveTvTime.MINUTE_MS * 5 || clampedTime > rightEdge - LiveTvTime.MINUTE_MS * 20) {
            userScrolled = true
            scrollToTime(clampedTime)
        }
        scope.launch {
            val visible = listState.layoutInfo.visibleItemsInfo
            val first = visible.firstOrNull()?.index ?: 0
            val last = visible.lastOrNull()?.index ?: 0
            if (clampedRow <= first || clampedRow >= last) {
                listState.animateScrollToItem((clampedRow - 2).coerceAtLeast(0))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || channels.isEmpty()) return@onPreviewKeyEvent false
                val current = cursor ?: LiveTvGuideCursor(0, nowMs)
                val channel = channels.getOrNull(current.row)
                val program = channel?.let { guide.programAt(it.id, current.timeMs) }
                when (event.key) {
                    Key.DirectionDown -> { moveCursor(current.row + 1, current.timeMs); true }
                    Key.DirectionUp -> { moveCursor(current.row - 1, current.timeMs); true }
                    Key.DirectionRight -> {
                        val next = channel?.let { guide.nextProgram(it.id, (program?.endMs ?: current.timeMs + 1)) }
                        moveCursor(current.row, next?.startMs ?: (current.timeMs + LiveTvTime.HALF_HOUR_MS))
                        true
                    }
                    Key.DirectionLeft -> {
                        val previous = channel?.let { c ->
                            guide.programs(c.id).lastOrNull { it.endMs <= (program?.startMs ?: current.timeMs) }
                        }
                        moveCursor(current.row, previous?.startMs ?: (current.timeMs - LiveTvTime.HALF_HOUR_MS))
                        true
                    }
                    Key.PageDown -> { moveCursor(current.row, current.timeMs + PageJumpMs); true }
                    Key.PageUp -> { moveCursor(current.row, current.timeMs - PageJumpMs); true }
                    Key.MoveHome -> { moveCursor(current.row, nowMs); userScrolled = false; true }
                    Key.Enter, Key.NumPadEnter, Key.DirectionCenter -> {
                        if (channel != null) play(channel)
                        true
                    }
                    Key.F -> {
                        channel?.let { LiveTvRepository.toggleFavorite(it.id) }
                        true
                    }
                    else -> false
                }
            },
    ) {
        detail?.let { (channel, program) ->
            LiveTvDetailsHeader(
                channel = channel,
                program = program,
                nowMs = nowMs,
                compact = compact,
                isFavorite = channel.id in favoriteSet,
                horizontalPadding = horizontalPadding,
                onWatch = { play(channel) },
                onFavoriteToggle = { LiveTvRepository.toggleFavorite(channel.id) },
            )
            Spacer(Modifier.height(10.dp))
        }
        LiveTvTimeNav(
            compact = compact,
            horizontalPadding = horizontalPadding,
            onEarlier = { userScrolled = true; scrollToTime(window.startMs + ((scrollX / pxPerMs).toLong()) - PageJumpMs + (viewportPx * NowLeadFraction / pxPerMs).toLong()) },
            onNow = { userScrolled = false; scrollToTime(nowMs) },
            onLater = { userScrolled = true; scrollToTime(window.startMs + ((scrollX / pxPerMs).toLong()) + PageJumpMs + (viewportPx * NowLeadFraction / pxPerMs).toLong()) },
        )
        if (channels.isEmpty()) {
            LiveTvEmptyGroup(state.selectedGroupId == LiveTvFavoritesGroupId)
        } else {
            val channelColumnPx = with(density) { metrics.channelColumnWidth.toPx() }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = if (compact) 0.dp else horizontalPadding - 8.dp)
                    .onSizeChanged { size -> viewportPx = (size.width - channelColumnPx).coerceAtLeast(0f) },
            ) {
                LiveTvGuideGrid(
                    guide = guide,
                    channels = channels,
                    window = window,
                    nowMs = nowMs,
                    metrics = metrics,
                    listState = listState,
                    horizontalState = horizontalState,
                    scrollXPx = { scrollX },
                    visibleStartMs = visibleStartMs,
                    visibleEndMs = visibleEndMs,
                    pxPerMs = pxPerMs,
                    cursor = cursor,
                    selectedProgramId = selected?.second?.id,
                    favoriteIds = favoriteSet,
                    tuningChannelId = tuningChannelId,
                    contentPadding = PaddingValues(bottom = nuvioSafeBottomPadding(24.dp)),
                    onChannelClick = ::play,
                    onProgramClick = { row, channel, program ->
                        runCatching { focusRequester.requestFocus() }
                        cursor = LiveTvGuideCursor(row, program.startMs.coerceAtLeast(window.startMs))
                        if (program.isAiringAt(nowMs)) {
                            play(channel)
                        } else {
                            selected = channel to program
                        }
                    },
                    onFavoriteToggle = { LiveTvRepository.toggleFavorite(it.id) },
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                        .border(1.dp, LiveTvNeon.GridLine, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
                )
            }
        }
    }
}

@Composable
private fun LiveTvTimeNav(
    compact: Boolean,
    horizontalPadding: Dp,
    onEarlier: () -> Unit,
    onNow: () -> Unit,
    onLater: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LiveTvIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, stringResource(Res.string.live_tv_earlier), onEarlier)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(LiveTvNeon.SurfaceRaised)
                .border(1.dp, LiveTvNeon.Cyan.copy(alpha = 0.45f), RoundedCornerShape(50))
                .clickable(onClick = onNow)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(Res.string.live_tv_now),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.Cyan),
            )
        }
        LiveTvIconButton(Icons.AutoMirrored.Rounded.KeyboardArrowRight, stringResource(Res.string.live_tv_later), onLater)
        if (!compact) {
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(Res.string.live_tv_keyboard_hint),
                style = TextStyle(fontSize = 11.sp, color = LiveTvNeon.TextMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveTvDetailsHeader(
    channel: LiveTvChannel,
    program: LiveTvProgram,
    nowMs: Long,
    compact: Boolean,
    isFavorite: Boolean,
    horizontalPadding: Dp,
    onWatch: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    val airing = program.isAiringAt(nowMs)
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding)
            .height(if (compact) 104.dp else 136.dp)
            .clip(shape)
            .background(LiveTvNeon.Panel)
            .border(1.dp, Brush.horizontalGradient(listOf(LiveTvNeon.Violet.copy(alpha = 0.6f), LiveTvNeon.Cyan.copy(alpha = 0.4f), LiveTvNeon.Magenta.copy(alpha = 0.6f))), shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(0.34f, fill = true)
                    .widthIn(max = 260.dp)
                    .background(LiveTvNeon.SurfaceRaised),
            ) {
                val art = program.thumbnail
                if (!art.isNullOrBlank()) {
                    NuvioAsyncImage(
                        model = art,
                        contentDescription = program.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        LiveTvChannelLogo(channel = channel, size = 72.dp)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Brush.horizontalGradient(listOf(Color.Transparent, LiveTvNeon.Panel))),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (compact) 14.dp else 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (compact) LiveTvChannelLogo(channel = channel, size = 28.dp)
                Text(
                    text = (if (channel.number > 0) "${channel.number}  " else "") + channel.name,
                    style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.CyanSoft, letterSpacing = 0.8.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (airing) LiveTvLiveBadge(label = stringResource(Res.string.live_tv_live_badge), compact = true)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = program.title,
                style = TextStyle(fontSize = if (compact) 17.sp else 22.sp, fontWeight = FontWeight.Black, color = LiveTvNeon.TextPrimary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            val meta = buildList {
                add(if (airing || program.endMs <= nowMs) liveTvTimeRange(program) else stringResource(Res.string.live_tv_starts_at, LiveTvClock.formatTimeOfDay(program.startMs)))
                program.genres.filterNot { it.equals("Series", ignoreCase = true) }.take(2).forEach(::add)
                program.releaseInfo?.let(::add)
            }.joinToString("  ·  ")
            Text(
                text = meta,
                style = TextStyle(fontSize = 12.sp, color = LiveTvNeon.TextSecondary),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (airing) {
                Spacer(Modifier.height(6.dp))
                LiveTvProgressBar(progress = program.progressAt(nowMs), modifier = Modifier.fillMaxWidth(0.6f).height(3.dp))
            }
            if (!compact) {
                program.description?.let { description ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = TextStyle(fontSize = 12.sp, color = LiveTvNeon.TextMuted),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(
            modifier = Modifier.padding(end = if (compact) 10.dp else 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LiveTvWatchButton(
                label = stringResource(if (airing) Res.string.live_tv_watch_now else Res.string.live_tv_watch),
                compact = compact,
                onClick = onWatch,
            )
            LiveTvIconButton(
                icon = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = stringResource(if (isFavorite) Res.string.live_tv_remove_favorite else Res.string.live_tv_add_favorite),
                onClick = onFavoriteToggle,
                tint = if (isFavorite) LiveTvNeon.Magenta else LiveTvNeon.TextSecondary,
            )
        }
    }
}

@Composable
internal fun LiveTvWatchButton(
    label: String,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(LiveTvNeon.AccentGradient)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 12.dp else 18.dp, vertical = if (compact) 8.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Color(0xFF05040A), modifier = Modifier.size(18.dp))
        if (!compact) {
            Text(text = label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Black, color = Color(0xFF05040A)))
        }
    }
}

@Composable
private fun LiveTvOnNowContent(
    state: LiveTvUiState,
    guide: LiveTvGuide,
    nowMs: Long,
    compact: Boolean,
    maxWidth: Dp,
    horizontalPadding: Dp,
    tuningChannelId: String?,
) {
    val channels = remember(state.guide, state.selectedGroupId, state.favoriteIds, state.recentIds) { state.visibleChannels() }
    val minuteBucket = nowMs / LiveTvTime.MINUTE_MS
    val entries = remember(channels, state.onNowSort, minuteBucket) { guide.onNow(channels, nowMs, state.onNowSort) }
    val favoriteSet = remember(state.favoriteIds) { state.favoriteIds.toSet() }
    val recents = remember(state.recentIds, guide) { state.recentIds.mapNotNull(guide::channel) }
    Column(modifier = Modifier.fillMaxSize()) {
        if (recents.isNotEmpty() && state.selectedGroupId != LiveTvRecentGroupId) {
            LiveTvRecentStrip(
                channels = recents,
                horizontalPadding = horizontalPadding,
                onPlay = { LiveTvLauncher.play(it.id, state.selectedGroupId) },
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding).padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LiveTvSortChip(stringResource(Res.string.live_tv_sort_channel), state.onNowSort == LiveTvOnNowSort.Channel) {
                LiveTvRepository.setOnNowSort(LiveTvOnNowSort.Channel)
            }
            LiveTvSortChip(stringResource(Res.string.live_tv_sort_ending_soon), state.onNowSort == LiveTvOnNowSort.EndingSoon) {
                LiveTvRepository.setOnNowSort(LiveTvOnNowSort.EndingSoon)
            }
            LiveTvSortChip(stringResource(Res.string.live_tv_sort_just_started), state.onNowSort == LiveTvOnNowSort.JustStarted) {
                LiveTvRepository.setOnNowSort(LiveTvOnNowSort.JustStarted)
            }
        }
        if (entries.isEmpty()) {
            LiveTvEmptyGroup(state.selectedGroupId == LiveTvFavoritesGroupId)
        } else {
            val columns = ((maxWidth - horizontalPadding * 2) / if (compact) 170.dp else 300.dp).toInt().coerceIn(1, 6)
            LiveTvOnNowGrid(
                entries = entries,
                nowMs = nowMs,
                favoriteIds = favoriteSet,
                tuningChannelId = tuningChannelId,
                columns = columns,
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    bottom = nuvioSafeBottomPadding(24.dp),
                ),
                onPlay = { LiveTvLauncher.play(it.id, state.selectedGroupId) },
                onFavoriteToggle = { LiveTvRepository.toggleFavorite(it.id) },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun LiveTvSortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) LiveTvNeon.Cyan.copy(alpha = 0.16f) else Color.Transparent)
            .border(1.dp, if (selected) LiveTvNeon.Cyan.copy(alpha = 0.7f) else LiveTvNeon.GridLineStrong, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = if (selected) LiveTvNeon.Cyan else LiveTvNeon.TextSecondary),
        )
    }
}

@Composable
private fun LiveTvRecentStrip(
    channels: List<LiveTvChannel>,
    horizontalPadding: Dp,
    onPlay: (LiveTvChannel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding)) {
        Text(
            text = stringResource(Res.string.live_tv_recently_watched).uppercase(),
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, color = LiveTvNeon.TextMuted),
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            channels.take(10).forEach { channel ->
                Box(modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { onPlay(channel) }) {
                    LiveTvChannelLogo(channel = channel, size = 52.dp)
                }
            }
        }
    }
}

@Composable
private fun LiveTvEmptyGroup(isFavorites: Boolean) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(if (isFavorites) Res.string.live_tv_empty_favorites else Res.string.live_tv_empty_group),
            style = TextStyle(fontSize = 15.sp, color = LiveTvNeon.TextMuted),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LiveTvLoadingPanel() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            NuvioLoadingIndicator(color = LiveTvNeon.Cyan)
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(Res.string.live_tv_loading),
                style = TextStyle(fontSize = 14.sp, color = LiveTvNeon.TextSecondary),
            )
        }
    }
}

@Composable
private fun LiveTvMessagePanel(
    title: String,
    message: String?,
    primaryLabel: String,
    onPrimary: () -> Unit,
    secondaryLabel: String,
    onSecondary: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.widthIn(max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = LiveTvNeon.Magenta, modifier = Modifier.size(48.dp))
            Text(text = title, style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Black, color = LiveTvNeon.TextPrimary))
            if (!message.isNullOrBlank()) {
                Text(
                    text = message,
                    style = TextStyle(fontSize = 13.sp, color = LiveTvNeon.TextSecondary),
                    textAlign = TextAlign.Center,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LiveTvWatchButton(label = primaryLabel, compact = false, onClick = onPrimary)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .border(1.dp, LiveTvNeon.GridLineStrong, RoundedCornerShape(50))
                        .clickable(onClick = onSecondary)
                        .padding(horizontal = 18.dp, vertical = 10.dp),
                ) {
                    Text(text = secondaryLabel, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.TextSecondary))
                }
            }
        }
    }
}

@Composable
private fun LiveTvSetupPanel(
    initialUrl: String,
    onSave: (String) -> Unit,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(LiveTvNeon.Panel)
                .border(1.dp, LiveTvNeon.AccentGradient, RoundedCornerShape(22.dp))
                .padding(26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(Icons.Rounded.LiveTv, contentDescription = null, tint = LiveTvNeon.Cyan, modifier = Modifier.size(44.dp))
            Text(
                text = stringResource(Res.string.live_tv_setup_title),
                style = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Black, color = LiveTvNeon.TextPrimary),
            )
            Text(
                text = stringResource(Res.string.live_tv_setup_message),
                style = TextStyle(fontSize = 13.sp, color = LiveTvNeon.TextSecondary),
            )
            NuvioInputField(
                value = url,
                onValueChange = { url = it },
                placeholder = stringResource(Res.string.live_tv_manifest_placeholder),
            )
            NuvioPrimaryButton(
                text = stringResource(Res.string.live_tv_save),
                enabled = url.isNotBlank(),
                onClick = { onSave(url) },
            )
        }
    }
}

@Composable
private fun LiveTvSettingsDialog(
    state: LiveTvUiState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var url by remember { mutableStateOf(state.manifestUrl) }
    DialogSurface(
        onDismissRequest = onDismiss,
        title = stringResource(Res.string.live_tv_settings),
        message = stringResource(Res.string.live_tv_setup_message),
    ) {
        NuvioInputField(
            value = url,
            onValueChange = { url = it },
            placeholder = stringResource(Res.string.live_tv_manifest_placeholder),
        )
        state.guide?.let { guide ->
            Text(
                text = guide.addonName + " · " + stringResource(Res.string.live_tv_channel_count, guide.lineupIds.size),
                style = TextStyle(fontSize = 12.sp, color = LiveTvNeon.TextMuted),
            )
        }
        DialogButtons {
            DialogButton(text = stringResource(Res.string.live_tv_cancel), onClick = onDismiss)
            DialogButton(
                text = stringResource(Res.string.live_tv_save),
                onClick = { onSave(url) },
                style = DialogButtonStyle.Primary,
                enabled = url.isNotBlank(),
            )
        }
    }
}
