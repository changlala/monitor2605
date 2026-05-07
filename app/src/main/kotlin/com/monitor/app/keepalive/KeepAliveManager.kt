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
        return elapsed > 120_000L
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
