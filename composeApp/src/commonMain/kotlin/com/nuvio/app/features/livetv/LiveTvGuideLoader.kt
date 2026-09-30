package com.nuvio.app.features.livetv

import com.nuvio.app.features.addons.buildAddonResourceUrl
import com.nuvio.app.features.addons.fetchAddonResponseText
import com.nuvio.app.features.streams.StreamItem
import com.nuvio.app.features.streams.StreamParser
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

internal const val LiveTvMaxCatalogPages = 20
internal const val LiveTvMaxGroupPages = 5
internal const val LiveTvMaxMetaFallbackChannels = 250
internal const val LiveTvMetaFallbackConcurrency = 6

/** Fetches an addon URL as text; injectable so the loader can be tested without a network. */
internal typealias LiveTvFetcher = suspend (url: String, forceRefresh: Boolean) -> String

internal val LiveTvNetworkFetcher: LiveTvFetcher = { url, forceRefresh -> fetchAddonResponseText(url, forceRefresh) }

/** Loads a full guide (lineup, schedule, chips) from a Stremio Live TV addon. */
internal object LiveTvGuideLoader {

    suspend fun load(
        manifestUrl: String,
        nowMs: Long,
        forceRefresh: Boolean,
        fetch: LiveTvFetcher = LiveTvNetworkFetcher,
    ): LiveTvGuide = coroutineScope {
        val info = LiveTvParser.parseManifest(fetch(manifestUrl, forceRefresh))
        val plan = LiveTvCatalogPlan.from(info)
            ?: error("This addon has no channel catalogs Nuvio can read.")
        val builder = LiveTvGuideBuilder()
        val lineupBase = plan.lineup.requiredExtraDefaults().orEmpty()

        if (plan.usesDateEpg) {
            // Stremio's native EPG serves one page set per UTC day; fetch every day the guide shows.
            val window = liveTvGuideWindow(guide = null, nowMs = nowMs)
            val dates = LiveTvTime.utcDatesBetween(window.startMs, window.startMs + LiveTvGuideMaxSpanMs)
            val pages = dates.map { date ->
                async {
                    runCatching {
                        fetchAllPages(manifestUrl, plan.lineup, lineupBase + (LiveTvExtraDate to date), LiveTvMaxCatalogPages, forceRefresh, fetch)
                    }.getOrNull()
                }
            }.awaitAll()
            // Today's page decides the lineup order; later days only add programmes (and any new channels).
            pages.filterNotNull().forEach(builder::addLineup)
        }

        if (builder.lineupSize == 0) {
            builder.addLineup(fetchAllPages(manifestUrl, plan.lineup, lineupBase, LiveTvMaxCatalogPages, forceRefresh, fetch))
        }

        val groupJob = async { loadGroups(manifestUrl, plan, forceRefresh, fetch) }

        // Addons without the date extra may still carry schedules on their meta endpoint.
        if (info.supportsResource("meta")) {
            val missing = builder.lineupIds().filterNot(builder::hasPrograms).take(LiveTvMaxMetaFallbackChannels)
            val needsMetaFallback = !plan.usesDateEpg && missing.isNotEmpty()
            if (needsMetaFallback) {
                val semaphore = Semaphore(LiveTvMetaFallbackConcurrency)
                missing.map { channelId ->
                    async {
                        semaphore.withPermit {
                            val channel = builder.channel(channelId) ?: return@withPermit null
                            runCatching {
                                val url = buildAddonResourceUrl(manifestUrl, "meta", channel.type, channel.id)
                                LiveTvParser.parseMetaResponse(fetch(url, forceRefresh), channel.type)
                            }.getOrNull()
                        }
                    }
                }.awaitAll().filterNotNull().forEach { parsed ->
                    builder.addPrograms(parsed.channel.id, parsed.programs)
                }
            }
        }

        groupJob.await().forEach { (catalog, items) ->
            builder.addGroup(id = catalog.id, name = catalog.name, items = items)
        }

        if (builder.lineupSize == 0) error("The addon returned no channels.")
        builder.build(info = info, manifestUrl = manifestUrl, loadedAtMs = nowMs)
    }

    /** Streams for a channel, in the addon's order (Primary first), keeping only directly playable ones. */
    suspend fun loadStreams(
        manifestUrl: String,
        guide: LiveTvGuide,
        channel: LiveTvChannel,
        fetch: LiveTvFetcher = LiveTvNetworkFetcher,
    ): List<StreamItem> {
        val url = buildAddonResourceUrl(manifestUrl, "stream", channel.type, channel.id)
        val payload = fetch(url, true)
        return StreamParser.parse(
            payload = payload,
            addonName = guide.addonName,
            addonId = guide.addonId,
            addonLogo = guide.addonLogo,
        ).filter { it.playableDirectUrl != null }
    }

    private suspend fun loadGroups(
        manifestUrl: String,
        plan: LiveTvCatalogPlan,
        forceRefresh: Boolean,
        fetch: LiveTvFetcher,
    ): List<Pair<LiveTvCatalogInfo, List<ParsedLiveChannel>>> = coroutineScope {
        plan.groups.map { catalog ->
            async {
                runCatching {
                    catalog to fetchAllPages(
                        manifestUrl = manifestUrl,
                        catalog = catalog,
                        baseExtras = catalog.requiredExtraDefaults().orEmpty(),
                        maxPages = LiveTvMaxGroupPages,
                        forceRefresh = forceRefresh,
                        fetch = fetch,
                    )
                }.getOrNull()
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun fetchAllPages(
        manifestUrl: String,
        catalog: LiveTvCatalogInfo,
        baseExtras: Map<String, String>,
        maxPages: Int,
        forceRefresh: Boolean,
        fetch: LiveTvFetcher,
    ): List<ParsedLiveChannel> {
        val results = ArrayList<ParsedLiveChannel>()
        val seen = HashSet<String>()
        var skip = 0
        for (page in 0 until maxPages) {
            val extras = if (skip > 0) baseExtras + (LiveTvExtraSkip to skip.toString()) else baseExtras
            val url = buildLiveTvCatalogUrl(manifestUrl, catalog, extras)
            val items = try {
                LiveTvParser.parseCatalog(fetch(url, forceRefresh), catalog.type)
            } catch (error: Throwable) {
                // The first page must work; a failing later page (often a missing terminal page) just ends paging.
                if (page == 0) throw error else break
            }
            if (items.isEmpty()) break
            val fresh = items.filter { seen.add(it.channel.id) }
            if (fresh.isEmpty()) break // addon ignored `skip`
            results += fresh
            if (!catalog.supportsSkip) break
            skip += items.size
        }
        return results
    }
}
