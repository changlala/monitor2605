package com.monitor.app.location

import android.content.Context
import android.location.Location
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.monitor.app.config.ConfigManager
import com.monitor.app.diag.DiagnosticLogger
import com.monitor.app.util.BatteryMonitor
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
        val batteryPct = getBatterySnapshot()
        val decision = strategyDecider.decide(config, batteryPct)

        val location = withTimeoutOrNull(30_000L) {
            suspendCancellableCoroutine<Location?> { cont ->
                val cts = CancellationTokenSource()
                cont.invokeOnCancellation { cts.cancel() }
                val priority = if (decision.priority == "HIGH_ACCURACY")
                    Priority.PRIORITY_HIGH_ACCURACY
                else
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY

                fusedClient.getCurrentLocation(priority, cts.token)
                    .addOnSuccessListener { cont.resume(it) }
                    .addOnFailureListener { cont.resume(null) }
            }
        }

        if (location != null) {
            locationRepository.maybeInsert(location, batteryPct, decision.intervalSeconds)
            diagnosticLogger.log(
                "location_workmanager",
                """{"mode":"${decision.effectiveConfig}","pct":$batteryPct}"""
            )
        }

        return Result.success()
    }

    private suspend fun getBatterySnapshot(): Int {
        return try {
            withTimeout(2_000L) {
                BatteryMonitor.observe(context).first().pct
            }
        } catch (_: Exception) {
            100
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
