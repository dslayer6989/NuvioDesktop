package com.nuvio.app.features.livetv

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Simulates a static GitHub Pages addon like Stremio-TV, which pre-renders every catalog URL. */
class LiveTvGuideLoaderTest {
    private val base = "https://example.github.io/Stremio-TV"
    private val manifestUrl = "$base/manifest.json"
    private val now = 1790683200000L + 30 * LiveTvTime.MINUTE_MS // 2026-09-29T12:30:00Z

    private val manifest = """
        {"id":"com.stremiotv.philly","name":"Stremio TV","resources":["catalog","meta","stream"],"types":["tv"],
         "catalogs":[
           {"type":"tv","id":"channels","name":"Stremio TV","extra":[{"name":"skip"},{"name":"date"}]},
           {"type":"tv","id":"favorites","name":"★ Favorites","extra":[{"name":"genre","isRequired":true,"options":["All"]},{"name":"skip"}]},
           {"type":"tv","id":"sports","name":"Sports","extra":[{"name":"genre","isRequired":true,"options":["All"]},{"name":"skip"}]},
           {"type":"tv","id":"local-test","name":"Local Test","extra":[{"name":"skip"}]}
         ],
         "behaviorHints":{"epgProvider":true}}
    """.trimIndent()

    private fun meta(id: String, name: String, vararg programs: Pair<String, String>): String {
        val videos = programs.joinToString(",") { (start, title) ->
            val startMs = LiveTvTime.parseInstant(start)!!
            val end = LiveTvTime.utcDate(startMs + LiveTvTime.HOUR_MS) + "T" +
                start.substringAfter("T").let { time ->
                    val hour = time.take(2).toInt()
                    "${((hour + 1) % 24).toString().padStart(2, '0')}${time.drop(2)}"
                }
            """{"title":"$title","startTime":"$start","endTime":"$end"}"""
        }
        return """{"id":"$id","type":"tv","name":"$name","genres":["News"],"poster":"$base/$id.png",
            "behaviorHints":{"isLive":true,"hasScheduledVideos":true},"videos":[$videos]}"""
    }

    private fun responses(): Map<String, String> {
        val day1 = "2026-09-29"
        val day2 = "2026-09-30"
        val page1Day1 = """{"metasDetailed":[${meta("stremiotv_cnn", "CNN", "${day1}T12:00:00Z" to "News Central", "${day1}T13:00:00Z" to "Situation Room")}]}"""
        val page2Day1 = """{"metasDetailed":[${meta("stremiotv_espn", "ESPN", "${day1}T12:00:00Z" to "SportsCenter")}]}"""
        val page1Day2 = """{"metasDetailed":[${meta("stremiotv_cnn", "CNN", "${day2}T00:00:00Z" to "Overnight")}]}"""
        val empty = """{"metasDetailed":[]}"""
        return mapOf(
            manifestUrl to manifest,
            "$base/catalog/tv/channels/date=$day1.json" to page1Day1,
            "$base/catalog/tv/channels/skip=1&date=$day1.json" to page2Day1,
            "$base/catalog/tv/channels/skip=2&date=$day1.json" to empty,
            "$base/catalog/tv/channels/date=$day2.json" to page1Day2,
            "$base/catalog/tv/channels/skip=1&date=$day2.json" to empty,
            // day 3 is intentionally missing (404) — the loader must tolerate it.
            "$base/catalog/tv/favorites/genre=All.json" to """{"metas":[{"id":"stremiotv_espn","type":"tv","name":"ESPN"}]}""",
            "$base/catalog/tv/favorites/genre=All&skip=1.json" to """{"metas":[]}""",
            "$base/catalog/tv/sports/genre=All.json" to """{"metas":[{"id":"stremiotv_espn","type":"tv","name":"ESPN"}]}""",
            "$base/catalog/tv/local-test.json" to """{"metas":[{"id":"stremiotv_test","type":"tv","name":"Test Feed"}]}""",
            "$base/stream/tv/stremiotv_cnn.json" to """{"streams":[
                {"name":"Primary • 720p","url":"https://cdn.example/cnn.m3u8"},
                {"name":"Magnet","url":"magnet:?xt=urn:btih:abc"},
                {"name":"Backup 1 • 1080p","url":"https://backup.example/cnn.m3u8",
                 "behaviorHints":{"notWebReady":true,"proxyHeaders":{"request":{"Referer":"https://example"}}}}]}""",
        )
    }

    private fun fetcher(log: MutableList<String> = ArrayList()): LiveTvFetcher {
        val map = responses()
        return { url, _ ->
            log += url
            map[url] ?: error("HTTP 404 for $url")
        }
    }

    @Test
    fun loadsLineupScheduleAndChipsFromStaticAddon() = runTest {
        val requested = ArrayList<String>()
        val guide = LiveTvGuideLoader.load(manifestUrl, now, forceRefresh = false, fetch = fetcher(requested))

        assertEquals("Stremio TV", guide.addonName)
        assertEquals(listOf("stremiotv_cnn", "stremiotv_espn"), guide.lineupIds)
        assertEquals(listOf("News Central", "Situation Room", "Overnight"), guide.programs("stremiotv_cnn").map { it.title })
        assertEquals("SportsCenter", guide.programAt("stremiotv_espn", now)?.title)
        assertEquals(listOf("favorites", "sports", "local-test"), guide.groups.map { it.id })
        assertEquals(listOf("stremiotv_test"), guide.channelsFor("local-test").map { it.id })
        assertEquals(0, guide.channel("stremiotv_test")?.number)
        // Native EPG addons never need the per-channel meta fallback.
        assertTrue(requested.none { "/meta/" in it })
    }

    @Test
    fun streamsKeepAddonOrderAndDropUnplayableLinks() = runTest {
        val fetch = fetcher()
        val guide = LiveTvGuideLoader.load(manifestUrl, now, forceRefresh = false, fetch = fetch)
        val streams = LiveTvGuideLoader.loadStreams(manifestUrl, guide, guide.channel("stremiotv_cnn")!!, fetch)
        assertEquals(listOf("Primary • 720p", "Backup 1 • 1080p"), streams.map { it.name })
        assertEquals(mapOf("Referer" to "https://example"), streams[1].behaviorHints.proxyHeaders?.request)
    }

    @Test
    fun fallsBackToMetaEndpointWithoutDateExtra() = runTest {
        val plainManifest = """{"id":"x","name":"Plain","resources":["catalog","meta","stream"],
            "catalogs":[{"type":"tv","id":"all","name":"All"}]}"""
        val map = mapOf(
            manifestUrl to plainManifest,
            "$base/catalog/tv/all.json" to """{"metas":[{"id":"c1","type":"tv","name":"One"}]}""",
            "$base/meta/tv/c1.json" to """{"meta":${meta("c1", "One", "2026-09-29T12:00:00Z" to "Show")}}""",
        )
        val guide = LiveTvGuideLoader.load(manifestUrl, now, false) { url, _ -> map[url] ?: error("404 $url") }
        assertEquals("Show", guide.programAt("c1", now)?.title)
    }

    @Test
    fun failsClearlyWhenManifestIsUnreachable() = runTest {
        assertFailsWith<IllegalStateException> {
            LiveTvGuideLoader.load(manifestUrl, now, false) { url, _ -> error("HTTP 404 for $url") }
        }
    }

    @Test
    fun normalizesManifestLinks() {
        assertEquals(manifestUrl, normalizeManifestUrl("stremio://example.github.io/Stremio-TV/manifest.json"))
        assertEquals(manifestUrl, normalizeManifestUrl("example.github.io/Stremio-TV/"))
        assertEquals("$manifestUrl?token=1", normalizeManifestUrl("$base?token=1"))
        assertEquals("http://192.168.1.20:7000/manifest.json", normalizeManifestUrl("http://192.168.1.20:7000"))
    }
}
