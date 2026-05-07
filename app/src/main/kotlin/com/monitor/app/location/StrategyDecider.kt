package com.monitor.app.location

import com.monitor.app.config.AppConfig
import com.monitor.app.util.TimeRangeMatcher
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrategyDecider @Inject constructor() {

    enum class Mode { PEAK, OFF_PEAK }

    data class Decision(
        val mode: Mode,
        val intervalSeconds: Int,
        val priority: String,
        val effectiveConfig: String,
        val forceWorkManager: Boolean = false
    )

    fun decide(config: AppConfig, batteryPct: Int, now: LocalTime = LocalTime.now()): Decision {
        // Step 1: pick base period config
        val peakRange = TimeRangeMatcher.TimeRange(
            config.location_strategy.peak_hours.start,
            config.location_strategy.peak_hours.end
        )
        val isPeak = TimeRangeMatcher.isInRange(peakRange, now)
        val periodConfig = if (isPeak) config.location_strategy.peak_hours
                           else config.location_strategy.off_peak_hours

        // Step 2: apply degradation (sorted descending by battery threshold, first match wins)
        val deg = config.degradation
            .sortedByDescending { it.battery_pct_above }
            .find { batteryPct >= it.battery_pct_above }

        val multiplier = deg?.interval_multiplier ?: 1
        val forceWm = deg?.force_workmanager ?: false
        val interval = (periodConfig.interval_seconds * multiplier)
            .coerceAtLeast(60)

        val effectiveConfig = buildString {
            append(if (isPeak) "peak" else "off_peak")
            if (deg != null && deg.mode != "normal") append("_${deg.mode}")
        }

        return Decision(
            mode = if (isPeak) Mode.PEAK else Mode.OFF_PEAK,
            intervalSeconds = interval,
            priority = periodConfig.priority,
            effectiveConfig = effectiveConfig,
            forceWorkManager = forceWm
        )
    }
}
