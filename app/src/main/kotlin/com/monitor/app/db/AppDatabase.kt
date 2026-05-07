package com.monitor.app.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.monitor.app.db.entities.LocationRecord
import com.monitor.app.db.entities.ReportLog

@Database(
    entities = [LocationRecord::class, ReportLog::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun locationRecordDao(): LocationRecordDao
    abstract fun reportLogDao(): ReportLogDao
}
