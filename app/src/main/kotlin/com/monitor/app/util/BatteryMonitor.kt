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
