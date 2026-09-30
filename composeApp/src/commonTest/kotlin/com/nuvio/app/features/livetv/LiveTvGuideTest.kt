package com.nuvio.app.features.livetv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiveTvGuideTest {
    private val manifestUrl = "https://example.github.io/Stremio-TV/manifest.json"

    // Mirrors the real Stremio-TV manifest: native EPG catalog plus genre-gated category catalogs.
    private val manifest = """
        {"id":"com.stremiotv.philly","version":"0.6.9","name":"Stremio TV",
         "resources":["catalog","meta","stream"],"types":["tv"],"idPrefixes":["stremiotv_"],
         "catalogs":[
           {"type":"tv","id":"channels","name":"Stremio TV","extra":[{"name":"skip"},{"name":"date"}]},
           {"type":"tv","id":"favorites","name":"★ Favorites","extra":[{"name":"genre","isRequired":true,"options":["All"]},{"name":"skip"}]},
           {"type":"tv","id":"news","name":"National News","extra":[{"name":"genre","isRequired":true,"options":["All"]},{"name":"skip"}]},
           {"type":"tv","id":"search","name":"Search","extra":[{"name":"search","isRequired":true}]}
         ],
         "behaviorHints":{"p2p":false,"epgProvider":true},
         "logo":"https://example.github.io/Stremio-TV/icon.svg"}
    """.trimIndent()

    private val datePage = """
        {"metasDetailed":[
          {"id":"stremiotv_cnn","type":"tv","name":"CNN","genres":["News"],"posterShape":"square",
           "behaviorHints":{"isLive":true,"hasScheduledVideos":true},
           "poster":"https://example/cnn.png",
           "videos":[
             {"id":"stremiotv_cnn:epg:2026-09-29T12:00:00Z","title":"Live: CNN News Central",
              "released":"2026-09-29T12:00:00Z","startTime":"2026-09-29T12:00:00Z","endTime":"2026-09-29T13:00:00Z",
              "runtime":"60 min","overview":"The latest news.","thumbnail":"http://img/1.jpg","genres":["Series","News"]},
             {"id":"stremiotv_cnn:epg:2026-09-29T13:00:00Z","title":"Live: CNN News Central",
              "startTime":"2026-09-29T13:00:00Z","endTime":"2026-09-29T14:00:00Z"}
           ]},
          {"id":"stremiotv_espnews","type":"tv","name":"ESPNews","genres":["Sports"],
           "behaviorHints":{"isLive":true,"hasScheduledVideos":true},
           "videos":[
             {"title":"30 for 30","startTime":"2026-09-29T11:30:00Z","endTime":"2026-09-29T13:00:00Z"},
             {"title":"SportsCenter","released":"2026-09-29T13:00:00Z","runtime":"60 min"}
           ]},
          {"id":"stremiotv_local","type":"tv","name":"WJAC","behaviorHints":{"isLive":true}}
        ]}
    """.trimIndent()

    @Test
    fun parsesNativeEpgManifest() {
        val info = LiveTvParser.parseManifest(manifest)
        assertTrue(info.epgProvider)
        assertEquals("Stremio TV", info.name)
        assertEquals(listOf("catalog", "meta", "stream"), info.resources)

        val plan = assertNotNull(LiveTvCatalogPlan.from(info))
        assertEquals("channels", plan.lineup.id)
        assertTrue(plan.usesDateEpg)
        // The search catalog cannot be requested without a query, so it is not a chip.
        assertEquals(listOf("favorites", "news"), plan.groups.map { it.id })
        assertEquals(mapOf("genre" to "All"), plan.groups.first().requiredExtraDefaults())
    }

    @Test
    fun buildsCatalogUrlsInManifestExtraOrder() {
        val plan = assertNotNull(LiveTvCatalogPlan.from(LiveTvParser.parseManifest(manifest)))
        assertEquals(
            "https://example.github.io/Stremio-TV/catalog/tv/channels/date=2026-09-29.json",
            buildLiveTvCatalogUrl(manifestUrl, plan.lineup, mapOf("date" to "2026-09-29")),
        )
        assertEquals(
            "https://example.github.io/Stremio-TV/catalog/tv/channels/skip=100&date=2026-09-29.json",
            buildLiveTvCatalogUrl(manifestUrl, plan.lineup, mapOf("date" to "2026-09-29", "skip" to "100")),
        )
        assertEquals(
            "https://example.github.io/Stremio-TV/catalog/tv/favorites/genre=All.json",
            buildLiveTvCatalogUrl(manifestUrl, plan.groups.first(), mapOf("genre" to "All")),
        )
        assertEquals(
            "https://example.github.io/Stremio-TV/catalog/tv/channels.json",
            buildLiveTvCatalogUrl(manifestUrl, plan.lineup, emptyMap()),
        )
    }

    @Test
    fun parsesScheduleFromMetasDetailed() {
        val channels = LiveTvParser.parseCatalog(datePage, fallbackType = "tv")
        assertEquals(listOf("CNN", "ESPNews", "WJAC"), channels.map { it.channel.name })
        assertEquals("https://example/cnn.png", channels[0].channel.logo)
        assertEquals(2, channels[0].programs.size)
        assertEquals("The latest news.", channels[0].programs[0].description)
        assertEquals(LiveTvTime.parseInstant("2026-09-29T13:00:00Z"), channels[0].programs[0].endMs)
        // `released` + `runtime` is accepted because the meta declares hasScheduledVideos.
        val sportsCenter = channels[1].programs[1]
        assertEquals(LiveTvTime.parseInstant("2026-09-29T13:00:00Z"), sportsCenter.startMs)
        assertEquals(LiveTvTime.parseInstant("2026-09-29T14:00:00Z"), sportsCenter.endMs)
        assertTrue(channels[2].programs.isEmpty())
    }

    @Test
    fun ignoresReleasedDatesWithoutScheduleHint() {
        val page = """{"metas":[{"id":"c1","type":"channel","name":"Uploads",
            "videos":[{"title":"Clip","released":"2020-01-01T00:00:00Z"}]}]}"""
        val parsed = LiveTvParser.parseCatalog(page, fallbackType = "tv")
        assertTrue(parsed.single().programs.isEmpty())
    }

    @Test
    fun builderMergesDatePagesAndGroups() {
        val info = LiveTvParser.parseManifest(manifest)
        val builder = LiveTvGuideBuilder()
        builder.addLineup(LiveTvParser.parseCatalog(datePage, "tv"))
        // The same programme appears again on the next UTC day's page; it must not duplicate.
        builder.addLineup(LiveTvParser.parseCatalog(datePage, "tv"))
        builder.addGroup(
            id = "favorites",
            name = "★ Favorites",
            items = LiveTvParser.parseCatalog("""{"metas":[{"id":"stremiotv_cnn","type":"tv","name":"CNN"},
                {"id":"stremiotv_test","type":"tv","name":"Local Test"}]}""", "tv"),
        )
        val guide = builder.build(info, manifestUrl, loadedAtMs = 0L)

        assertEquals(listOf("stremiotv_cnn", "stremiotv_espnews", "stremiotv_local"), guide.lineupIds)
        assertEquals(1, guide.channel("stremiotv_cnn")?.number)
        assertEquals(0, guide.channel("stremiotv_test")?.number)
        assertEquals(2, guide.programs("stremiotv_cnn").size)
        assertTrue(guide.hasGuideData)
        assertEquals(listOf("stremiotv_cnn", "stremiotv_test"), guide.channelsFor("favorites").map { it.id })
        assertEquals(3, guide.channelsFor(null).size)

        val at1230 = assertNotNull(LiveTvTime.parseInstant("2026-09-29T12:30:00Z"))
        assertEquals("Live: CNN News Central", guide.programAt("stremiotv_cnn", at1230)?.title)
        assertEquals("30 for 30", guide.programAt("stremiotv_espnews", at1230)?.title)
        assertNull(guide.programAt("stremiotv_local", at1230))
        assertEquals("SportsCenter", guide.nextProgram("stremiotv_espnews", at1230)?.title)
    }

    @Test
    fun derivesGenreChipsWhenAddonHasNoCategoryCatalogs() {
        val info = LiveTvParser.parseManifest(
            """{"id":"x","name":"X","catalogs":[{"type":"tv","id":"all","name":"All"}]}""",
        )
        val plan = assertNotNull(LiveTvCatalogPlan.from(info))
        assertFalse(plan.usesDateEpg)
        assertTrue(plan.groups.isEmpty())
        val builder = LiveTvGuideBuilder()
        builder.addLineup(LiveTvParser.parseCatalog(datePage, "tv"))
        val guide = builder.build(info, manifestUrl, 0L)
        assertEquals(listOf("News", "Sports"), guide.groups.map { it.name })
    }

    @Test
    fun finalizeFillsMissingEndsAndTrimsOverlaps() {
        val base = 1_000_000_000_000L
        val programs = finalizePrograms(
            "c",
            listOf(
                ParsedLiveProgram("b", "B", base + 30 * LiveTvTime.MINUTE_MS, null, null, null, emptyList(), null),
                ParsedLiveProgram("a", "A", base, base + 45 * LiveTvTime.MINUTE_MS, null, null, emptyList(), null),
                ParsedLiveProgram("c", "C", base + 90 * LiveTvTime.MINUTE_MS, null, null, null, emptyList(), null),
            ),
        )
        assertEquals(listOf("A", "B", "C"), programs.map { it.title })
        assertEquals(base + 30 * LiveTvTime.MINUTE_MS, programs[0].endMs) // trimmed to next start
        assertEquals(base + 90 * LiveTvTime.MINUTE_MS, programs[1].endMs) // filled from next start
        assertEquals(base + 150 * LiveTvTime.MINUTE_MS, programs[2].endMs) // default length
    }

    @Test
    fun timeHelpers() {
        assertEquals(0L, LiveTvTime.parseInstant("1970-01-01T00:00:00Z"))
        assertEquals(1790683200000L, LiveTvTime.parseInstant("2026-09-29T12:00:00Z"))
        assertEquals(1790683200000L, LiveTvTime.parseInstant("2026-09-29T08:00:00-04:00"))
        assertEquals(1790683200500L, LiveTvTime.parseInstant("2026-09-29T12:00:00.5Z"))
        assertEquals(1790683200000L, LiveTvTime.parseInstant("20260929120000 +0000"))
        assertNull(LiveTvTime.parseInstant("not a date"))
        assertEquals("2026-09-29", LiveTvTime.utcDate(1790683200000L))
        assertEquals("1969-12-31", LiveTvTime.utcDate(-1L))
        assertEquals(listOf("2026-09-29", "2026-09-30"), LiveTvTime.utcDatesBetween(1790683200000L, 1790683200000L + LiveTvTime.DAY_MS - 1))
        assertEquals(60, LiveTvTime.parseRuntimeMinutes("60 min"))
        assertEquals(90, LiveTvTime.parseRuntimeMinutes("1h 30m"))
        assertEquals(90, LiveTvTime.parseRuntimeMinutes("PT1H30M"))
        assertEquals(75, LiveTvTime.parseRuntimeMinutes("1:15"))
        assertEquals(1790683200000L, LiveTvTime.floorToHalfHour(1790683200000L + 29 * LiveTvTime.MINUTE_MS))
    }

    @Test
    fun guideWindowCoversLookBackAndSchedule() {
        val now = assertNotNull(LiveTvTime.parseInstant("2026-09-29T12:40:00Z"))
        val empty = liveTvGuideWindow(null, now)
        assertEquals(LiveTvTime.parseInstant("2026-09-29T11:30:00Z"), empty.startMs)
        assertEquals(empty.startMs + LiveTvGuideMinSpanMs, empty.endMs)
        assertEquals(12, empty.slots.size)
        assertTrue(empty.slots.all { (it - empty.startMs) % LiveTvTime.HALF_HOUR_MS == 0L })
    }

    @Test
    fun channelNavigationWraps() {
        val list = listOf("a", "b", "c").map { LiveTvChannel(id = it, type = "tv", name = it) }
        assertEquals("b", list.neighborOf("a", 1)?.id)
        assertEquals("c", list.neighborOf("a", -1)?.id)
        assertEquals("a", list.neighborOf("c", 1)?.id)
        assertEquals("a", list.neighborOf("missing", 1)?.id)
    }

    @Test
    fun onNowSorting() {
        val info = LiveTvParser.parseManifest(manifest)
        val builder = LiveTvGuideBuilder()
        builder.addLineup(LiveTvParser.parseCatalog(datePage, "tv"))
        val guide = builder.build(info, manifestUrl, 0L)
        val at1230 = assertNotNull(LiveTvTime.parseInstant("2026-09-29T12:30:00Z"))
        val endingSoon = guide.onNow(guide.lineup, at1230, LiveTvOnNowSort.EndingSoon)
        assertEquals(listOf("CNN", "ESPNews", "WJAC"), endingSoon.map { it.channel.name })
        val justStarted = guide.onNow(guide.lineup, at1230, LiveTvOnNowSort.JustStarted)
        assertEquals(listOf("CNN", "ESPNews", "WJAC"), justStarted.map { it.channel.name })
        assertEquals("Live: CNN News Central", justStarted.first().next?.title)
    }
}
