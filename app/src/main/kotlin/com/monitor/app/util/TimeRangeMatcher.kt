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
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
