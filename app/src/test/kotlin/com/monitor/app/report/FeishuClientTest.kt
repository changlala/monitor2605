package com.monitor.app.report

import com.google.gson.Gson
import com.monitor.app.diag.DiagnosticLogger
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class FeishuClientTest {

    private val client = FeishuClient(mockk<DiagnosticLogger>(relaxed = true))
    private val gson = Gson()

    @Test
    fun `buildCardJson produces JSON object not double-encoded string`() {
        val batch = ReportPayload.Batch(
            timestamp = 1234567890000L,
            deviceId = "test-device-001",
            records = listOf(
                ReportPayload.Record(
                    lat = 39.9042, lng = 116.4074, alt = 50.5,
                    acc = 10.0f, provider = "gps",
                    ts = 1234567890000L, battery = 80
                )
            )
        )

        val json = client.buildCardJson(batch)

        // Must start with { (JSON object), NOT " (double-encoded string literal)
        val trimmed = json.trimStart()
        assertTrue("JSON must start with '{', got: ${trimmed.take(5)}", trimmed.startsWith("{"))
        assertFalse("JSON must NOT start with '\"', got: ${trimmed.take(5)}", trimmed.startsWith("\""))

        // Must be valid JSON parseable as a Map
        val parsed = gson.fromJson(json, Map::class.java) as Map<*, *>
        assertEquals("interactive", parsed["msg_type"])
        assertNotNull(parsed["card"])
    }

    @Test
    fun `buildCardJson wraps content in correct card structure`() {
        val batch = ReportPayload.Batch(
            timestamp = 0L,
            deviceId = "device-1",
            records = listOf(
                ReportPayload.Record(
                    lat = 1.0, lng = 2.0, alt = null,
                    acc = null, provider = "network",
                    ts = 1000L, battery = null
                )
            )
        )

        val json = client.buildCardJson(batch)
        val parsed = gson.fromJson(json, Map::class.java) as Map<*, *>

        // Verify card structure
        val card = parsed["card"] as Map<*, *>
        assertNotNull(card["header"])
        val elements = card["elements"] as List<*>
        assertTrue("Should have at least 4 elements", elements.size >= 4)
    }

    @Test
    fun `buildCardJson with empty records still produces valid card`() {
        val batch = ReportPayload.Batch(
            timestamp = 0L,
            deviceId = "device-x",
            records = emptyList()
        )

        val json = client.buildCardJson(batch)
        val trimmed = json.trimStart()
        assertTrue("Should start with {", trimmed.startsWith("{"))
        val parsed = gson.fromJson(json, Map::class.java) as Map<*, *>
        assertEquals("interactive", parsed["msg_type"])
    }
}
