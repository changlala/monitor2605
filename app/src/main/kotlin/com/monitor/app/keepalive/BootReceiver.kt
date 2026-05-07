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
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        diagnosticLogger.log("device_reboot")

        // Attempt 1: startForegroundService
        try {
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startForegroundService(serviceIntent)
            diagnosticLogger.log("device_reboot", """{"action":"service_started"}""")
            return
        } catch (e: ForegroundServiceStartNotAllowedException) {
            diagnosticLogger.log("device_reboot",
                """{"action":"blocked","reason":"ForegroundServiceStartNotAllowed"}""")
        } catch (e: Exception) {
            diagnosticLogger.exception("boot_restart_failed", e)
        }

        // Attempt 2: WorkManager fallback
        try {
            val work = OneTimeWorkRequestBuilder<RestartWorker>()
                .setInitialDelay(10, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(work)
            diagnosticLogger.log("device_reboot", """{"action":"workmanager_fallback"}""")
        } catch (e: Exception) {
            diagnosticLogger.exception("boot_workmanager_fallback_failed", e)
        }
    }
}
