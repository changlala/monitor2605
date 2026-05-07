package com.monitor.app.config

import com.monitor.app.diag.DiagnosticLogger
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSources @Inject constructor(
    private val diagnosticLogger: DiagnosticLogger
) {

    fun fetch(sources: List<ConfigSource>, timeoutSeconds: Int): Result<String> {
        val client = OkHttpClient.Builder()
            .connectTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .readTimeout(timeoutSeconds.toLong(), TimeUnit.SECONDS)
            .build()
        for (source in sources.sortedBy { it.priority }) {
            try {
                val request = Request.Builder().url(source.url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) return Result.success(body)
                }
            } catch (e: Exception) {
                diagnosticLogger.exception("config_fetch_fail|source=${source.url}", e)
            }
        }
        return Result.failure(Exception("All config sources failed"))
    }
}
