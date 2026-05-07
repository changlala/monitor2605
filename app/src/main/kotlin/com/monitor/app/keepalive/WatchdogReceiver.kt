package com.monitor.app.keepalive

import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.location.LocationService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WatchdogReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        diagnosticLogger.log("alarm_wakeup", """{"action":"check"}""")

        // Attempt 1: startForegroundService
        try {
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startForegroundService(serviceIntent)
            diagnosticLogger.log("alarm_wakeup", """{"action":"restart","method":"startForegroundService"}""")
        } catch (e: ForegroundServiceStartNotAllowedException) {
            diagnosticLogger.log("watchdog_restart_blocked",
                """{"method":"startForegroundService","reason":"ForegroundServiceStartNotAllowed"}""")
            // Attempt 2: schedule immediate restart via WorkManager
            tryRestartViaWorkManager(context)
        } catch (e: Exception) {
            diagnosticLogger.exception("watchdog_restart_failed", e)
            // Attempt 2: schedule immediate restart via WorkManager
            tryRestartViaWorkManager(context)
        }
    }

    private fun tryRestartViaWorkManager(context: Context) {
        try {
            val work = OneTimeWorkRequestBuilder<RestartWorker>()
                .setInitialDelay(1, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(work)
            diagnosticLogger.log("watchdog_restart_fallback",
                """{"method":"WorkManager","reason":"foreground_service_blocked"}""")
        } catch (e: Exception) {
            diagnosticLogger.exception("watchdog_workmanager_fallback_failed", e)
        }
    }
}
