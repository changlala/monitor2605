package com.monitor.app.keepalive

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                diagnosticLogger.log("watchdog_restart_blocked", """{"reason":"background_launch_restricted"}""")
            }
        }
    }

    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return manager.getRunningServices(Int.MAX_VALUE)
            .any { it.service.className == serviceClass.name }
    }
}
