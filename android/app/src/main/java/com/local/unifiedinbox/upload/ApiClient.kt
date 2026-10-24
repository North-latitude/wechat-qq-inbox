package com.local.unifiedinbox.upload

import com.local.unifiedinbox.model.OutboxItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.nio.charset.StandardCharsets

sealed class ApiException(message: String) : Exception(message)

class AuthException(message: String = "鉴权失败") : ApiException(message)

class PermanentException(message: String = "请求被服务器拒绝") : ApiException(message)

class RetryableException(message: String = "网络或服务暂不可用") : ApiException(message)

data class PingResponse(val serverTime: String?)

data class BatchResponse(
    val accepted: Int,
    val duplicates: Int
)

/**
 * Minimal HttpURLConnection client. The bearer token is sent only in the Authorization header,
 * never in a URL, and response/request bodies are never logged.
 */
class ApiClient {

    suspend fun ping(baseUrl: String, token: String): PingResponse = withContext(Dispatchers.IO) {
        val response = execute(
            method = "GET",
            url = "$baseUrl/api/v1/ping",
            token = token,
            body = null
        )
        val serverTime = try {
            JSONObject(response.body).optString("server_time", "").ifBlank { null }
        } catch (_: Exception) {
            null
        }
        PingResponse(serverTime = serverTime)
    }

    suspend fun sendBatch(
        baseUrl: String,
        token: String,
        deviceId: String,
        items: List<OutboxItem>
    ): BatchResponse = withContext(Dispatchers.IO) {
        if (items.isEmpty()) return@withContext BatchResponse(0, 0)
        val messages = JSONArray()
        for (item in items.take(MAX_BATCH_SIZE)) {
            try {
                val message = JSONObject(item.payload)
                val matchedRules = message.optJSONArray("matched_rules")
                if (matchedRules == null || matchedRules.length() == 0) {
                    throw PermanentException("本地队列缺少命中规则")
                }
                messages.put(message)
            } catch (exception: PermanentException) {
                throw exception
            } catch (_: Exception) {
                throw PermanentException("本地队列数据格式异常")
            }
        }
        val body = JSONObject().apply {
            put("device_id", deviceId)
            put("messages", messages)
        }.toString()

        val response = execute(
            method = "POST",
            url = "$baseUrl/api/v1/messages/batch",
            token = token,
            body = body
        )
        try {
            val json = JSONObject(response.body)
            return@withContext BatchResponse(
                accepted = json.optInt("accepted", items.size),
                duplicates = json.optInt("duplicates", 0)
            )
        } catch (_: Exception) {
            return@withContext BatchResponse(accepted = items.size, duplicates = 0)
        }
    }

    suspend fun sendHeartbeat(
        baseUrl: String,
        token: String,
        payload: JSONObject
    ) = withContext(Dispatchers.IO) {
        execute(
            method = "POST",
            url = "$baseUrl/api/v1/collector/heartbeat",
            token = token,
            body = payload.toString()
        )
        Unit
    }

    private fun execute(
        method: String,
        url: String,
        token: String,
        body: String?
    ): HttpResult {
        val connection: HttpURLConnection = try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = TIMEOUT_MILLIS
                readTimeout = TIMEOUT_MILLIS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("Accept", "application/json")
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
            }
        } catch (exception: SocketTimeoutException) {
            throw RetryableException("网络超时")
        } catch (exception: IOException) {
            throw RetryableException("网络连接失败")
        }

        try {
            if (body != null) {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { output ->
                    output.write(bytes)
                }
            }

            val status = connection.responseCode
            val responseBody = readBody(connection, status)
            when {
                status in 200..299 -> return HttpResult(status = status, body = responseBody)
                status == 401 || status == 403 -> throw AuthException(
                    parseDetail(responseBody) ?: "鉴权失败"
                )
                status == 429 -> throw RetryableException(
                    parseDetail(responseBody) ?: "请求过于频繁，稍后重试"
                )
                status in 500..599 -> throw RetryableException(
                    parseDetail(responseBody) ?: "服务器暂时不可用"
                )
                status in 300..399 -> throw PermanentException("服务器返回了重定向")
                status in 400..499 -> throw PermanentException(
                    parseDetail(responseBody) ?: "请求被服务器拒绝"
                )
                else -> throw RetryableException("服务器返回异常状态")
            }
        } catch (exception: SocketTimeoutException) {
            throw RetryableException("网络超时")
        } catch (exception: IOException) {
            throw RetryableException("网络连接失败")
        } finally {
            connection.disconnect()
        }
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""
        return stream.bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(MAX_BODY_CHARS)
            var total = 0
            while (total < buffer.size) {
                val read = reader.read(buffer, total, buffer.size - total)
                if (read <= 0) break
                total += read
            }
            String(buffer, 0, total)
        }
    }

    private fun parseDetail(body: String): String? {
        if (body.isBlank()) return null
        val detail = try {
            JSONObject(body).optString("detail", "")
        } catch (_: Exception) {
            ""
        }
        return detail
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
            .take(MAX_DETAIL_CHARS)
            .ifBlank { null }
    }

    private data class HttpResult(
        val status: Int,
        val body: String
    )

    companion object {
        private const val TIMEOUT_MILLIS = 15_000
        private const val MAX_BATCH_SIZE = 50
        private const val MAX_BODY_CHARS = 65_536
        private const val MAX_DETAIL_CHARS = 300
    }
}
