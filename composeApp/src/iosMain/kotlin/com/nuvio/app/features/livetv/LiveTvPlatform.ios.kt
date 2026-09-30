package com.nuvio.app.features.livetv

import platform.Foundation.NSCalendar
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.NSUserDefaults
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

internal actual object LiveTvStorage {
    private const val prefix = "nuvio_live_tv_"

    actual fun loadString(key: String): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(prefix + key)

    actual fun saveString(key: String, value: String?) {
        val defaults = NSUserDefaults.standardUserDefaults
        if (value == null) defaults.removeObjectForKey(prefix + key) else defaults.setObject(value, forKey = prefix + key)
    }
}

internal actual object LiveTvClock {
    actual fun nowEpochMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    actual fun formatTimeOfDay(epochMs: Long): String = NSDateFormatter().apply {
        dateStyle = NSDateFormatterNoStyle
        timeStyle = NSDateFormatterShortStyle
    }.stringFromDate(epochMs.toNSDate())

    actual fun formatDay(epochMs: Long): String = NSDateFormatter().apply {
        setLocalizedDateFormatFromTemplate("EEEMMMd")
    }.stringFromDate(epochMs.toNSDate())

    actual fun localDayStart(epochMs: Long): Long {
        val start = NSCalendar.currentCalendar.startOfDayForDate(epochMs.toNSDate())
        return (start.timeIntervalSince1970 * 1000.0).toLong()
    }

    private fun Long.toNSDate(): NSDate = NSDate.dateWithTimeIntervalSince1970(this / 1000.0)
}
