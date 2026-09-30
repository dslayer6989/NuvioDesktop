package com.nuvio.app.features.livetv

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nuvio.app.core.ui.NuvioAsyncImage
import androidx.compose.ui.layout.ContentScale

/** The Neon Broadcast palette. Live TV always uses it, whatever the app theme is. */
internal object LiveTvNeon {
    val Background = Color(0xFF07060D)
    val Surface = Color(0xFF0F0C1A)
    val SurfaceRaised = Color(0xFF171329)
    val Panel = Color(0xCC120F22)
    val GridLine = Color(0xFF231E3A)
    val GridLineStrong = Color(0xFF342C57)
    val Cyan = Color(0xFF00E5FF)
    val CyanSoft = Color(0xFF5CF2FF)
    val Magenta = Color(0xFFFF2BD6)
    val Violet = Color(0xFF7C4DFF)
    val LiveRed = Color(0xFFFF3B6B)
    val TextPrimary = Color(0xFFF4F2FF)
    val TextSecondary = Color(0xFFB9B3D9)
    val TextMuted = Color(0xFF7D7699)
    val PastCell = Color(0xFF0D0B17)
    val FutureCell = Color(0xFF15122A)
    val AiringCell = Color(0xFF1B1840)

    val AccentGradient = Brush.horizontalGradient(listOf(Violet, Cyan, Magenta))
    val AiringGradient = Brush.horizontalGradient(listOf(Color(0xFF1E2A55), Color(0xFF2A1745)))
    val ScreenGradient = Brush.verticalGradient(listOf(Color(0xFF0B0917), Background, Color(0xFF05040A)))
}

/** Pulsing red "● LIVE" pill. */
@Composable
internal fun LiveTvLiveBadge(
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "live_badge")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
        label = "live_badge_pulse",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(LiveTvNeon.LiveRed.copy(alpha = 0.16f))
            .border(1.dp, LiveTvNeon.LiveRed.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 1.dp else 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 6.dp else 7.dp)
                .drawBehind {
                    drawCircle(LiveTvNeon.LiveRed.copy(alpha = 0.35f * pulse), radius = size.minDimension * 1.3f)
                    drawCircle(LiveTvNeon.LiveRed.copy(alpha = 0.55f + 0.45f * pulse))
                },
        )
        Text(
            text = label,
            style = TextStyle(
                fontSize = if (compact) 9.sp else 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp,
                color = LiveTvNeon.TextPrimary,
            ),
        )
    }
}

/** Channel logo on a rounded, softly lit tile, with a monogram when there is no image. */
@Composable
internal fun LiveTvChannelLogo(
    channel: LiveTvChannel,
    size: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF1B1733), Color(0xFF100D1E))))
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                brush = if (selected) LiveTvNeon.AccentGradient else Brush.linearGradient(listOf(LiveTvNeon.GridLineStrong, LiveTvNeon.GridLine)),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = channel.monogram(),
            style = TextStyle(
                fontSize = (size.value * 0.30f).sp,
                fontWeight = FontWeight.Black,
                color = LiveTvNeon.TextSecondary,
            ),
        )
        if (!channel.logo.isNullOrBlank()) {
            NuvioAsyncImage(
                model = channel.logo,
                contentDescription = channel.name,
                modifier = Modifier.fillMaxSize().padding(size * 0.12f),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** A thin progress track with a glowing cyan-to-magenta fill. */
@Composable
internal fun LiveTvProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 3.dp,
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f))
            .drawBehind {
                val width = size.width * progress.coerceIn(0f, 1f)
                if (width <= 0f) return@drawBehind
                drawRoundRect(
                    brush = Brush.horizontalGradient(listOf(LiveTvNeon.Cyan, LiveTvNeon.Magenta), endX = size.width),
                    size = size.copy(width = width),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height / 2f),
                )
                drawCircle(
                    color = LiveTvNeon.CyanSoft.copy(alpha = 0.55f),
                    radius = size.height * 1.4f,
                    center = Offset(width, size.height / 2f),
                )
            }
            .padding(vertical = height / 2),
    )
}

internal fun LiveTvChannel.monogram(): String =
    name.split(' ', '-', '/', '|')
        .filter { it.isNotBlank() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "TV" }

/** "42 min" / "1h 5m" for the remaining time of a programme. */
internal fun liveTvDurationParts(durationMs: Long): Pair<Int, Int> {
    val totalMinutes = ((durationMs + LiveTvTime.MINUTE_MS - 1) / LiveTvTime.MINUTE_MS).toInt().coerceAtLeast(0)
    return totalMinutes / 60 to totalMinutes % 60
}
