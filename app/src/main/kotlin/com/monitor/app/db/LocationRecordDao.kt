package com.monitor.app.db

import androidx.room.*
import com.monitor.app.db.entities.LocationRecord

@Dao
interface LocationRecordDao {

    @Insert
    suspend fun insert(record: LocationRecord): Long

    @Query("SELECT * FROM location_record WHERE id > :lastReportedId ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnreported(lastReportedId: Long, limit: Int): List<LocationRecord>

    @Query("DELETE FROM location_record WHERE id <= :lastReportedId AND recorded_at < :cutoff")
    suspend fun deleteReportedOlderThan(lastReportedId: Long, cutoff: Long): Int

    @Query("DELETE FROM location_record WHERE recorded_at < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM location_record")
    suspend fun count(): Int
}
