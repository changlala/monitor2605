package com.monitor.app.config

data class AppConfig(
    val version: Int = 1,
    val update_interval_minutes: Int = 60,
    val config_sources: List<ConfigSource> = listOf(),
    val config_fetch_timeout_seconds: Int = 10,
    val config_fetch_strategy: String = "sequential",
    val location_strategy: LocationStrategy = LocationStrategy(),
    val degradation: List<DegradationLevel> = listOf(
        DegradationLevel(battery_pct_above = 50, mode = "normal"),
        DegradationLevel(battery_pct_above = 20, interval_multiplier = 2, mode = "low_power", max_accuracy_seconds = 600),
        DegradationLevel(battery_pct_above = 0, interval_multiplier = 5, mode = "critical", force_workmanager = true)
    ),
    val report: ReportConfig = ReportConfig(),
    val network: NetworkConfig = NetworkConfig(),
    val keep_alive: KeepAliveConfig = KeepAliveConfig()
)

data class ConfigSource(
    val url: String,
    val priority: Int
)

data class LocationStrategy(
    val peak_hours: PeriodConfig = PeriodConfig(start = "07:00", end = "20:00", interval_seconds = 300, priority = "HIGH_ACCURACY"),
    val off_peak_hours: PeriodConfig = PeriodConfig(start = "20:00", end = "07:00", interval_seconds = 1800, priority = "BALANCED_POWER_ACCURACY")
)

data class PeriodConfig(
    val start: String,
    val end: String,
    val interval_seconds: Int,
    val priority: String
)

data class DegradationLevel(
    val battery_pct_above: Int,
    val interval_multiplier: Int = 1,
    val mode: String = "normal",
    val max_accuracy_seconds: Int? = null,
    val force_workmanager: Boolean = false
)

data class ReportConfig(
    val batch_size: Int = 100,
    val intervals: List<ReportInterval> = listOf(
        ReportInterval(start = "08:00", end = "22:00", interval_seconds = 600),
        ReportInterval(start = "22:00", end = "08:00", interval_seconds = 3600)
    ),
    val wifi_only: Boolean = false,
    val retry_max: Int = 5,
    val retry_backoff_base_seconds: Int = 30
)

data class ReportInterval(
    val start: String,
    val end: String,
    val interval_seconds: Int
)

data class NetworkConfig(
    val base_url: String = "",
    val timeout_seconds: Int = 30
)

data class KeepAliveConfig(
    val foreground_service_notification: NotificationConfig = NotificationConfig(),
    val restart_on_kill: RestartOnKillConfig = RestartOnKillConfig(),
    val watchdog: WatchdogConfig = WatchdogConfig(),
    val device_specific: DeviceSpecificConfig = DeviceSpecificConfig()
)

data class NotificationConfig(
    val title: String = "系统服务",
    val text: String = "设备服务运行中",
    val on_click: String = "none"
)

data class RestartOnKillConfig(
    val enabled: Boolean = true,
    val max_restarts_per_hour: Int = 3,
    val restart_delay_seconds: Int = 30,
    val alarm_wakeup_enabled: Boolean = true
)

data class WatchdogConfig(
    val enabled: Boolean = true,
    val check_interval_seconds: Int = 300
)

data class DeviceSpecificConfig(
    val xiaomi_autostart_guide: Boolean = true,
    val huawei_protected_app_guide: Boolean = true,
    val oppo_background_guide: Boolean = true
)
