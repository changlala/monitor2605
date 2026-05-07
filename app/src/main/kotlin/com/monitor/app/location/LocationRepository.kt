package com.monitor.app.location

import android.location.Location
import com.monitor.app.db.LocationRecordDao
import com.monitor.app.db.entities.LocationRecord
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationRepository @Inject constructor(
    private val locationRecordDao: LocationRecordDao
) {
    private var lastInsertedLocation: Location? = null
    private var lastRecordedMinute: Long = 0

    suspend fun maybeInsert(location: Location, batteryPct: Int?, minIntervalSeconds: Int) {
        val now = System.currentTimeMillis()
        val currentMinute = now / 60_000

        // Always keep at least one record per minute
        val forceKeep = currentMinute != lastRecordedMinute

        if (!forceKeep && shouldDebounce(location, minIntervalSeconds)) {
            return
        }

        val record = LocationRecord(
            latitude = location.latitude,
            longitude = location.longitude,
            altitude = if (location.hasAltitude()) location.altitude else null,
            accuracy = if (location.hasAccuracy()) location.accuracy else null,
            provider = location.provider ?: "unknown",
            recordedAt = location.time,
            batteryPct = batteryPct
        )
        locationRecordDao.insert(record)
        lastInsertedLocation = location
        lastRecordedMinute = currentMinute
    }

    private fun shouldDebounce(location: Location, minIntervalSeconds: Int): Boolean {
        val prev = lastInsertedLocation ?: return false
        val distance = distanceBetween(prev, location)
        val timeDelta = location.time - prev.time
        return distance < 1.0 && timeDelta < (minIntervalSeconds * 1000L / 2)
    }

    private fun distanceBetween(a: Location, b: Location): Double {
        val dLat = Math.toRadians(b.latitude - a.latitude)
        val dLon = Math.toRadians(b.longitude - a.longitude)
        val lat1 = Math.toRadians(a.latitude)
        val lat2 = Math.toRadians(b.latitude)
        val aH = sin(dLat / 2).pow(2) + sin(dLon / 2).pow(2) * cos(lat1) * cos(lat2)
        val c = 2 * atan2(sqrt(aH), sqrt(1 - aH))
        return 6_371_000 * c
    }

    private fun Double.pow(n: Int): Double = Math.pow(this, n.toDouble())
}
