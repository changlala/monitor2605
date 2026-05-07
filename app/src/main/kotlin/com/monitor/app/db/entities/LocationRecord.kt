package com.monitor.app.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "location_record",
    indices = [Index(value = ["recorded_at"])]
)
data class LocationRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,
    @ColumnInfo(name = "altitude") val altitude: Double?,
    @ColumnInfo(name = "accuracy") val accuracy: Float?,
    @ColumnInfo(name = "provider") val provider: String,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long,
    @ColumnInfo(name = "battery_pct") val batteryPct: Int?,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis()
)
