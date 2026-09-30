package com.nuvio.app.features.livetv

import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

enum class LiveTvViewMode {
    Guide,
    OnNow,
}

data class LiveTvUiState(
    val manifestUrl: String = "",
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val guide: LiveTvGuide? = null,
    /** null = full lineup; otherwise a [LiveTvGroup.id] or one of the local chip ids. */
    val selectedGroupId: String? = null,
    val viewMode: LiveTvViewMode = LiveTvViewMode.Guide,
    val onNowSort: LiveTvOnNowSort = LiveTvOnNowSort.Channel,
    val favoriteIds: List<String> = emptyList(),
    val recentIds: List<String> = emptyList(),
) {
    val isConfigured: Boolean
        get() = manifestUrl.isNotBlank()

    /** Channels for the selected chip, resolving the local Favorites/Recent chips. */
    fun visibleChannels(): List<LiveTvChannel> {
        val guide = guide ?: return emptyList()
        return when (selectedGroupId) {
            LiveTvFavoritesGroupId -> favoriteIds.mapNotNull(guide::channel)
            LiveTvRecentGroupId -> recentIds.mapNotNull(guide::channel)
            else -> guide.channelsFor(selectedGroupId)
        }
    }

    /** Addon groups shown as chips (the addon's own favorites catalog is replaced by local Favorites). */
    fun addonChips(): List<LiveTvGroup> =
        guide?.groups.orEmpty().filterNot { it.isAddonFavoritesGroup() }
}

internal const val LiveTvFavoritesGroupId = "local:favorites"
internal const val LiveTvRecentGroupId = "local:recent"
internal const val LiveTvMaxRecents = 12
internal const val LiveTvStaleAfterMs = 30 * LiveTvTime.MINUTE_MS

private const val KeyManifestUrl = "manifest_url"
private const val KeyFavorites = "favorites"
private const val KeyRecents = "recents"
private const val KeySelectedGroup = "selected_group"
private const val KeyViewMode = "view_mode"
private const val KeyOnNowSort = "on_now_sort"

internal fun LiveTvGroup.isAddonFavoritesGroup(): Boolean =
    id.contains("favorite", ignoreCase = true) || name.contains("favorite", ignoreCase = true)

object LiveTvRepository {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val json = Json { ignoreUnknownKeys = true }
    private val idListSerializer = ListSerializer(String.serializer())

    private val _uiState = MutableStateFlow(LiveTvUiState())
    val uiState: StateFlow<LiveTvUiState> = _uiState.asStateFlow()

    private var initialized = false
    private var loadJob: Job? = null

    fun ensureLoaded() {
        if (!initialized) {
            initialized = true
            val storedUrl = LiveTvStorage.loadString(KeyManifestUrl)
            _uiState.value = LiveTvUiState(
                manifestUrl = storedUrl ?: LiveTvDefaultManifestUrl,
                selectedGroupId = LiveTvStorage.loadString(KeySelectedGroup),
                viewMode = LiveTvStorage.loadString(KeyViewMode)
                    ?.let { name -> LiveTvViewMode.entries.firstOrNull { it.name == name } }
                    ?: LiveTvViewMode.Guide,
                onNowSort = LiveTvStorage.loadString(KeyOnNowSort)
                    ?.let { name -> LiveTvOnNowSort.entries.firstOrNull { it.name == name } }
                    ?: LiveTvOnNowSort.Channel,
                favoriteIds = loadIds(KeyFavorites),
                recentIds = loadIds(KeyRecents),
            )
        }
        val state = _uiState.value
        if (state.guide == null && !state.isLoading && state.errorMessage == null && state.isConfigured) {
            refresh()
        }
    }

    fun refresh(forceRefresh: Boolean = false) {
        val manifestUrl = _uiState.value.manifestUrl.trim()
        if (manifestUrl.isBlank()) return
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                isLoading = it.guide == null,
                isRefreshing = it.guide != null,
                errorMessage = null,
            )
        }
        loadJob = scope.launch {
            try {
                val guide = LiveTvGuideLoader.load(
                    manifestUrl = normalizeManifestUrl(manifestUrl),
                    nowMs = LiveTvClock.nowEpochMs(),
                    forceRefresh = forceRefresh,
                )
                seedFavoritesIfNeeded(guide)
                _uiState.update { state ->
                    val selectionStillValid = state.selectedGroupId == null ||
                        state.selectedGroupId == LiveTvFavoritesGroupId ||
                        state.selectedGroupId == LiveTvRecentGroupId ||
                        guide.groups.any { it.id == state.selectedGroupId }
                    state.copy(
                        guide = guide,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = null,
                        selectedGroupId = if (selectionStillValid) state.selectedGroupId else null,
                    )
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        // Keep showing a previously loaded guide; only surface the error when there is nothing.
                        errorMessage = if (it.guide == null) error.message ?: "Couldn't load Live TV." else null,
                    )
                }
            }
        }
    }

    /** Reloads in the background once the guide is older than 30 minutes. */
    fun refreshIfStale(nowMs: Long = LiveTvClock.nowEpochMs()) {
        val state = _uiState.value
        val guide = state.guide ?: return
        if (state.isLoading || state.isRefreshing) return
        if (nowMs - guide.loadedAtMs >= LiveTvStaleAfterMs) refresh()
    }

    fun setManifestUrl(url: String) {
        val trimmed = url.trim()
        LiveTvStorage.saveString(KeyManifestUrl, trimmed)
        loadJob?.cancel()
        _uiState.update {
            it.copy(
                manifestUrl = trimmed,
                guide = null,
                errorMessage = null,
                isLoading = false,
                isRefreshing = false,
                selectedGroupId = null,
            )
        }
        LiveTvStorage.saveString(KeySelectedGroup, null)
        refresh(forceRefresh = true)
    }

    fun selectGroup(groupId: String?) {
        LiveTvStorage.saveString(KeySelectedGroup, groupId)
        _uiState.update { it.copy(selectedGroupId = groupId) }
    }

    fun setViewMode(mode: LiveTvViewMode) {
        LiveTvStorage.saveString(KeyViewMode, mode.name)
        _uiState.update { it.copy(viewMode = mode) }
    }

    fun setOnNowSort(sort: LiveTvOnNowSort) {
        LiveTvStorage.saveString(KeyOnNowSort, sort.name)
        _uiState.update { it.copy(onNowSort = sort) }
    }

    fun isFavorite(channelId: String): Boolean = channelId in _uiState.value.favoriteIds

    fun toggleFavorite(channelId: String) {
        val current = _uiState.value.favoriteIds
        val updated = if (channelId in current) current - channelId else current + channelId
        saveIds(KeyFavorites, updated)
        _uiState.update { it.copy(favoriteIds = updated) }
    }

    fun recordWatched(channelId: String) {
        val updated = (listOf(channelId) + _uiState.value.recentIds.filter { it != channelId }).take(LiveTvMaxRecents)
        saveIds(KeyRecents, updated)
        _uiState.update { it.copy(recentIds = updated) }
    }

    /** The channel watched before the current one, for the "last channel" button. */
    fun previousChannelId(currentId: String?): String? =
        _uiState.value.recentIds.firstOrNull { it != currentId }

    suspend fun loadStreams(channel: LiveTvChannel): List<StreamItem> {
        val state = _uiState.value
        val guide = state.guide ?: error("Live TV guide isn't loaded yet.")
        return LiveTvGuideLoader.loadStreams(normalizeManifestUrl(state.manifestUrl), guide, channel)
    }

    fun clearLocalState() {
        loadJob?.cancel()
        initialized = false
        _uiState.value = LiveTvUiState()
    }

    private fun seedFavoritesIfNeeded(guide: LiveTvGuide) {
        if (LiveTvStorage.loadString(KeyFavorites) != null) return
        val seed = guide.groups.firstOrNull { it.isAddonFavoritesGroup() }?.channelIds.orEmpty()
        saveIds(KeyFavorites, seed)
        _uiState.update { it.copy(favoriteIds = seed) }
    }

    private fun loadIds(key: String): List<String> =
        LiveTvStorage.loadString(key)
            ?.let { payload -> runCatching { json.decodeFromString(idListSerializer, payload) }.getOrNull() }
            .orEmpty()

    private fun saveIds(key: String, ids: List<String>) {
        LiveTvStorage.saveString(key, json.encodeToString(idListSerializer, ids))
    }
}

/** Accepts `stremio://` links and bare addon roots, like the addon manager does. */
internal fun normalizeManifestUrl(raw: String): String {
    var url = raw.trim()
    if (url.startsWith("stremio://", ignoreCase = true)) url = "https://" + url.substring("stremio://".length)
    if (!url.contains("://")) url = "https://$url"
    val query = url.substringAfter("?", "")
    val base = url.substringBefore("?").trimEnd('/')
    val withManifest = if (base.endsWith("/manifest.json", ignoreCase = true)) base else "$base/manifest.json"
    return if (query.isEmpty()) withManifest else "$withManifest?$query"
}
