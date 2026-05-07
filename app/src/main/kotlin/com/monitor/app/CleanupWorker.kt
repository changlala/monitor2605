package com.monitor.app

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.monitor.app.db.LocationRecordDao
import com.monitor.app.db.ReportLogDao
import com.monitor.app.diag.DiagnosticLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRecordDao: LocationRecordDao,
    private val reportLogDao: ReportLogDao,
    private val diagnosticLogger: DiagnosticLogger
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 24 * 3600_000L
        val thirtyDaysAgo = now - 30 * 24 * 3600_000L

        val lastReportedId = reportLogDao.getLastReportedRecordId()
        val deletedLocations = locationRecordDao.deleteReportedOlderThan(lastReportedId, sevenDaysAgo)
        val deletedLogs = reportLogDao.deleteFailedOlderThan(thirtyDaysAgo)

        // Absolute fallback: delete any records older than 30 days regardless of report status
        val absoluteCutoff = now - 30 * 24 * 3600_000L
        val absoluteDeleted = locationRecordDao.deleteOlderThan(absoluteCutoff)

        diagnosticLogger.log("database_cleanup",
            """{"deleted_locations":$deletedLocations,"deleted_logs":$deletedLogs,"absolute_deleted":$absoluteDeleted}""")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleanup", ExistingPeriodicWorkPolicy.KEEP, work
            )
        }
    }
}
