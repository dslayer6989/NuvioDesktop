package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.encodeAddonPathSegment

internal const val LiveTvMaxGroupCatalogs = 24
internal const val LiveTvDefaultProgramMs = 60 * LiveTvTime.MINUTE_MS

/** Which catalogs to read for the lineup, the schedule and the filter chips. */
internal data class LiveTvCatalogPlan(
    val lineup: LiveTvCatalogInfo,
    /** True when [lineup] accepts Stremio's native EPG `date` extra. */
    val usesDateEpg: Boolean,
    val groups: List<LiveTvCatalogInfo>,
) {
    companion object {
        fun from(info: LiveTvAddonInfo): LiveTvCatalogPlan? {
            val live = info.catalogs
                .filter { it.type.lowercase() in LiveTvChannelTypes }
                .ifEmpty { info.catalogs }
            val requestable = live.filter { it.isRequestable }
            val guide = requestable.firstOrNull { it.supportsDate }
            val lineup = guide
                ?: requestable.firstOrNull { it.requiredExtraDefaults().isNullOrEmpty() }
                ?: requestable.firstOrNull()
                ?: return null
            val groups = requestable
                .filter { it.type != lineup.type || it.id != lineup.id }
                .take(LiveTvMaxGroupCatalogs)
            return LiveTvCatalogPlan(lineup = lineup, usesDateEpg = guide != null, groups = groups)
        }
    }
}

/**
 * Builds a Stremio catalog URL. Extras follow the manifest's declaration order (how Stremio
 * itself orders them), which matters for static addons that pre-render each combination.
 */
internal fun buildLiveTvCatalogUrl(
    manifestUrl: String,
    catalog: LiveTvCatalogInfo,
    extras: Map<String, String>,
): String {
    val remaining = extras.filterValues { it.isNotBlank() }.toMutableMap()
    val ordered = ArrayList<Pair<String, String>>()
    catalog.extras.forEach { declared ->
        val key = remaining.keys.firstOrNull { it.equals(declared.name, ignoreCase = true) } ?: return@forEach
        ordered += declared.name to remaining.remove(key).orEmpty()
    }
    remaining.forEach { (key, value) -> ordered += key to value }
    val segment = ordered.joinToString("&") { (key, value) -> "$key=${value.encodeAddonPathSegment()}" }
        .takeIf { it.isNotEmpty() }
    return buildAddonResourceUrl(
        manifestUrl = manifestUrl,
        resource = "catalog",
        type = catalog.type,
        id = catalog.id,
        extraPathSegment = segment,
    )
}

/** Accumulates catalog pages into a [LiveTvGuide]. */
internal class LiveTvGuideBuilder {
    private val channels = LinkedHashMap<String, LiveTvChannel>()
    private val lineup = LinkedHashSet<String>()
    private val programs = HashMap<String, LinkedHashMap<Long, ParsedLiveProgram>>()
    private val groups = ArrayList<LiveTvGroup>()

    val lineupSize: Int
        get() = lineup.size

    fun lineupIds(): List<String> = lineup.toList()

    fun knows(channelId: String): Boolean = channelId in channels

    fun hasPrograms(channelId: String): Boolean = programs[channelId]?.isNotEmpty() == true

    fun channel(channelId: String): LiveTvChannel? = channels[channelId]

    fun addLineup(items: List<ParsedLiveChannel>) {
        items.forEach { item ->
            lineup += item.channel.id
            mergeChannel(item.channel)
            addPrograms(item.channel.id, item.programs)
        }
    }

    fun addGroup(id: String, name: String, items: List<ParsedLiveChannel>) {
        items.forEach { item ->
            mergeChannel(item.channel)
            addPrograms(item.channel.id, item.programs)
        }
        val ids = items.map { it.channel.id }.distinct()
        if (ids.isEmpty()) return
        val existingIndex = groups.indexOfFirst { it.id == id }
        if (existingIndex >= 0) {
            val merged = (groups[existingIndex].channelIds + ids).distinct()
            groups[existingIndex] = groups[existingIndex].copy(channelIds = merged)
        } else {
            groups += LiveTvGroup(id = id, name = name, channelIds = ids)
        }
    }

    fun addPrograms(channelId: String, items: List<ParsedLiveProgram>) {
        if (items.isEmpty()) return
        val byStart = programs.getOrPut(channelId) { LinkedHashMap() }
        items.forEach { program ->
            val existing = byStart[program.startMs]
            if (existing == null || existing.isLessCompleteThan(program)) {
                byStart[program.startMs] = program
            }
        }
    }

    fun build(
        info: LiveTvAddonInfo,
        manifestUrl: String,
        loadedAtMs: Long,
    ): LiveTvGuide {
        val numbered = LinkedHashMap<String, LiveTvChannel>()
        lineup.forEachIndexed { index, id ->
            channels[id]?.let { numbered[id] = it.copy(number = index + 1) }
        }
        channels.forEach { (id, channel) -> if (id !in numbered) numbered[id] = channel.copy(number = 0) }

        val finalized = programs.mapValues { (channelId, byStart) -> finalizePrograms(channelId, byStart.values) }
            .filterValues { it.isNotEmpty() }

        val resolvedGroups = if (groups.isNotEmpty()) groups.toList() else deriveGenreGroups(numbered)

        return LiveTvGuide(
            manifestUrl = manifestUrl,
            addonId = info.id,
            addonName = info.name,
            addonLogo = info.logo,
            lineupIds = lineup.toList(),
            channelsById = numbered,
            programsByChannel = finalized,
            groups = resolvedGroups,
            hasGuideData = finalized.isNotEmpty(),
            loadedAtMs = loadedAtMs,
        )
    }

    private fun mergeChannel(channel: LiveTvChannel) {
        val existing = channels[channel.id]
        channels[channel.id] = if (existing == null) {
            channel
        } else {
            existing.copy(
                logo = existing.logo ?: channel.logo,
                genres = existing.genres.ifEmpty { channel.genres },
                description = existing.description ?: channel.description,
            )
        }
    }

    private fun deriveGenreGroups(channelsById: Map<String, LiveTvChannel>): List<LiveTvGroup> {
        val byGenre = LinkedHashMap<String, MutableList<String>>()
        lineup.forEach { id ->
            channelsById[id]?.genres?.firstOrNull()?.let { genre ->
                byGenre.getOrPut(genre) { ArrayList() } += id
            }
        }
        if (byGenre.size < 2) return emptyList()
        return byGenre.map { (genre, ids) -> LiveTvGroup(id = "genre:$genre", name = genre, channelIds = ids) }
    }
}

private fun ParsedLiveProgram.isLessCompleteThan(other: ParsedLiveProgram): Boolean {
    if (endMs == null && other.endMs != null) return true
    if (description == null && other.description != null) return true
    return thumbnail == null && other.thumbnail != null && description == other.description
}

/** Sorts a channel's schedule, fills missing end times and trims overlaps. */
internal fun finalizePrograms(
    channelId: String,
    parsed: Collection<ParsedLiveProgram>,
): List<LiveTvProgram> {
    val sorted = parsed.sortedBy { it.startMs }
    val result = ArrayList<LiveTvProgram>(sorted.size)
    sorted.forEachIndexed { index, program ->
        val next = sorted.getOrNull(index + 1)
        var end = program.endMs ?: next?.startMs ?: (program.startMs + LiveTvDefaultProgramMs)
        if (next != null && next.startMs > program.startMs && end > next.startMs) {
            end = next.startMs
        }
        if (end <= program.startMs) return@forEachIndexed
        result += LiveTvProgram(
            id = program.id,
            channelId = channelId,
            title = program.title,
            startMs = program.startMs,
            endMs = end,
            description = program.description,
            thumbnail = program.thumbnail,
            genres = program.genres,
            releaseInfo = program.releaseInfo,
        )
    }
    return result
}

/** The visible time range of the guide grid. */
internal data class LiveTvGuideWindow(
    val startMs: Long,
    val endMs: Long,
) {
    val slots: List<Long>
        get() = LiveTvTime.halfHourSlots(startMs, endMs)
}

internal const val LiveTvGuideLookBackMs = LiveTvTime.HOUR_MS
internal const val LiveTvGuideMaxSpanMs = 48 * LiveTvTime.HOUR_MS
internal const val LiveTvGuideMinSpanMs = 6 * LiveTvTime.HOUR_MS

/**
 * Starts one hour before the current half hour and ends at the last scheduled program
 * (clamped to 6–48 hours), rounded up to a half-hour boundary.
 */
internal fun liveTvGuideWindow(guide: LiveTvGuide?, nowMs: Long): LiveTvGuideWindow {
    val start = LiveTvTime.floorToHalfHour(nowMs) - LiveTvGuideLookBackMs
    val lastEnd = guide?.programsByChannel?.values?.maxOfOrNull { list -> list.lastOrNull()?.endMs ?: 0L } ?: 0L
    val span = (lastEnd - start).coerceIn(LiveTvGuideMinSpanMs, LiveTvGuideMaxSpanMs)
    val end = LiveTvTime.floorToHalfHour(start + span + LiveTvTime.HALF_HOUR_MS - 1)
    return LiveTvGuideWindow(startMs = start, endMs = end)
}

/** Channel up/down with wrap-around. */
internal fun List<LiveTvChannel>.neighborOf(channelId: String, delta: Int): LiveTvChannel? {
    if (isEmpty()) return null
    val index = indexOfFirst { it.id == channelId }
    if (index < 0) return first()
    val size = size
    return this[((index + delta) % size + size) % size]
}

enum class LiveTvOnNowSort {
    Channel,
    EndingSoon,
    JustStarted,
}

data class LiveTvOnNowEntry(
    val channel: LiveTvChannel,
    val current: LiveTvProgram?,
    val next: LiveTvProgram?,
)

internal fun LiveTvGuide.onNow(
    channels: List<LiveTvChannel>,
    nowMs: Long,
    sort: LiveTvOnNowSort,
): List<LiveTvOnNowEntry> {
    val entries = channels.map { channel ->
        val current = programAt(channel.id, nowMs)
        LiveTvOnNowEntry(
            channel = channel,
            current = current,
            next = nextProgram(channel.id, current?.endMs ?: nowMs),
        )
    }
    return when (sort) {
        LiveTvOnNowSort.Channel -> entries
        LiveTvOnNowSort.EndingSoon -> entries.sortedWith(
            compareBy<LiveTvOnNowEntry> { it.current == null }.thenBy { it.current?.endMs ?: Long.MAX_VALUE },
        )
        LiveTvOnNowSort.JustStarted -> entries.sortedWith(
            compareBy<LiveTvOnNowEntry> { it.current == null }.thenByDescending { it.current?.startMs ?: Long.MIN_VALUE },
        )
    }
}
