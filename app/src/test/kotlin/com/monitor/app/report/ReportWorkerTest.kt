package com.monitor.app.report

import android.content.Context
import androidx.work.WorkerParameters
import com.monitor.app.config.AppConfig
import com.monitor.app.config.ConfigManager
import com.monitor.app.db.LocationRecordDao
import com.monitor.app.db.ReportLogDao
import com.monitor.app.db.entities.LocationRecord
import com.monitor.app.db.entities.ReportLog
import com.monitor.app.diag.DiagnosticLogger
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ReportWorkerTest {

    // In-memory fake DAOs so we can observe the actual query results
    private class FakeLocationRecordDao : LocationRecordDao {
        val records = mutableListOf<LocationRecord>()
        private var nextId = 1L

        override suspend fun insert(record: LocationRecord): Long {
            val id = nextId++
            records.add(record.copy(id = id))
            return id
        }

        override suspend fun getUnreported(lastReportedId: Long, limit: Int): List<LocationRecord> {
            return records
                .filter { it.id > lastReportedId }
                .sortedBy { it.recordedAt }
                .take(limit)
        }

        override suspend fun deleteReportedOlderThan(lastReportedId: Long, cutoff: Long): Int {
            val toDelete = records.filter { it.id <= lastReportedId && it.recordedAt < cutoff }
            records.removeAll(toDelete)
            return toDelete.size
        }

        override suspend fun deleteOlderThan(cutoff: Long): Int {
            val toDelete = records.filter { it.recordedAt < cutoff }
            records.removeAll(toDelete)
            return toDelete.size
        }

        override suspend fun count(): Int = records.size
    }

    private class FakeReportLogDao : ReportLogDao {
        val logs = mutableListOf<ReportLog>()
        private var nextId = 1L

        override suspend fun insert(log: ReportLog): Long {
            val id = nextId++
            logs.add(log.copy(id = id))
            return id
        }

        override suspend fun getLastReportedRecordId(): Long {
            return logs.filter { it.success }.maxOfOrNull { it.toRecordId } ?: 0L
        }

        override suspend fun deleteFailedOlderThan(cutoff: Long): Int {
            val toDelete = logs.filter { !it.success && it.reportedAt < cutoff }
            logs.removeAll(toDelete)
            return toDelete.size
        }
    }

    private fun makeRecord(id: Long, lat: Double = 39.9, lng: Double = 116.4): LocationRecord {
        return LocationRecord(
            id = id, latitude = lat, longitude = lng, altitude = null,
            accuracy = 5.0f, provider = "gps", recordedAt = System.currentTimeMillis(),
            batteryPct = 80
        )
    }

    private fun createWorker(
        locationDao: FakeLocationRecordDao,
        reportLogDao: FakeReportLogDao,
        feishuClient: FeishuClient,
        appConfig: AppConfig = AppConfig()
    ): ReportWorker {
        val context = mockk<Context>(relaxed = true)
        every { context.contentResolver } returns mockk(relaxed = true)

        val configManager = mockk<ConfigManager>(relaxed = true)
        every { configManager.getConfigBlocking() } returns appConfig

        val diagnosticLogger = mockk<DiagnosticLogger>(relaxed = true)
        val workerParams = mockk<WorkerParameters>(relaxed = true)

        return ReportWorker(
            context = context,
            workerParams = workerParams,
            configManager = configManager,
            locationRecordDao = locationDao,
            reportLogDao = reportLogDao,
            feishuClient = feishuClient,
            diagnosticLogger = diagnosticLogger
        )
    }

    @Test
    fun `4xx error advances past batch — does NOT loop forever`() = runTest {
        val locationDao = FakeLocationRecordDao()
        val reportLogDao = FakeReportLogDao()

        // One unreported record
        locationDao.records.add(makeRecord(id = 1))

        val feishuClient = mockk<FeishuClient>(relaxed = true)
        every { feishuClient.send(any(), any()) } returns FeishuClient.ReportResult(
            success = false, responseCode = 400
        )

        val worker = createWorker(locationDao, reportLogDao, feishuClient)

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)

        // KEY ASSERTION: feishuClient.send was called exactly ONCE
        // Before fix: infinite loop would call it many times
        verify(exactly = 1) { feishuClient.send(any(), any()) }

        // One report log entry with 400
        assertEquals("Should have exactly 1 report log", 1, reportLogDao.logs.size)
        assertEquals(400, reportLogDao.logs[0].responseCode)
        assertFalse(reportLogDao.logs[0].success)
    }

    @Test
    fun `success then 4xx — each batch processed exactly once`() = runTest {
        val locationDao = FakeLocationRecordDao()
        val reportLogDao = FakeReportLogDao()

        // Two records that will form two separate batches (batch_size=1 for testing)
        locationDao.records.add(makeRecord(id = 1))
        locationDao.records.add(makeRecord(id = 2))

        val feishuClient = mockk<FeishuClient>(relaxed = true)
        // First call: success, second call: 400
        every { feishuClient.send(any(), any()) } returnsMany listOf(
            FeishuClient.ReportResult(success = true, responseCode = 200),
            FeishuClient.ReportResult(success = false, responseCode = 400)
        )

        // Use a config with batch_size=1 so each record is its own batch
        val config = AppConfig(
            report = com.monitor.app.config.ReportConfig(batch_size = 1)
        )
        val worker = createWorker(locationDao, reportLogDao, feishuClient, config)

        worker.doWork()

        // send called exactly twice — once per batch, no looping
        verify(exactly = 2) { feishuClient.send(any(), any()) }

        // Two report log entries
        assertEquals(2, reportLogDao.logs.size)

        // First entry: success
        assertTrue(reportLogDao.logs[0].success)
        assertEquals(200, reportLogDao.logs[0].responseCode)

        // Second entry: abandoned with 400
        assertFalse(reportLogDao.logs[1].success)
        assertEquals(400, reportLogDao.logs[1].responseCode)

        // getLastReportedRecordId returns 1 (the successful batch's to_record_id)
        assertEquals(1, reportLogDao.getLastReportedRecordId())
    }

    @Test
    fun `consecutive 4xx errors — both batches abandoned, not looped`() = runTest {
        val locationDao = FakeLocationRecordDao()
        val reportLogDao = FakeReportLogDao()

        locationDao.records.add(makeRecord(id = 1))
        locationDao.records.add(makeRecord(id = 2))

        val feishuClient = mockk<FeishuClient>(relaxed = true)
        every { feishuClient.send(any(), any()) } returns FeishuClient.ReportResult(
            success = false, responseCode = 400
        )

        val config = AppConfig(
            report = com.monitor.app.config.ReportConfig(batch_size = 1)
        )
        val worker = createWorker(locationDao, reportLogDao, feishuClient, config)

        worker.doWork()

        // Two calls — one per batch, no retry of first batch
        verify(exactly = 2) { feishuClient.send(any(), any()) }
        assertEquals(2, reportLogDao.logs.size)
        // Neither should be marked success
        assertFalse(reportLogDao.logs[0].success)
        assertFalse(reportLogDao.logs[1].success)
    }

    @Test
    fun `empty database — worker exits cleanly`() = runTest {
        val locationDao = FakeLocationRecordDao()
        val reportLogDao = FakeReportLogDao()

        val feishuClient = mockk<FeishuClient>(relaxed = true)

        val worker = createWorker(locationDao, reportLogDao, feishuClient)

        val result = worker.doWork()

        assertEquals(androidx.work.ListenableWorker.Result.success(), result)
        verify(exactly = 0) { feishuClient.send(any(), any()) }
        assertEquals(0, reportLogDao.logs.size)
    }
}
