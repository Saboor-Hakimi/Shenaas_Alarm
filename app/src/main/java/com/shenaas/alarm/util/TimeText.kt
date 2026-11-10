package com.shenaas.alarm.util

import android.content.Context
import android.text.format.DateFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil

object TimeText {
    fun is24h(context: Context): Boolean = DateFormat.is24HourFormat(context)

    /** "6:30 AM" or "06:30" depending on the system setting. */
    fun clock(context: Context, hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(formatter(context))

    fun clock(context: Context, epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalTime().format(formatter(context))

    /** Hours and minutes only, without the AM/PM marker, for big displays. */
    fun bigClock(context: Context, hour: Int, minute: Int): String =
        LocalTime.of(hour, minute).format(DateTimeFormatter.ofPattern(if (is24h(context)) "HH:mm" else "h:mm", Locale.getDefault()))

    fun amPm(context: Context, hour: Int): String? =
        if (is24h(context)) null else LocalTime.of(hour, 0).format(DateTimeFormatter.ofPattern("a", Locale.getDefault()))

    /** "7 h 12 min", "3 d 4 h", "12 min" or "under a minute". */
    fun countdown(millis: Long): String {
        val totalMinutes = ceil(millis / 60_000.0).toLong().coerceAtLeast(0)
        val days = totalMinutes / (24 * 60)
        val hours = totalMinutes % (24 * 60) / 60
        val minutes = totalMinutes % 60
        return when {
            millis < 60_000 -> "under a minute"
            days > 0 -> if (hours > 0) "$days d $hours h" else "$days d"
            hours > 0 -> if (minutes > 0) "$hours h $minutes min" else "$hours h"
            else -> "$minutes min"
        }
    }

    /** "4m 12s" / "38s" for challenge and wake-up durations. */
    fun duration(millis: Long): String {
        val seconds = (millis / 1000).coerceAtLeast(0)
        val m = seconds / 60
        val s = seconds % 60
        return if (m > 0) "${m}m ${s}s" else "${s}s"
    }

    private fun formatter(context: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24h(context)) "HH:mm" else "h:mm a", Locale.getDefault())
}
