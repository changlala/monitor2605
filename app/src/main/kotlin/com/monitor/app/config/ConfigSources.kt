package com.monitor.app.config

import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigSources @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    fun fetch(sources: List<ConfigSource>, timeoutSeconds: Int): Result<String> {
        for (source in sources.sortedBy { it.priority }) {
            try {
                val request = Request.Builder().url(source.url).build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (body != null) return Result.success(body)
                }
            } catch (_: Exception) { }
        }
        return Result.failure(Exception("All config sources failed"))
    }
}
