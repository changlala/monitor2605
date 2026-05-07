package com.monitor.app.keepalive

import android.app.ForegroundServiceStartNotAllowedException
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
            try {
                context.startForegroundService(serviceIntent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                diagnosticLogger.log("service_restart_blocked", """{"reason":"background_launch_restricted"}""")
            }
        }
    }
}
