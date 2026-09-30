package com.nuvio.app.features.livetv

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull

/** A channel as returned by one catalog/meta response, with any schedule it carried. */
internal data class ParsedLiveChannel(
    val channel: LiveTvChannel,
    val programs: List<ParsedLiveProgram>,
)

/** A schedule entry before end times are finalized (some addons omit them). */
internal data class ParsedLiveProgram(
    val id: String,
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    val description: String?,
    val thumbnail: String?,
    val genres: List<String>,
    val releaseInfo: String?,
)

/**
 * Parses Stremio addon responses for Live TV, including Stremio's native EPG protocol:
 * `behaviorHints.epgProvider` in the manifest, a `date` catalog extra, and `metasDetailed`
 * entries whose `videos` carry `startTime`/`endTime`.
 */
internal object LiveTvParser {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseManifest(payload: String): LiveTvAddonInfo {
        val root = json.parseToJsonElement(payload) as? JsonObject
            ?: error("Addon manifest is not a JSON object")
        val catalogs = (root["catalogs"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = obj.string("type") ?: return@mapNotNull null
            val id = obj.string("id") ?: return@mapNotNull null
            LiveTvCatalogInfo(
                type = type,
                id = id,
                name = obj.string("name") ?: id,
                extras = parseExtras(obj),
            )
        }
        val resources = (root["resources"] as? JsonArray).orEmpty().mapNotNull { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull
                is JsonObject -> element.string("name")
                else -> null
            }
        }
        val hints = root["behaviorHints"] as? JsonObject
        return LiveTvAddonInfo(
            id = root.string("id") ?: "",
            name = root.string("name") ?: "Live TV",
            logo = root.string("logo") ?: root.string("icon"),
            description = root.string("description"),
            epgProvider = hints?.boolean("epgProvider") == true,
            types = root.stringList("types"),
            resources = resources,
            catalogs = catalogs,
        )
    }

    /** Parses `catalog` responses (`metasDetailed` for EPG pages, `metas` otherwise). */
    fun parseCatalog(payload: String, fallbackType: String): List<ParsedLiveChannel> {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return emptyList()
        val metas = (root["metasDetailed"] as? JsonArray) ?: (root["metas"] as? JsonArray) ?: return emptyList()
        return metas.mapNotNull { element -> (element as? JsonObject)?.let { parseMeta(it, fallbackType) } }
    }

    /** Parses a `meta` response. */
    fun parseMetaResponse(payload: String, fallbackType: String): ParsedLiveChannel? {
        val root = json.parseToJsonElement(payload) as? JsonObject ?: return null
        val meta = root["meta"] as? JsonObject ?: return null
        return parseMeta(meta, fallbackType)
    }

    private fun parseMeta(meta: JsonObject, fallbackType: String): ParsedLiveChannel? {
        val id = meta.string("id") ?: return null
        val hints = meta["behaviorHints"] as? JsonObject
        val hasScheduledVideos = hints?.boolean("hasScheduledVideos") == true
        val channel = LiveTvChannel(
            id = id,
            type = meta.string("type") ?: fallbackType,
            name = meta.string("name")?.trim()?.takeIf { it.isNotEmpty() } ?: id,
            logo = meta.string("logo") ?: meta.string("poster") ?: meta.string("background"),
            genres = meta.stringList("genres").ifEmpty { meta.stringList("genre") },
            description = meta.string("description"),
        )
        val programs = (meta["videos"] as? JsonArray).orEmpty().mapNotNull { element ->
            (element as? JsonObject)?.let { parseProgram(it, id, hasScheduledVideos) }
        }
        return ParsedLiveChannel(channel = channel, programs = programs)
    }

    private fun parseProgram(
        video: JsonObject,
        channelId: String,
        hasScheduledVideos: Boolean,
    ): ParsedLiveProgram? {
        val explicitStart = LiveTvTime.parseInstant(video.string("startTime") ?: video.string("start"))
        // `released` alone only means "schedule" when the addon says so; otherwise it is an upload date.
        val start = explicitStart
            ?: if (hasScheduledVideos) LiveTvTime.parseInstant(video.string("released")) else null
        start ?: return null
        val explicitEnd = LiveTvTime.parseInstant(video.string("endTime") ?: video.string("end") ?: video.string("stop"))
        val runtimeEnd = LiveTvTime.parseRuntimeMinutes(video.string("runtime"))?.let { start + it * LiveTvTime.MINUTE_MS }
        val end = (explicitEnd ?: runtimeEnd)?.takeIf { it > start }
        val title = (video.string("title") ?: video.string("name"))?.trim()?.takeIf { it.isNotEmpty() } ?: "Untitled"
        return ParsedLiveProgram(
            id = video.string("id") ?: "$channelId:$start",
            title = title,
            startMs = start,
            endMs = end,
            description = (video.string("overview") ?: video.string("description"))?.trim()?.takeIf { it.isNotEmpty() },
            thumbnail = video.string("thumbnail") ?: video.string("poster"),
            genres = video.stringList("genres").ifEmpty { video.stringList("genre") },
            releaseInfo = video.string("releaseInfo"),
        )
    }

    private fun parseExtras(catalog: JsonObject): List<LiveTvCatalogExtra> {
        val declared = (catalog["extra"] as? JsonArray).orEmpty().mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val name = obj.string("name") ?: return@mapNotNull null
            LiveTvCatalogExtra(
                name = name,
                isRequired = obj.boolean("isRequired") == true,
                options = obj.stringList("options"),
            )
        }
        if (declared.isNotEmpty()) return declared
        // Legacy manifests: extraSupported / extraRequired string arrays.
        val required = catalog.stringList("extraRequired").toSet()
        return (catalog.stringList("extraSupported") + required).distinct().map { name ->
            LiveTvCatalogExtra(name = name, isRequired = name in required)
        }
    }

    private fun JsonObject.string(name: String): String? =
        (this[name] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    private fun JsonObject.boolean(name: String): Boolean? = (this[name] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.stringList(name: String): List<String> =
        when (val element: JsonElement? = this[name]) {
            is JsonArray -> element.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            is JsonPrimitive -> listOfNotNull(element.contentOrNull?.trim()?.takeIf(String::isNotEmpty))
            else -> emptyList()
        }
}
