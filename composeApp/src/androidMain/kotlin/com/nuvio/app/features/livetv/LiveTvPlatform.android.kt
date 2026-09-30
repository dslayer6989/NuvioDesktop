package com.nuvio.app.features.livetv

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

internal actual object LiveTvStorage {
    private const val preferencesName = "nuvio_live_tv"
    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadString(key: String): String? = preferences?.getString(key, null)

    actual fun saveString(key: String, value: String?) {
        val editor = preferences?.edit() ?: return
        if (value == null) editor.remove(key) else editor.putString(key, value)
        editor.apply()
    }
}

internal actual object LiveTvClock {
    actual fun nowEpochMs(): Long = System.currentTimeMillis()

    actual fun formatTimeOfDay(epochMs: Long): String =
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMs))

    actual fun formatDay(epochMs: Long): String =
        DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(epochMs))

    actual fun localDayStart(epochMs: Long): Long {
        val zone = ZoneId.systemDefault()
        return Instant.ofEpochMilli(epochMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    }
}
