package com.nuvio.app.features.livetv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.compose.resources.stringResource
import nuvio.composeapp.generated.resources.Res
import nuvio.composeapp.generated.resources.live_tv_chip_all
import nuvio.composeapp.generated.resources.live_tv_chip_favorites
import nuvio.composeapp.generated.resources.live_tv_chip_recent

internal data class LiveTvChip(
    val id: String?,
    val label: String,
    val icon: ImageVector? = null,
)

@Composable
internal fun LiveTvChipRow(
    state: LiveTvUiState,
    onSelect: (String?) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val chips = buildList {
        add(LiveTvChip(id = null, label = stringResource(Res.string.live_tv_chip_all)))
        if (state.favoriteIds.isNotEmpty()) {
            add(LiveTvChip(id = LiveTvFavoritesGroupId, label = stringResource(Res.string.live_tv_chip_favorites), icon = Icons.Rounded.Star))
        }
        if (state.recentIds.isNotEmpty()) {
            add(LiveTvChip(id = LiveTvRecentGroupId, label = stringResource(Res.string.live_tv_chip_recent), icon = Icons.Rounded.History))
        }
        state.addonChips().forEach { group -> add(LiveTvChip(id = group.id, label = group.name)) }
    }
    LazyRow(
        modifier = modifier,
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(chips, key = { it.id ?: "__all__" }) { chip ->
            LiveTvChipItem(
                chip = chip,
                selected = chip.id == state.selectedGroupId,
                onClick = { onSelect(chip.id) },
            )
        }
    }
}

@Composable
private fun LiveTvChipItem(
    chip: LiveTvChip,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .then(
                if (selected) {
                    Modifier.background(LiveTvNeon.AccentGradient)
                } else {
                    Modifier.background(LiveTvNeon.SurfaceRaised).border(1.dp, LiveTvNeon.GridLineStrong, shape)
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        chip.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color(0xFF05040A) else LiveTvNeon.TextSecondary,
                modifier = Modifier.size(15.dp),
            )
        }
        Text(
            text = chip.label,
            style = TextStyle(
                fontSize = 13.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) Color(0xFF05040A) else LiveTvNeon.TextSecondary,
            ),
        )
    }
}
