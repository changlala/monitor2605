package com.monitor.app.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "report_log")
data class ReportLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "from_record_id") val fromRecordId: Long,
    @ColumnInfo(name = "to_record_id") val toRecordId: Long,
    @ColumnInfo(name = "record_count") val recordCount: Int,
    @ColumnInfo(name = "reported_at") val reportedAt: Long,
    @ColumnInfo(name = "response_code") val responseCode: Int?,
    @ColumnInfo(name = "success") val success: Boolean = false
)
