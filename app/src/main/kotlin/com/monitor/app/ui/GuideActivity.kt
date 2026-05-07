package com.monitor.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.monitor.app.R
import com.monitor.app.location.LocationService

class GuideActivity : ComponentActivity() {

    private var started = false

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
                Toast.makeText(this, "请授予所有必需权限", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guide)

        if (allGranted()) {
            startServiceAndFinish()
            return
        }

        updateStatus()

        findViewById<Button>(R.id.btn_finish).setOnClickListener {
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }

        findViewById<Button>(R.id.btn_settings).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$packageName")
            }
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        if (started) return
        if (allGranted()) {
            startServiceAndFinish()
        } else {
            updateStatus()
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

        val oemHint = when {
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ->
                "\nOPPO 额外步骤：设置 → 应用管理 → 本应用 → 耗电保护 → 允许后台运行"
            Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ->
                "\n小米额外步骤：设置 → 应用设置 → 授权管理 → 自启动管理 → 允许本应用自启动"
            Build.MANUFACTURER.equals("Huawei", ignoreCase = true) ->
                "\n华为额外步骤：手机管家 → 应用启动管理 → 关闭自动管理 → 允许自启动"
            else -> ""
        }
        if (oemHint.isNotEmpty()) sb.append(oemHint)

        findViewById<TextView>(R.id.guide_text).text = sb.toString()
    }

    private fun startServiceAndFinish() {
        started = true
        val intent = Intent(this, LocationService::class.java)
        startForegroundService(intent)
        finish()
    }
}
