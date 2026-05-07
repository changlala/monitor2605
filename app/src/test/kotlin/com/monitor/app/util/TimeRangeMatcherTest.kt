package com.monitor.app.util

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalTime

class TimeRangeMatcherTest {

    @Test
    fun `normal range 8-22 matches inside`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(12, 0)))
    }

    @Test
    fun `normal range 8-22 rejects before`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(6, 0)))
    }

    @Test
    fun `normal range 8-22 rejects after`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(23, 0)))
    }

    @Test
    fun `cross-midnight range 20-07 matches before midnight`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(22, 30)))
    }

    @Test
    fun `cross-midnight range 20-07 matches after midnight`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(3, 15)))
    }

    @Test
    fun `cross-midnight range 20-07 rejects midday`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(13, 0)))
    }

    @Test
    fun `range boundaries inclusive`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(8, 0)))
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(22, 0)))
    }
}
