package com.monitor.app.keepalive

import android.content.Context
import android.content.Intent
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.location.LocationService
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class RestartWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val diagnosticLogger: DiagnosticLogger
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        diagnosticLogger.log("watchdog_restart_work", """{"action":"attempt"}""")
        try {
            val intent = Intent(context, LocationService::class.java)
            context.startForegroundService(intent)
            diagnosticLogger.log("watchdog_restart_work", """{"action":"success"}""")
        } catch (e: Exception) {
            diagnosticLogger.exception("watchdog_restart_work_failed", e)
        }
        return Result.success()
    }
}
