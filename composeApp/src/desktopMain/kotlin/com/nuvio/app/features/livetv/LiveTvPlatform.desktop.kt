package com.nuvio.app.features.livetv

import com.nuvio.app.core.storage.DesktopStorage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal actual object LiveTvStorage {
    private val store = DesktopStorage.store("nuvio_live_tv")

    actual fun loadString(key: String): String? = store.getString(key)

    actual fun saveString(key: String, value: String?) {
        if (value == null) store.remove(key) else store.putString(key, value)
    }
}

internal actual object LiveTvClock {
    private val timeFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withZone(ZoneId.systemDefault())

    private val dayFormatter: DateTimeFormatter
        get() = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()).withZone(ZoneId.systemDefault())

    actual fun nowEpochMs(): Long = System.currentTimeMillis()

    actual fun formatTimeOfDay(epochMs: Long): String = timeFormatter.format(Instant.ofEpochMilli(epochMs))

    actual fun formatDay(epochMs: Long): String = dayFormatter.format(Instant.ofEpochMilli(epochMs))

    actual fun localDayStart(epochMs: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
