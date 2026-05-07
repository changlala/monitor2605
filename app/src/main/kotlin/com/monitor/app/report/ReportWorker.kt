package com.monitor.app.report

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.monitor.app.config.ConfigManager
import com.monitor.app.db.LocationRecordDao
import com.monitor.app.db.ReportLogDao
import com.monitor.app.db.entities.ReportLog
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.util.DeviceId
import com.monitor.app.util.TimeRangeMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@HiltWorker
class ReportWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val configManager: ConfigManager,
    private val locationRecordDao: LocationRecordDao,
    private val reportLogDao: ReportLogDao,
    private val feishuClient: FeishuClient,
    private val diagnosticLogger: DiagnosticLogger
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val config = configManager.getConfigBlocking()
        val deviceId = DeviceId.get(context)

        // Check if this time window has an active report interval
        val now = LocalTime.now()
        val activeInterval = config.report.intervals.find { interval ->
            val range = TimeRangeMatcher.TimeRange(interval.start, interval.end)
            TimeRangeMatcher.isInRange(range, now)
        }
        if (activeInterval == null) return Result.success()

        val batchSize = config.report.batch_size
        var retryCount = 0

        while (true) {
            val lastReportedId = reportLogDao.getLastReportedRecordId()
            val batch = locationRecordDao.getUnreported(lastReportedId, batchSize)
            if (batch.isEmpty()) break

            val payload = ReportPayload.build(batch, deviceId)
            val result = feishuClient.send(config.network.base_url, payload)

            val log = ReportLog(
                fromRecordId = batch.first().id,
                toRecordId = batch.last().id,
                recordCount = batch.size,
                reportedAt = System.currentTimeMillis(),
                responseCode = result.responseCode,
                success = result.success
            )
            reportLogDao.insert(log)
            diagnosticLogger.log(
                if (result.success) "report_success" else "report_fail",
                """{"count":${batch.size},"code":${result.responseCode}}"""
            )

            if (result.success) {
                retryCount = 0
                // Continue to next batch
            } else {
                val isClientError = result.responseCode in 400..499
                if (isClientError) {
                    diagnosticLogger.log("report_abandon",
                        """{"count":${batch.size},"code":${result.responseCode},"reason":"4xx_client_error"}""")
                    retryCount = 0
                    // Continue to next batch
                } else {
                    retryCount++
                    if (retryCount >= config.report.retry_max) {
                        diagnosticLogger.log("report_abandon", """{"count":${batch.size}}""")
                        retryCount = 0
                        // Continue to next batch
                    } else {
                        val backoffSeconds = config.report.retry_backoff_base_seconds *
                                Math.pow(2.0, retryCount - 1.0).toInt()
                        delay(backoffSeconds * 1000L)
                        // Retry same batch
                    }
                }
            }
        }

        return Result.success()
    }

    companion object {
        fun schedule(context: Context, intervalSeconds: Int) {
            val work = PeriodicWorkRequestBuilder<ReportWorker>(
                intervalSeconds.toLong(), TimeUnit.SECONDS
            ).setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "report",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork("report")
            }
        }
    }
}
