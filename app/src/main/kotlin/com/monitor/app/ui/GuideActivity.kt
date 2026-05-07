package com.monitor.app.ui

import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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
            Build.MANUFACTURER.equals("OPPO", ignoreCase = true) ||
            Build.MANUFACTURER.equals("OnePlus", ignoreCase = true) ->
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
