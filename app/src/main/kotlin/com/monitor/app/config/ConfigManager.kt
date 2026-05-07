package com.monitor.app.config

import android.content.Context
import com.google.gson.Gson
import com.monitor.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ConfigManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val configSources: ConfigSources
) {
    private val gson = Gson()
    private val configFile = File(context.filesDir, "current_config.json")
    private val _config = MutableStateFlow<AppConfig>(AppConfig())
    val config: StateFlow<AppConfig> = _config.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun init() {
        val cached = loadCached()
        _config.value = cached
        scope.launch { fetchAndApply() }
    }

    private suspend fun fetchAndApply() {
        val current = _config.value
        val sources = current.config_sources.ifEmpty { getHardcodedSources() }
        val result = withContext(Dispatchers.IO) {
            configSources.fetch(sources, current.config_fetch_timeout_seconds)
        }
        result.onSuccess { rawJson ->
            val cleanJson = stripComments(rawJson)
            val newConfig = gson.fromJson(cleanJson, AppConfig::class.java)
            saveCached(cleanJson)
            _config.value = newConfig
        }
    }

    private fun loadCached(): AppConfig {
        return try {
            if (configFile.exists()) {
                val raw = configFile.readText()
                gson.fromJson(raw, AppConfig::class.java)
            } else {
                AppConfig(config_sources = getHardcodedSources())
            }
        } catch (_: Exception) {
            AppConfig(config_sources = getHardcodedSources())
        }
    }

    private fun saveCached(json: String) {
        try { configFile.writeText(json) } catch (_: Exception) { }
    }

    private fun stripComments(json5: String): String {
        return json5.lines()
            .map { line ->
                var result = line.replace(Regex("(?<!:)//.*$"), "")  // Don't strip :// in URLs
                result = result.replace(Regex("/\\*.*?\\*/", setOf(RegexOption.DOT_MATCHES_ALL)), "")
                result
            }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun getHardcodedSources(): List<ConfigSource> {
        val urls = BuildConfig.DEFAULT_CONFIG_SOURCE_URLS.split(";")
        return urls.mapIndexed { i, url -> ConfigSource(url = url.trim(), priority = i + 1) }
    }

    fun getConfigBlocking(): AppConfig = _config.value
}
