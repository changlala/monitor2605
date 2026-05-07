package com.monitor.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.*
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.keepalive.KeepAliveManager
import com.monitor.app.location.LocationService
import com.monitor.app.report.ReportWorker
import com.monitor.app.ui.GuideActivity
import com.monitor.app.util.TimeRangeMatcher
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalTime
import javax.inject.Inject

@HiltAndroidApp
class MonitorApplication : Application(), Configuration.Provider {

    @Inject lateinit var configManager: ConfigManager
    @Inject lateinit var keepAliveManager: KeepAliveManager
    @Inject lateinit var diagnosticLogger: DiagnosticLogger
    @Inject lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Detect if previously killed
        val wasKilled = keepAliveManager.checkWasKilled()
        if (wasKilled) {
            diagnosticLogger.log("service_killed")
            keepAliveManager.restartIfKilled()
        }

        // Initialize config (loads cached, then async fetches remote)
        configManager.init()

        // Check permissions on Android 10+
        var missingPermissions = emptyList<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val missing = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missing.add(Manifest.permission.POST_NOTIFICATIONS)
            missingPermissions = missing

            if (missing.isNotEmpty()) {
                val guideIntent = Intent(this, GuideActivity::class.java)
                guideIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(guideIntent)
                return  // Don't start service until permissions granted
            }
        }

        startAllServices()
    }

    private fun startAllServices() {
        // Start foreground location service
        val intent = Intent(this, LocationService::class.java)
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
}
