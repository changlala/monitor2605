package com.monitor.app.db

import androidx.room.*
import com.monitor.app.db.entities.ReportLog

@Dao
interface ReportLogDao {

    @Insert
    suspend fun insert(log: ReportLog): Long

    @Query("SELECT COALESCE(MAX(to_record_id), 0) FROM report_log WHERE success = 1")
    suspend fun getLastReportedRecordId(): Long

    @Query("DELETE FROM report_log WHERE success = 0 AND reported_at < :cutoff")
    suspend fun deleteFailedOlderThan(cutoff: Long): Int
}
