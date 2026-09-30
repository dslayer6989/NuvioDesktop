package com.nuvio.app.features.livetv

import com.nuvio.app.features.streams.StreamItem
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** A request to start watching a channel, from any screen (guide, On Now, Home row). */
data class LiveTvPlayRequest(
    val channelId: String,
    /** The chip the user was browsing, so channel up/down stays inside it. */
    val groupId: String? = null,
)

/**
 * Screens ask for playback here; the app shell (which owns navigation and the player)
 * listens and launches. This keeps Home and Live TV free of navigation plumbing.
 */
object LiveTvLauncher {
    private val _requests = MutableSharedFlow<LiveTvPlayRequest>(extraBufferCapacity = 8)
    val requests: SharedFlow<LiveTvPlayRequest> = _requests.asSharedFlow()

    private val _tuningChannelId = MutableStateFlow<String?>(null)

    /** The channel whose streams are being resolved right now, for "Tuning…" indicators. */
    val tuningChannelId: StateFlow<String?> = _tuningChannelId.asStateFlow()

    fun play(channelId: String, groupId: String? = null) {
        _requests.tryEmit(LiveTvPlayRequest(channelId = channelId, groupId = groupId))
    }

    internal fun setTuning(channelId: String?) {
        _tuningChannelId.value = channelId
    }
}

/** What the player needs to tune a channel and fail over between its sources. */
data class LiveTvTuneResult(
    val channel: LiveTvChannel,
    val program: LiveTvProgram?,
    val streams: List<StreamItem>,
    val groupId: String?,
)

sealed interface LiveTvTuneOutcome {
    data class Ready(val result: LiveTvTuneResult) : LiveTvTuneOutcome
    data class Failed(val message: String) : LiveTvTuneOutcome
}

internal object LiveTvTuner {
    /** Resolves a channel's streams (Primary first). Never throws. */
    suspend fun tune(request: LiveTvPlayRequest): LiveTvTuneOutcome {
        LiveTvRepository.ensureLoaded()
        val state = LiveTvRepository.uiState.value
        val guide = state.guide ?: return LiveTvTuneOutcome.Failed("Live TV is still loading. Try again in a moment.")
        val channel = guide.channel(request.channelId)
            ?: return LiveTvTuneOutcome.Failed("That channel is no longer in the lineup.")
        val streams = try {
            LiveTvRepository.loadStreams(channel)
        } catch (error: Throwable) {
            if (error is kotlinx.coroutines.CancellationException) throw error
            return LiveTvTuneOutcome.Failed("Couldn't reach ${channel.name}: ${error.message ?: "network error"}")
        }
        if (streams.isEmpty()) {
            return LiveTvTuneOutcome.Failed("${channel.name} has no working streams right now.")
        }
        return LiveTvTuneOutcome.Ready(
            LiveTvTuneResult(
                channel = channel,
                program = guide.programAt(channel.id, LiveTvClock.nowEpochMs()),
                streams = streams,
                groupId = request.groupId,
            ),
        )
    }
}

/** State of the channel currently playing in the player, shared with the in-player overlay. */
data class LiveTvSessionState(
    val channel: LiveTvChannel,
    val groupId: String?,
    val streams: List<StreamItem>,
    val streamIndex: Int = 0,
    /** Bumped on every channel or source change so the startup watchdog restarts. */
    val generation: Long = 0L,
) {
    val currentStream: StreamItem?
        get() = streams.getOrNull(streamIndex)

    val hasBackup: Boolean
        get() = streamIndex + 1 < streams.size
}

object LiveTvPlayerSession {
    private val _state = MutableStateFlow<LiveTvSessionState?>(null)
    val state: StateFlow<LiveTvSessionState?> = _state.asStateFlow()
    private var generation = 0L

    fun start(result: LiveTvTuneResult) {
        generation += 1
        _state.value = LiveTvSessionState(
            channel = result.channel,
            groupId = result.groupId,
            streams = result.streams,
            streamIndex = 0,
            generation = generation,
        )
        LiveTvRepository.recordWatched(result.channel.id)
    }

    /** Moves to [index] within the current channel's sources. */
    fun selectStream(index: Int): StreamItem? {
        val current = _state.value ?: return null
        val stream = current.streams.getOrNull(index) ?: return null
        generation += 1
        _state.value = current.copy(streamIndex = index, generation = generation)
        return stream
    }

    /** Next backup after the current source, or null when every source has been tried. */
    fun advanceToBackup(): StreamItem? {
        val current = _state.value ?: return null
        if (!current.hasBackup) return null
        return selectStream(current.streamIndex + 1)
    }

    fun clear() {
        _state.value = null
    }
}

/** Short source label for notifications, e.g. "Backup 1 • 1080p". */
internal fun StreamItem.liveSourceLabel(index: Int): String =
    name?.takeIf { it.isNotBlank() } ?: if (index == 0) "Primary" else "Backup $index"

/** One-shot UI events for the Live TV tab (re-selecting the tab jumps the guide back to "now"). */
object LiveTvScreenEvents {
    private val _jumpToNow = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val jumpToNow: SharedFlow<Unit> = _jumpToNow.asSharedFlow()

    fun requestJumpToNow() {
        _jumpToNow.tryEmit(Unit)
    }

    private val _openTab = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Other screens (the Home "Live Now" row) ask the shell to switch to the Live TV tab. */
    val openTab: SharedFlow<Unit> = _openTab.asSharedFlow()

    fun requestOpenTab() {
        _openTab.tryEmit(Unit)
    }
}
