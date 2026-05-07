package com.monitor.app.diag

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DiagnosticLogger @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logDir = File(context.filesDir, "logs")
    private val dateFormat = DateTimeFormatter.ofPattern("yyyy_MM_dd")
    private var currentDate: String? = null
    private var writer: BufferedWriter? = null
    private val lock = Any()

    init {
        logDir.mkdirs()
    }

    fun log(event: String, detail: String = "") {
        synchronized(lock) {
            try {
                val now = Instant.now()
                val today = LocalDate.ofInstant(now, ZoneId.systemDefault()).format(dateFormat)
                rotateIfNeeded(today)
                val ts = DateTimeFormatter.ISO_INSTANT.format(now)
                val line = if (detail.isNotEmpty()) "[$ts] $event | $detail" else "[$ts] $event"
                writer?.apply { write(line + "\n"); flush() }
            } catch (_: Exception) { }
        }
    }

    fun getLogFiles(): List<File> {
        return logDir.listFiles()?.filter { it.name.startsWith("diagnostic_") }
            ?.sortedByDescending { it.name } ?: emptyList()
    }

    private fun rotateIfNeeded(today: String) {
        if (today != currentDate) {
            writer?.close()
            writer = BufferedWriter(FileWriter(File(logDir, "diagnostic_$today.log"), true))
            currentDate = today
            purgeOldLogs()
        }
    }

    private fun purgeOldLogs() {
        val cutoff = LocalDate.now().minusDays(7).format(dateFormat)
        logDir.listFiles()?.filter { it.name.startsWith("diagnostic_") && it.name < "diagnostic_$cutoff" }
            ?.forEach { it.delete() }
    }
}
