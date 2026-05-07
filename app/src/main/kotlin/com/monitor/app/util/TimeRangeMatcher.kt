package com.monitor.app.util

import java.time.LocalTime

object TimeRangeMatcher {

    data class TimeRange(val start: String, val end: String) {
        val startMinute: Int = start.toMinuteOfDay()
        val endMinute: Int = end.toMinuteOfDay()
    }

    fun isInRange(range: TimeRange, now: LocalTime = LocalTime.now()): Boolean {
        val nowMinute = now.hour * 60 + now.minute
        return if (range.startMinute <= range.endMinute) {
            nowMinute in range.startMinute..range.endMinute
        } else {
            nowMinute >= range.startMinute || nowMinute <= range.endMinute
        }
    }

    private fun String.toMinuteOfDay(): Int {
        val parts = split(":")
        require(parts.size == 2 && parts[0].length == 2 && parts[1].length == 2) {
            "Invalid time format: '$this'. Expected HH:mm."
        }
        val hour = parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid hour in '$this'")
        val minute = parts[1].toIntOrNull() ?: throw IllegalArgumentException("Invalid minute in '$this'")
        require(hour in 0..23) { "Hour out of range in '$this'" }
        require(minute in 0..59) { "Minute out of range in '$this'" }
        return hour * 60 + minute
    }
}
