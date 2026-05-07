package com.monitor.app.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.monitor.app.diag.DiagnosticLogger
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@AndroidEntryPoint
class HiddenActivity : ComponentActivity() {

    @Inject lateinit var diagnosticLogger: DiagnosticLogger

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden)
        exportLogs()
    }

    private fun exportLogs() {
        try {
            val logFiles = diagnosticLogger.getLogFiles()
            val exportDir = File(getExternalFilesDir(null), "monitor_logs")
            exportDir.mkdirs()

            for (file in logFiles) {
                file.copyTo(File(exportDir, file.name), overwrite = true)
            }

            Toast.makeText(this, "日志已导出到 ${exportDir.absolutePath}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "导出失败: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
