package com.nuvio.app.features.livetv

/**
 * Content type used for every Live TV playback launch.
 *
 * Watch progress, Continue Watching and scrobbling all skip this type, so live channels never
 * show up as half-watched movies.
 */
internal const val LiveTvContentType = "livetv"

internal fun String?.isLiveTvContentType(): Boolean =
    this != null && this.equals(LiveTvContentType, ignoreCase = true)

/** No addon is bundled; the user enters their own manifest URL on first launch. */
internal const val LiveTvDefaultManifestUrl = ""

/** Stremio content types that describe live channels. */
internal val LiveTvChannelTypes = setOf("tv", "channel", "live", "livetv", "events")

data class LiveTvChannel(
    val id: String,
    val type: String,
    val name: String,
    val logo: String? = null,
    val genres: List<String> = emptyList(),
    val description: String? = null,
    /** 1-based position in the addon's main lineup; 0 for channels only found in a side catalog. */
    val number: Int = 0,
)

data class LiveTvProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val startMs: Long,
    val endMs: Long,
    val description: String? = null,
    val thumbnail: String? = null,
    val genres: List<String> = emptyList(),
    val releaseInfo: String? = null,
) {
    val durationMs: Long
        get() = endMs - startMs

    fun isAiringAt(nowMs: Long): Boolean = nowMs >= startMs && nowMs < endMs

    fun progressAt(nowMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return ((nowMs - startMs).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    }
}

data class LiveTvGroup(
    val id: String,
    val name: String,
    val channelIds: List<String>,
)

data class LiveTvGuide(
    val manifestUrl: String,
    val addonId: String,
    val addonName: String,
    val addonLogo: String?,
    /** Main lineup, in addon order. */
    val lineupIds: List<String>,
    /** Every channel we know about, including ones that only appear in a side catalog. */
    val channelsById: Map<String, LiveTvChannel>,
    val programsByChannel: Map<String, List<LiveTvProgram>>,
    val groups: List<LiveTvGroup>,
    val hasGuideData: Boolean,
    val loadedAtMs: Long,
) {
    val lineup: List<LiveTvChannel>
        get() = lineupIds.mapNotNull(channelsById::get)

    fun channel(id: String): LiveTvChannel? = channelsById[id]

    fun programs(channelId: String): List<LiveTvProgram> = programsByChannel[channelId].orEmpty()

    fun programAt(channelId: String, atMs: Long): LiveTvProgram? =
        programs(channelId).firstOrNull { it.isAiringAt(atMs) }

    fun nextProgram(channelId: String, afterMs: Long): LiveTvProgram? =
        programs(channelId).firstOrNull { it.startMs >= afterMs }

    fun channelsFor(groupId: String?): List<LiveTvChannel> {
        if (groupId == null) return lineup
        val group = groups.firstOrNull { it.id == groupId } ?: return lineup
        return group.channelIds.mapNotNull(channelsById::get)
    }
}

/** Addon manifest details that matter to Live TV. */
data class LiveTvAddonInfo(
    val id: String,
    val name: String,
    val logo: String?,
    val description: String?,
    val epgProvider: Boolean,
    val types: List<String>,
    val resources: List<String>,
    val catalogs: List<LiveTvCatalogInfo>,
) {
    fun supportsResource(name: String): Boolean =
        resources.isEmpty() || resources.any { it.equals(name, ignoreCase = true) }
}

data class LiveTvCatalogInfo(
    val type: String,
    val id: String,
    val name: String,
    val extras: List<LiveTvCatalogExtra> = emptyList(),
) {
    val supportsDate: Boolean
        get() = extras.any { it.name.equals(LiveTvExtraDate, ignoreCase = true) }

    val supportsSkip: Boolean
        get() = extras.any { it.name.equals(LiveTvExtraSkip, ignoreCase = true) }

    /**
     * Values for required extras we can satisfy automatically (the first declared option).
     * `date` and `skip` are supplied by the loader, so they never block a catalog.
     * Returns null when a required extra has no options (for example `search`).
     */
    fun requiredExtraDefaults(): Map<String, String>? {
        val values = LinkedHashMap<String, String>()
        for (extra in extras) {
            if (!extra.isRequired) continue
            if (extra.name.equals(LiveTvExtraDate, ignoreCase = true)) continue
            if (extra.name.equals(LiveTvExtraSkip, ignoreCase = true)) continue
            val option = extra.options.firstOrNull() ?: return null
            values[extra.name] = option
        }
        return values
    }

    val isRequestable: Boolean
        get() = requiredExtraDefaults() != null
}

data class LiveTvCatalogExtra(
    val name: String,
    val isRequired: Boolean = false,
    val options: List<String> = emptyList(),
)

internal const val LiveTvExtraDate = "date"
internal const val LiveTvExtraSkip = "skip"
