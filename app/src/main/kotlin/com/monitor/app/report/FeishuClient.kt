package com.monitor.app.report

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeishuClient @Inject constructor() {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class ReportResult(
        val success: Boolean,
        val responseCode: Int,
        val error: String? = null
    )

    fun send(webhookUrl: String, payload: ReportPayload.Batch): ReportResult {
        return try {
            val cardJson = buildCardJson(payload)
            val body = cardJson.toRequestBody(jsonMediaType)
            val request = Request.Builder().url(webhookUrl).post(body).build()
            val response = client.newCall(request).execute()
            val isSuccess = response.isSuccessful
            val respBody = response.body?.string() ?: ""
            val feishuOk = respBody.contains("\"code\":0")
            ReportResult(
                success = isSuccess && feishuOk,
                responseCode = response.code
            )
        } catch (e: Exception) {
            ReportResult(success = false, responseCode = -1, error = e.message)
        }
    }

    private fun buildCardJson(batch: ReportPayload.Batch): String {
        val recordsJson = batch.records.joinToString(",") {
            """{"lat":${it.lat},"lng":${it.lng},"alt":${it.alt ?: "null"},"acc":${it.acc ?: "null"},"provider":"${it.provider}","ts":${it.ts},"battery":${it.battery ?: "null"}}"""
        }
        return """
        {
          "msg_type": "interactive",
          "card": {
            "header": {
              "title": {"content": "位置上报", "tag": "plain_text"},
              "template": "blue"
            },
            "elements": [
              {"tag": "plain_text", "content": "设备: ${batch.deviceId}"},
              {"tag": "plain_text", "content": "时间戳: ${batch.timestamp}"},
              {"tag": "plain_text", "content": "条数: ${batch.records.size}"},
              {"tag": "plain_text", "content": "数据: [$recordsJson]"}
            ]
          }
        }
        """.trimIndent()
    }
}
