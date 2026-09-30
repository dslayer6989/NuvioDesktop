package com.nuvio.app.features.livetv

/** Pure time helpers for the guide. All values are epoch milliseconds in UTC. */
internal object LiveTvTime {
    const val MINUTE_MS: Long = 60_000L
    const val HALF_HOUR_MS: Long = 30 * MINUTE_MS
    const val HOUR_MS: Long = 60 * MINUTE_MS
    const val DAY_MS: Long = 24 * HOUR_MS

    fun floorToHalfHour(epochMs: Long): Long = epochMs.floorDiv(HALF_HOUR_MS) * HALF_HOUR_MS

    /** Half-hour slot starts covering [startMs, endMs). */
    fun halfHourSlots(startMs: Long, endMs: Long): List<Long> {
        val slots = ArrayList<Long>()
        var cursor = floorToHalfHour(startMs)
        while (cursor < endMs) {
            slots += cursor
            cursor += HALF_HOUR_MS
        }
        return slots
    }

    /** "YYYY-MM-DD" for the UTC day containing [epochMs]. */
    fun utcDate(epochMs: Long): String {
        val days = epochMs.floorDiv(DAY_MS)
        val (year, month, day) = civilFromDays(days)
        return buildString {
            append(year.toString().padStart(4, '0'))
            append('-')
            append(month.toString().padStart(2, '0'))
            append('-')
            append(day.toString().padStart(2, '0'))
        }
    }

    /** Every UTC date touched by [startMs, endMs]. */
    fun utcDatesBetween(startMs: Long, endMs: Long): List<String> {
        if (endMs < startMs) return emptyList()
        val first = startMs.floorDiv(DAY_MS)
        val last = endMs.floorDiv(DAY_MS)
        return (first..last).map { utcDate(it * DAY_MS) }
    }

    private val isoPattern = Regex(
        """^(\d{4})-(\d{2})-(\d{2})(?:[Tt ](\d{2}):(\d{2})(?::(\d{2})(?:[.,](\d{1,9}))?)?)?\s*(Z|z|[+-]\d{2}(?::?\d{2})?)?$""",
    )

    private val xmltvPattern = Regex("""^(\d{4})(\d{2})(\d{2})(\d{2})(\d{2})(\d{2})?\s*([+-]\d{4})?$""")

    /** Parses ISO-8601 instants ("2026-09-29T20:00:00Z", offsets, fractions) and XMLTV stamps. */
    fun parseInstant(value: String?): Long? {
        val text = value?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        isoPattern.matchEntire(text)?.let { match ->
            val g = match.groupValues
            val year = g[1].toInt()
            val month = g[2].toInt()
            val day = g[3].toInt()
            val hour = g[4].toIntOrNull() ?: 0
            val minute = g[5].toIntOrNull() ?: 0
            val second = g[6].toIntOrNull() ?: 0
            val fraction = g[7].takeIf { it.isNotEmpty() }?.padEnd(3, '0')?.take(3)?.toInt() ?: 0
            val offsetMs = parseOffset(g[8]) ?: return null
            return toEpochMs(year, month, day, hour, minute, second, fraction, offsetMs)
        }
        xmltvPattern.matchEntire(text)?.let { match ->
            val g = match.groupValues
            val offsetMs = parseOffset(g[7]) ?: return null
            return toEpochMs(
                year = g[1].toInt(),
                month = g[2].toInt(),
                day = g[3].toInt(),
                hour = g[4].toInt(),
                minute = g[5].toInt(),
                second = g[6].toIntOrNull() ?: 0,
                millis = 0,
                offsetMs = offsetMs,
            )
        }
        return null
    }

    /** "60 min", "1h 30m", "1:30", "PT1H30M" or a bare number of minutes. */
    fun parseRuntimeMinutes(value: String?): Int? {
        val text = value?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return null
        text.toIntOrNull()?.let { return it.takeIf { minutes -> minutes > 0 } }
        Regex("""^(\d+):(\d{2})$""").matchEntire(text)?.let { match ->
            return (match.groupValues[1].toInt() * 60 + match.groupValues[2].toInt()).takeIf { it > 0 }
        }
        val iso = Regex("""^pt(?:(\d+)h)?(?:(\d+)m)?(?:\d+s)?$""").matchEntire(text)
        if (iso != null) {
            val total = (iso.groupValues[1].toIntOrNull() ?: 0) * 60 + (iso.groupValues[2].toIntOrNull() ?: 0)
            return total.takeIf { it > 0 }
        }
        val hours = Regex("""(\d+)\s*(?:h|hr|hrs|hour|hours)\b""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val minutes = Regex("""(\d+)\s*(?:m|min|mins|minute|minutes)\b""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
        val total = hours * 60 + minutes
        return total.takeIf { it > 0 }
    }

    private fun parseOffset(raw: String): Long? {
        if (raw.isEmpty() || raw.equals("z", ignoreCase = true)) return 0L
        val sign = if (raw.startsWith('-')) -1 else 1
        val digits = raw.drop(1).replace(":", "")
        val hours = digits.take(2).toIntOrNull() ?: return null
        val minutes = digits.drop(2).takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (hours > 18 || minutes > 59) return null
        return sign * (hours * HOUR_MS + minutes * MINUTE_MS)
    }

    private fun toEpochMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int,
        offsetMs: Long,
    ): Long? {
        if (month !in 1..12 || day !in 1..31 || hour > 23 || minute > 59 || second > 60) return null
        val days = daysFromCivil(year, month, day)
        return days * DAY_MS + hour * HOUR_MS + minute * MINUTE_MS + second * 1_000L + millis - offsetMs
    }

    // Howard Hinnant's civil calendar algorithms (proleptic Gregorian).
    private fun daysFromCivil(year: Int, month: Int, day: Int): Long {
        val y = (if (month <= 2) year - 1 else year).toLong()
        val era = (if (y >= 0) y else y - 399) / 400
        val yoe = y - era * 400
        val mp = (month + 9) % 12
        val doy = (153 * mp + 2) / 5 + day - 1
        val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
        return era * 146097 + doe - 719468
    }

    private fun civilFromDays(daysSinceEpoch: Long): Triple<Int, Int, Int> {
        val z = daysSinceEpoch + 719468
        val era = (if (z >= 0) z else z - 146096) / 146097
        val doe = z - era * 146097
        val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
        val y = yoe + era * 400
        val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
        val mp = (5 * doy + 2) / 153
        val d = (doy - (153 * mp + 2) / 5 + 1).toInt()
        val m = (if (mp < 10) mp + 3 else mp - 9).toInt()
        return Triple((if (m <= 2) y + 1 else y).toInt(), m, d)
    }
}
