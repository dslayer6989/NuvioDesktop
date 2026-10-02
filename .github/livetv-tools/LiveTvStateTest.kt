package com.nuvio.app.features.livetv

import com.nuvio.app.features.streams.StreamItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

private fun liveStream(
    name: String?,
    url: String?,
    title: String? = null,
    description: String? = null,
) = StreamItem(
    name = name,
    title = title,
    description = description,
    url = url,
    addonName = "Test addon",
    addonId = "addon:test",
)

/** The remembered working source goes first; everything else keeps the addon's order. */
class LiveTvStreamPreferenceTest {
    private val a = liveStream("Primary", "https://cdn.example/a.m3u8")
    private val b = liveStream("Backup 1", "https://cdn.example/b.m3u8")
    private val c = liveStream("Backup 2", "https://cdn.example/c.m3u8")
    private val lineup = listOf(a, b, c)

    @Test
    fun remembersTheSourceByLink() {
        val saved = LiveTvWorkingStream(url = "https://cdn.example/c.m3u8", label = null, savedAtMs = 1L)
        assertEquals(listOf(c, a, b), orderStreamsByPreference(lineup, saved))
    }

    @Test
    fun fallsBackToTheLabelWhenTheLinkChanged() {
        val saved = assertNotNull(
            LiveTvWorkingStream.of(liveStream("Backup 1", "https://cdn.example/b.m3u8?token=old"), nowMs = 1L),
        )
        val fresh = listOf(a, liveStream("Backup 1", "https://cdn.example/b.m3u8?token=new"), c)
        assertEquals(listOf(fresh[1], fresh[0], fresh[2]), orderStreamsByPreference(fresh, saved))
    }

    @Test
    fun aLinkMatchBeatsALabelMatch() {
        val first = liveStream("HD", "https://cdn.example/one.m3u8")
        val second = liveStream("HD", "https://cdn.example/two.m3u8")
        val saved = LiveTvWorkingStream(url = "https://cdn.example/two.m3u8", label = "HD", savedAtMs = 1L)
        assertEquals(listOf(second, first), orderStreamsByPreference(listOf(first, second), saved))
    }

    @Test
    fun keepsTheRestInAddonOrder() {
        val d = liveStream("Backup 3", "https://cdn.example/d.m3u8")
        val ordered = orderStreamsByPreference(listOf(a, b, c, d), LiveTvWorkingStream.of(c, nowMs = 1L))
        assertEquals(listOf(c, a, b, d), ordered)
    }

    @Test
    fun leavesTheOrderAloneWhenThereIsNothingToPrefer() {
        assertSame(lineup, orderStreamsByPreference(lineup, null))

        val gone = LiveTvWorkingStream(url = "https://cdn.example/gone.m3u8", label = "Gone", savedAtMs = 1L)
        assertSame(lineup, orderStreamsByPreference(lineup, gone))

        assertSame(lineup, orderStreamsByPreference(lineup, LiveTvWorkingStream.of(a, nowMs = 1L)))

        val single = listOf(a)
        assertSame(single, orderStreamsByPreference(single, LiveTvWorkingStream.of(a, nowMs = 1L)))
    }

    @Test
    fun theLabelJoinsNameTitleAndDescription() {
        val stream = liveStream("HD", "https://cdn.example/x.m3u8", title = "Feed", description = "Eastern")
        assertEquals("HD|Feed|Eastern", LiveTvWorkingStream.of(stream, nowMs = 1L)?.label)
    }

    @Test
    fun cannotRememberAStreamWithoutALinkOrLabel() {
        assertNull(LiveTvWorkingStream.of(liveStream(name = null, url = null), nowMs = 1L))
    }

    @Test
    fun sameSourceComparesLinkAndLabelOnly() {
        val one = LiveTvWorkingStream(url = "u", label = "l", savedAtMs = 1L)
        assertTrue(one.sameSourceAs(LiveTvWorkingStream(url = "u", label = "l", savedAtMs = 99L)))
        assertTrue(!one.sameSourceAs(LiveTvWorkingStream(url = "u2", label = "l", savedAtMs = 1L)))
    }
}

/** Per-profile favorites, recents and chip: loading, the old device-wide fallback, and clearing. */
class LiveTvProfileDataTest {
    private val loaded = LiveTvUiState(
        favoriteIds = listOf("a", "b"),
        recentIds = listOf("c", "d"),
    )

    @Test
    fun aProfilesOwnValueWinsOverTheOldDeviceWideOne() {
        assertEquals("sports", resolveProfileValue("sports", "news"))
    }

    @Test
    fun theOldDeviceWideValueIsTheStartingPoint() {
        assertEquals("news", resolveProfileValue(null, "news"))
    }

    @Test
    fun anEmptyProfileValueMeansAllChannels() {
        assertNull(resolveProfileValue("", null))
        assertNull(resolveProfileValue("", "news"))
    }

    @Test
    fun anEmptyOldValueAlsoMeansAllChannels() {
        assertNull(resolveProfileValue(null, ""))
    }

    @Test
    fun nothingSavedMeansNothingSelected() {
        assertNull(resolveProfileValue(null, null))
    }

    @Test
    fun channelListsUseTheProfilesOwnList() {
        assertEquals(listOf("a"), decodeProfileIds("[\"a\"]", "[\"b\"]"))
    }

    @Test
    fun channelListsStartFromTheOldDeviceWideList() {
        assertEquals(listOf("b", "c"), decodeProfileIds(null, "[\"b\",\"c\"]"))
    }

    @Test
    fun aListTheProfileEmptiedDoesNotComeBack() {
        assertEquals(emptyList<String>(), decodeProfileIds("[]", "[\"b\"]"))
    }

    @Test
    fun unreadableOrMissingListsReadAsEmpty() {
        assertEquals(emptyList<String>(), decodeProfileIds("not json", null))
        assertEquals(emptyList<String>(), decodeProfileIds(null, null))
    }

    @Test
    fun clearingFavoritesLeavesTheFavoritesChip() {
        val cleared = loaded.copy(selectedGroupId = LiveTvFavoritesGroupId).withoutFavorites()
        assertEquals(emptyList<String>(), cleared.favoriteIds)
        assertNull(cleared.selectedGroupId)
        assertEquals(listOf("c", "d"), cleared.recentIds)
    }

    @Test
    fun clearingFavoritesKeepsAnyOtherChip() {
        for (chip in listOf("sports", LiveTvRecentGroupId)) {
            assertEquals(chip, loaded.copy(selectedGroupId = chip).withoutFavorites().selectedGroupId)
        }
        assertNull(loaded.withoutFavorites().selectedGroupId)
    }

    @Test
    fun clearingRecentsLeavesTheRecentChip() {
        val cleared = loaded.copy(selectedGroupId = LiveTvRecentGroupId).withoutRecents()
        assertEquals(emptyList<String>(), cleared.recentIds)
        assertNull(cleared.selectedGroupId)
        assertEquals(listOf("a", "b"), cleared.favoriteIds)
    }

    @Test
    fun clearingRecentsKeepsAnyOtherChip() {
        for (chip in listOf("sports", LiveTvFavoritesGroupId)) {
            assertEquals(chip, loaded.copy(selectedGroupId = chip).withoutRecents().selectedGroupId)
        }
        assertNull(loaded.withoutRecents().selectedGroupId)
    }
}
