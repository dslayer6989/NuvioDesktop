package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioLoadingIndicator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Sizes for the guide; desktop gets roomier rows than phones. */
@Immutable
internal data class LiveTvGridMetrics(
    val channelColumnWidth: Dp,
    val rowHeight: Dp,
    val halfHourWidth: Dp,
    val headerHeight: Dp,
    val compact: Boolean,
) {
    companion object {
        val Desktop = LiveTvGridMetrics(
            channelColumnWidth = 232.dp,
            rowHeight = 68.dp,
            halfHourWidth = 210.dp,
            headerHeight = 40.dp,
            compact = false,
        )
        val Compact = LiveTvGridMetrics(
            channelColumnWidth = 108.dp,
            rowHeight = 62.dp,
            halfHourWidth = 150.dp,
            headerHeight = 36.dp,
            compact = true,
        )
    }
}

/** Keyboard/remote cursor: which row and which moment in time is focused. */
@Immutable
internal data class LiveTvGuideCursor(
    val row: Int,
    val timeMs: Long,
)

/**
 * Everything the grid needs that changes while scrolling is passed as lambdas read only in
 * the layout/draw phase, so horizontal scrolling never recomposes the rows.
 */
@Composable
internal fun LiveTvGuideGrid(
    guide: LiveTvGuide,
    channels: List<LiveTvChannel>,
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    listState: LazyListState,
    horizontalState: ScrollableState,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float,
    cursor: LiveTvGuideCursor?,
    selectedProgramId: String?,
    favoriteIds: Set<String>,
    tuningChannelId: String?,
    contentPadding: PaddingValues,
    onChannelClick: (LiveTvChannel) -> Unit,
    onProgramClick: (Int, LiveTvChannel, LiveTvProgram) -> Unit,
    onFavoriteToggle: (LiveTvChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.scrollable(horizontalState, Orientation.Horizontal),
    ) {
        LiveTvTimeHeader(
            window = window,
            nowMs = nowMs,
            metrics = metrics,
            scrollXPx = scrollXPx,
            visibleStartMs = visibleStartMs,
            visibleEndMs = visibleEndMs,
            pxPerMs = pxPerMs,
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
        ) {
            itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                LiveTvGuideRow(
                    rowIndex = index,
                    channel = channel,
                    programs = guide.programs(channel.id),
                    window = window,
                    nowMs = nowMs,
                    metrics = metrics,
                    scrollXPx = scrollXPx,
                    visibleStartMs = visibleStartMs,
                    visibleEndMs = visibleEndMs,
                    pxPerMs = pxPerMs,
                    cursorTimeMs = cursor?.takeIf { it.row == index }?.timeMs,
                    selectedProgramId = selectedProgramId,
                    isFavorite = channel.id in favoriteIds,
                    isTuning = channel.id == tuningChannelId,
                    onChannelClick = onChannelClick,
                    onProgramClick = onProgramClick,
                    onFavoriteToggle = onFavoriteToggle,
                )
            }
        }
    }
}

@Composable
private fun LiveTvTimeHeader(
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float,
) {
    val slots = remember(window, visibleStartMs, visibleEndMs) {
        window.slots.filter { it + LiveTvTime.HALF_HOUR_MS > visibleStartMs && it < visibleEndMs }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.headerHeight)
            .background(LiveTvNeon.Surface)
            .drawBehind {
                drawLine(LiveTvNeon.GridLineStrong, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
            },
    ) {
        Box(
            modifier = Modifier
                .width(metrics.channelColumnWidth)
                .fillMaxHeight()
                .padding(start = 14.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = LiveTvClock.formatDay(nowMs),
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.CyanSoft, letterSpacing = 0.6.sp),
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            slots.forEach { slotStart ->
                val isDayStart = LiveTvClock.localDayStart(slotStart) == slotStart
                val isCurrent = nowMs >= slotStart && nowMs < slotStart + LiveTvTime.HALF_HOUR_MS
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(((slotStart - window.startMs) * pxPerMs - scrollXPx()).roundToInt(), 0)
                        }
                        .width(metrics.halfHourWidth)
                        .fillMaxHeight()
                        .drawBehind {
                            drawLine(
                                color = if (isDayStart) LiveTvNeon.Magenta else LiveTvNeon.GridLineStrong,
                                start = Offset(0f, size.height * 0.35f),
                                end = Offset(0f, size.height),
                                strokeWidth = if (isDayStart) 2f else 1f,
                            )
                        }
                        .padding(start = 10.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        text = if (isDayStart) LiveTvClock.formatDay(slotStart) else LiveTvClock.formatTimeOfDay(slotStart),
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = if (isCurrent || isDayStart) FontWeight.Black else FontWeight.SemiBold,
                            color = when {
                                isDayStart -> LiveTvNeon.Magenta
                                isCurrent -> LiveTvNeon.Cyan
                                else -> LiveTvNeon.TextSecondary
                            },
                        ),
                        maxLines = 1,
                    )
                }
            }
            LiveTvNowMarker(
                nowMs = nowMs,
                window = window,
                pxPerMs = pxPerMs,
                scrollXPx = scrollXPx,
                header = true,
            )
        }
    }
}

@Composable
private fun LiveTvGuideRow(
    rowIndex: Int,
    channel: LiveTvChannel,
    programs: List<LiveTvProgram>,
    window: LiveTvGuideWindow,
    nowMs: Long,
    metrics: LiveTvGridMetrics,
    scrollXPx: () -> Float,
    visibleStartMs: Long,
    visibleEndMs: Long,
    pxPerMs: Float,
    cursorTimeMs: Long?,
    selectedProgramId: String?,
    isFavorite: Boolean,
    isTuning: Boolean,
    onChannelClick: (LiveTvChannel) -> Unit,
    onProgramClick: (Int, LiveTvChannel, LiveTvProgram) -> Unit,
    onFavoriteToggle: (LiveTvChannel) -> Unit,
) {
    val density = LocalDensity.current
    val rowFocused = cursorTimeMs != null
    val visiblePrograms = remember(programs, window, visibleStartMs, visibleEndMs) {
        programs.filter { program ->
            program.endMs > max(window.startMs, visibleStartMs) && program.startMs < min(window.endMs, visibleEndMs)
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.rowHeight)
            .background(if (rowFocused) LiveTvNeon.SurfaceRaised.copy(alpha = 0.55f) else Color.Transparent)
            .drawBehind {
                drawLine(LiveTvNeon.GridLine, Offset(0f, size.height - 1f), Offset(size.width, size.height - 1f), 1f)
            },
    ) {
        LiveTvChannelCell(
            channel = channel,
            metrics = metrics,
            isFavorite = isFavorite,
            isTuning = isTuning,
            highlighted = rowFocused,
            onClick = { onChannelClick(channel) },
            onFavoriteToggle = { onFavoriteToggle(channel) },
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .clipToBounds(),
        ) {
            if (programs.isEmpty()) {
                LiveTvNoGuideCell(
                    focused = rowFocused,
                    onClick = { onChannelClick(channel) },
                )
            } else {
                visiblePrograms.forEach { program ->
                    val startMs = max(program.startMs, window.startMs)
                    val endMs = min(program.endMs, window.endMs)
                    val cellStartPx = (startMs - window.startMs) * pxPerMs
                    val cellWidthPx = ((endMs - startMs) * pxPerMs).coerceAtLeast(1f)
                    val cellWidth = with(density) { cellWidthPx.toDp() }
                    val focused = cursorTimeMs != null && cursorTimeMs >= program.startMs && cursorTimeMs < program.endMs
                    LiveTvProgramCell(
                        program = program,
                        nowMs = nowMs,
                        width = cellWidth,
                        selected = program.id == selectedProgramId,
                        focused = focused,
                        compact = metrics.compact,
                        cellStartPx = cellStartPx,
                        cellWidthPx = cellWidthPx,
                        scrollXPx = scrollXPx,
                        onClick = { onProgramClick(rowIndex, channel, program) },
                    )
                }
            }
            LiveTvNowMarker(
                nowMs = nowMs,
                window = window,
                pxPerMs = pxPerMs,
                scrollXPx = scrollXPx,
                header = false,
            )
        }
    }
}

@Composable
private fun LiveTvChannelCell(
    channel: LiveTvChannel,
    metrics: LiveTvGridMetrics,
    isFavorite: Boolean,
    isTuning: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
    onFavoriteToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .width(metrics.channelColumnWidth)
            .fillMaxHeight()
            .background(
                Brush.horizontalGradient(
                    listOf(LiveTvNeon.Surface, if (highlighted) LiveTvNeon.SurfaceRaised else LiveTvNeon.Surface),
                ),
            )
            .drawBehind {
                drawLine(LiveTvNeon.GridLineStrong, Offset(size.width - 1f, 0f), Offset(size.width - 1f, size.height), 1f)
                if (highlighted) {
                    drawRect(brush = Brush.verticalGradient(listOf(LiveTvNeon.Cyan, LiveTvNeon.Magenta)), size = size.copy(width = 3.dp.toPx()))
                }
            }
            .clickable(onClick = onClick)
            .padding(horizontal = if (metrics.compact) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (metrics.compact) 6.dp else 10.dp),
    ) {
        if (!metrics.compact) {
            Text(
                text = if (channel.number > 0) channel.number.toString() else "·",
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LiveTvNeon.TextMuted),
                modifier = Modifier.width(22.dp),
                maxLines = 1,
            )
        }
        Box(contentAlignment = Alignment.Center) {
            LiveTvChannelLogo(
                channel = channel,
                size = if (metrics.compact) 40.dp else 46.dp,
                selected = highlighted,
            )
            if (isTuning) {
                NuvioLoadingIndicator(size = 26.dp, color = LiveTvNeon.Cyan, active = true)
            }
        }
        if (!metrics.compact) {
            Text(
                text = channel.name,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = LiveTvNeon.TextPrimary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                contentDescription = null,
                tint = if (isFavorite) LiveTvNeon.Magenta else LiveTvNeon.TextMuted.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onFavoriteToggle),
            )
        } else if (isFavorite) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = LiveTvNeon.Magenta,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

@Composable
private fun LiveTvProgramCell(
    program: LiveTvProgram,
    nowMs: Long,
    width: Dp,
    selected: Boolean,
    focused: Boolean,
    compact: Boolean,
    cellStartPx: Float,
    cellWidthPx: Float,
    scrollXPx: () -> Float,
    onClick: () -> Unit,
) {
    val airing = program.isAiringAt(nowMs)
    val past = program.endMs <= nowMs
    val shape = RoundedCornerShape(10.dp)
    val highlight = selected || focused
    Box(
        modifier = Modifier
            .offset { IntOffset((cellStartPx - scrollXPx()).roundToInt(), 0) }
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(shape)
            .then(
                when {
                    airing -> Modifier.background(LiveTvNeon.AiringGradient)
                    past -> Modifier.background(LiveTvNeon.PastCell)
                    else -> Modifier.background(LiveTvNeon.FutureCell)
                },
            )
            .then(
                if (highlight) {
                    Modifier.border(1.5.dp, LiveTvNeon.AccentGradient, shape)
                } else if (airing) {
                    Modifier.border(1.dp, LiveTvNeon.Cyan.copy(alpha = 0.35f), shape)
                } else {
                    Modifier.border(1.dp, LiveTvNeon.GridLine, shape)
                },
            )
            .drawBehind {
                if (airing) {
                    drawRect(
                        brush = Brush.verticalGradient(listOf(LiveTvNeon.Cyan, LiveTvNeon.Magenta)),
                        size = size.copy(width = 3.dp.toPx()),
                    )
                    val progressWidth = size.width * program.progressAt(nowMs)
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(LiveTvNeon.Cyan.copy(alpha = 0.10f), LiveTvNeon.Cyan.copy(alpha = 0.02f)),
                            endX = progressWidth.coerceAtLeast(1f),
                        ),
                        size = size.copy(width = progressWidth),
                    )
                }
            }
            .clickable(onClick = onClick),
    ) {
        // Keep the title readable when the programme started before the visible edge ("sticky" titles).
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .offset {
                    val hiddenPx = (scrollXPx() - cellStartPx).coerceAtLeast(0f)
                    val maxShift = (cellWidthPx - 90.dp.toPx()).coerceAtLeast(0f)
                    IntOffset(hiddenPx.coerceAtMost(maxShift).roundToInt(), 0)
                }
                .graphicsLayer {
                    // Hide text once only a sliver of the programme is still on screen.
                    val visiblePx = cellWidthPx - (scrollXPx() - cellStartPx).coerceAtLeast(0f)
                    alpha = if (visiblePx < 64.dp.toPx()) 0f else 1f
                }
                .padding(start = if (airing) 11.dp else 9.dp, end = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = program.title,
                style = TextStyle(
                    fontSize = if (compact) 13.sp else 14.sp,
                    fontWeight = if (airing) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (past) LiveTvNeon.TextMuted else LiveTvNeon.TextPrimary,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = liveTvTimeRange(program),
                style = TextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (airing) LiveTvNeon.CyanSoft else LiveTvNeon.TextMuted,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LiveTvNoGuideCell(
    focused: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clip(shape)
            .background(LiveTvNeon.FutureCell.copy(alpha = 0.6f))
            .border(if (focused) 1.5.dp else 1.dp, if (focused) LiveTvNeon.AccentGradient else Brush.linearGradient(listOf(LiveTvNeon.GridLine, LiveTvNeon.GridLine)), shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = LiveTvStrings.noGuideData(),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = LiveTvNeon.TextMuted),
            maxLines = 1,
        )
    }
}

/** The glowing vertical "now" line. */
@Composable
private fun LiveTvNowMarker(
    nowMs: Long,
    window: LiveTvGuideWindow,
    pxPerMs: Float,
    scrollXPx: () -> Float,
    header: Boolean,
) {
    if (nowMs < window.startMs || nowMs > window.endMs) return
    val nowPx = (nowMs - window.startMs) * pxPerMs
    Box(
        modifier = Modifier
            .offset { IntOffset((nowPx - scrollXPx()).roundToInt() - 6.dp.roundToPx(), 0) }
            .width(12.dp)
            .fillMaxHeight()
            .drawBehind {
                val x = size.width / 2f
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(Color.Transparent, LiveTvNeon.Cyan.copy(alpha = 0.28f), Color.Transparent),
                    ),
                )
                drawLine(LiveTvNeon.CyanSoft, Offset(x, 0f), Offset(x, size.height), 2.dp.toPx())
                if (header) {
                    drawCircle(LiveTvNeon.Cyan, radius = 4.dp.toPx(), center = Offset(x, size.height - 4.dp.toPx()))
                }
            },
    )
}

internal fun liveTvTimeRange(program: LiveTvProgram): String =
    "${LiveTvClock.formatTimeOfDay(program.startMs)} – ${LiveTvClock.formatTimeOfDay(program.endMs)}"
