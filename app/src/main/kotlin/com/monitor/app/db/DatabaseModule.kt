package com.monitor.app.db

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context.applicationContext,
            AppDatabase::class.java,
            "monitor.db"
        ).build()
    }

    @Provides
    fun provideLocationRecordDao(db: AppDatabase): LocationRecordDao = db.locationRecordDao()

    @Provides
    fun provideReportLogDao(db: AppDatabase): ReportLogDao = db.reportLogDao()
}
