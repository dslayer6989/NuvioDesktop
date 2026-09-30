package com.nuvio.app.features.livetv

/** Small key/value store for Live TV settings, favorites and recents. */
internal expect object LiveTvStorage {
    fun loadString(key: String): String?
    fun saveString(key: String, value: String?)
}

/** Wall clock and local-time formatting for the guide. */
internal expect object LiveTvClock {
    fun nowEpochMs(): Long

    /** Short local time of day, e.g. "8:30 PM" or "20:30" depending on the device locale. */
    fun formatTimeOfDay(epochMs: Long): String

    /** Short local weekday + date, e.g. "Tue, Sep 29". */
    fun formatDay(epochMs: Long): String

    /** Epoch ms of local midnight for the day containing [epochMs]. */
    fun localDayStart(epochMs: Long): Long
}
