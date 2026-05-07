package com.monitor.app.report

import com.monitor.app.db.entities.LocationRecord

object ReportPayload {

    data class Record(
        val lat: Double,
        val lng: Double,
        val alt: Double?,
        val acc: Float?,
        val provider: String,
        val ts: Long,
        val battery: Int?
    )

    data class Batch(
        val timestamp: Long,
        val deviceId: String,
        val records: List<Record>
    )

    fun build(records: List<LocationRecord>, deviceId: String): Batch {
        return Batch(
            timestamp = System.currentTimeMillis(),
            deviceId = deviceId,
            records = records.map { r ->
                Record(
                    lat = r.latitude,
                    lng = r.longitude,
                    alt = r.altitude,
                    acc = r.accuracy,
                    provider = r.provider,
                    ts = r.recordedAt,
                    battery = r.batteryPct
                )
            }
        )
    }
}
