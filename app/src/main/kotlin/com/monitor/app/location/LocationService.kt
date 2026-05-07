package com.monitor.app.location

import android.app.NotificationChannel
import android.app.NotificationManager
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
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
        wakeLock.acquire()
        createNotificationChannel()
        diagnosticLogger.log("service_start")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        dispatcher.onServicePreSuperOnStart()
        try {
            val config = configManager.getConfigBlocking()
            val notification = buildNotification(config)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(1, notification)
            }
            startLocationUpdates(config)

            // Periodic strategy re-evaluation
            scope.launch {
                while (isActive) {
                    delay(60_000L) // Check every minute
                    val currentConfig = configManager.getConfigBlocking()
                    val pct = getBatterySnapshot()
                    val d = strategyDecider.decide(currentConfig, pct)

                    if (d.forceWorkManager) {
                        // Stop continuous updates, switch to WorkManager
                        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
                        LocationWorker.schedule(this@LocationService, d.intervalSeconds)
                        diagnosticLogger.log("mode_switch", """{"to":"${d.effectiveConfig}","reason":"battery_critical"}""")
                        // Stay as foreground service for keep-alive, but stop collecting
                        // Cancel this coroutine
                        cancel()
                    }
                }
            }
        } catch (e: Exception) {
            diagnosticLogger.exception("service_onstartcommand", e)
            // Rethrow to let START_STICKY restart us
            throw e
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates(config: AppConfig) {
        // Remove any existing callback to prevent duplicate registrations on restart
        locationCallback?.let { fusedClient.removeLocationUpdates(it) }
        locationCallback = null

        val decision = strategyDecider.decide(config, 100)

        if (decision.forceWorkManager) {
            // Critical battery: defer to WorkManager only, don't use continuous updates
            diagnosticLogger.log("mode_switch", """{"to":"${decision.effectiveConfig}","reason":"force_workmanager"}""")
            LocationWorker.schedule(this, decision.intervalSeconds)
            return
        }

        val request = LocationRequest.Builder(decision.intervalSeconds * 1000L)
            .setMinUpdateIntervalMillis(decision.intervalSeconds * 1000L)
            .setMaxUpdateDelayMillis((decision.intervalSeconds * 1500L))
            .setPriority(if (decision.priority == "HIGH_ACCURACY")
                Priority.PRIORITY_HIGH_ACCURACY else Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                scope.launch {
                    val pct = getBatterySnapshot()
                    var lastDecision: com.monitor.app.location.StrategyDecider.Decision? = null
                    for (loc in result.locations) {
                        val d = strategyDecider.decide(config, pct)
                        locationRepository.maybeInsert(loc, pct, d.intervalSeconds)
                        lastDecision = d
                    }
                    val d = lastDecision ?: return@launch
                    diagnosticLogger.log("location_collected",
                        """{"mode":"${d.effectiveConfig}","count":${result.locations.size},"pct":$pct}""")
                }
            }
        }
        locationCallback = callback

        try {
            fusedClient.requestLocationUpdates(request, callback, mainLooper)
        } catch (e: SecurityException) {
            diagnosticLogger.log("location_permission_denied")
        }
    }

    private suspend fun getBatterySnapshot(): Int {
        return try {
            withTimeout(2000L) {
                BatteryMonitor.observe(this@LocationService).first().pct
            }
        } catch (e: Exception) {
            diagnosticLogger.exception("get_battery_snapshot_service", e)
            100
        }
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
