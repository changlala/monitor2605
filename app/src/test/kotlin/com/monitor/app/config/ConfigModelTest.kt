package com.monitor.app.config

import com.google.gson.Gson
import org.junit.Test
import org.junit.Assert.*

class ConfigModelTest {

    private val gson = Gson()

    @Test
    fun `parse minimal config`() {
        val json = """{"version": 1}"""
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals(1, cfg.version)
    }

    @Test
    fun `parse full config from spec`() {
        val json = javaClass.classLoader!!.getResource("test_config.json").readText()
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals(1, cfg.version)
        assertEquals(3, cfg.config_sources.size)
        assertEquals("07:00", cfg.location_strategy.peak_hours.start)
    }

    @Test
    fun `cross-midnight off_peak_hours parse correctly`() {
        val json = """{"location_strategy": {"off_peak_hours": {"start": "20:00", "end": "07:00"}}}"""
        val cfg = gson.fromJson(json, AppConfig::class.java)
        assertEquals("20:00", cfg.location_strategy.off_peak_hours.start)
        assertEquals("07:00", cfg.location_strategy.off_peak_hours.end)
    }
}
