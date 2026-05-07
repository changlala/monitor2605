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
        val gson = com.google.gson.Gson()
        val recordsArray = com.google.gson.JsonArray()
        for (r in batch.records) {
            val obj = com.google.gson.JsonObject().apply {
                addProperty("lat", r.lat)
                addProperty("lng", r.lng)
                addProperty("alt", r.alt)
                addProperty("acc", r.acc)
                addProperty("provider", r.provider)
                addProperty("ts", r.ts)
                addProperty("battery", r.battery)
            }
            recordsArray.add(obj)
        }
        val card = gson.toJson(mapOf(
            "msg_type" to "interactive",
            "card" to mapOf(
                "header" to mapOf(
                    "title" to mapOf("content" to "位置上报", "tag" to "plain_text"),
                    "template" to "blue"
                ),
                "elements" to listOf(
                    mapOf("tag" to "plain_text", "content" to "设备: ${batch.deviceId}"),
                    mapOf("tag" to "plain_text", "content" to "时间戳: ${batch.timestamp}"),
                    mapOf("tag" to "plain_text", "content" to "条数: ${batch.records.size}"),
                    mapOf("tag" to "plain_text", "content" to "数据: ${gson.toJson(recordsArray)}")
                )
            )
        ))
        return gson.toJson(card)
    }
}
