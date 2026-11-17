package app.xuyu.chat.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(message: String, val code: Int = 0) : IOException(message)

data class WireMessage(val role: String, val content: String)

/** DeepSeek /user/balance 的结果 */
data class Balance(
    val available: Boolean,
    val currency: String,
    val total: String,
    val granted: String,
    val toppedUp: String
) {
    fun pretty(): String {
        val symbol = when (currency.uppercase()) {
            "CNY" -> "¥"
            "USD" -> "$"
            else -> ""
        }
        val extras = mutableListOf<String>()
        granted.toDoubleOrNull()?.let { if (it > 0) extras.add("赠送 $granted") }
        toppedUp.toDoubleOrNull()?.let { if (it > 0) extras.add("充值 $toppedUp") }
        return symbol + total + if (extras.isEmpty()) "" else "（" + extras.joinToString(" / ") + "）"
    }
}

/** 一个够用的 OpenAI 兼容客户端（DeepSeek / 中转 / Ollama 都能上） */
class ChatClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile
    private var call: Call? = null

    fun cancel() {
        try {
            call?.cancel()
        } catch (_: Exception) {
        }
    }

    private fun normalize(baseUrl: String): String {
        var b = baseUrl.trim().ifBlank { "https://api.deepseek.com" }
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://$b"
        return b.trimEnd('/')
    }

    fun endpoint(baseUrl: String): String {
        val b = normalize(baseUrl)
        return if (b.endsWith("/chat/completions")) b else "$b/chat/completions"
    }

    fun balanceEndpoint(baseUrl: String): String {
        var b = normalize(baseUrl)
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions")
        if (b.endsWith("/v1")) b = b.removeSuffix("/v1")
        return "$b/user/balance"
    }

    fun modelsEndpoint(baseUrl: String): String {
        var b = normalize(baseUrl)
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions")
        return "$b/models"
    }

    // ---------------- 流式对话 ----------------

    suspend fun stream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<WireMessage>,
        temperature: Float,
        maxTokens: Int,
        thinking: Boolean? = null,
        onUsage: ((prompt: Int, completion: Int) -> Unit)? = null,
        onDelta: (content: String, reasoning: String) -> Unit
    ) = withContext(Dispatchers.IO) {
        var emitted = false
        val wrapped: (String, String) -> Unit = { c, r ->
            if (c.isNotEmpty() || r.isNotEmpty()) emitted = true
            onDelta(c, r)
        }
        try {
            doStream(baseUrl, apiKey, model, messages, temperature, maxTokens, true, thinking, onUsage, wrapped)
        } catch (e: ApiException) {
            // 有些兼容接口不认 stream_options，退回普通流式再来一次
            if (e.code == 400 && !emitted) {
                doStream(baseUrl, apiKey, model, messages, temperature, maxTokens, false, thinking, onUsage, wrapped)
            } else {
                throw e
            }
        }
    }

    private fun doStream(
        baseUrl: String,
        apiKey: String,
        model: String,
        messages: List<WireMessage>,
        temperature: Float,
        maxTokens: Int,
        includeUsage: Boolean,
        thinking: Boolean?,
        onUsage: ((prompt: Int, completion: Int) -> Unit)?,
        onDelta: (content: String, reasoning: String) -> Unit
    ) {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", true)
            if (includeUsage) put("stream_options", JSONObject().put("include_usage", true))
            // 思考模式是 DeepSeek 的新参数；别的接口不塞，免得不认
            if (thinking != null && model.startsWith("deepseek", ignoreCase = true)) {
                put("thinking", JSONObject().put("type", if (thinking) "enabled" else "disabled"))
            }
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject().put("role", m.role).put("content", m.content))
                }
            })
            if (!model.contains("reasoner")) put("temperature", temperature.toDouble())
            if (maxTokens > 0) put("max_tokens", maxTokens)
        }.toString()

        val req = Request.Builder()
            .url(endpoint(baseUrl))
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .addHeader("Accept", "text/event-stream")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        val c = http.newCall(req)
        call = c
        var usageSent = false
        try {
            val resp = c.execute()
            if (!resp.isSuccessful) {
                val raw = try {
                    resp.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                throw ApiException(errorMessage(resp.code, raw), resp.code)
            }
            val src = resp.body?.source() ?: throw ApiException("服务器没有返回内容")
            src.use {
                while (true) {
                    val line = try {
                        it.readUtf8Line()
                    } catch (e: IOException) {
                        if (c.isCanceled()) break else throw e
                    } ?: break
                    if (line.isEmpty() || line.startsWith(":")) continue
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data == "[DONE]") break
                    val obj = try {
                        JSONObject(data)
                    } catch (_: Exception) {
                        continue
                    }
                    if (!usageSent && onUsage != null) {
                        val usage = obj.optJSONObject("usage")
                        if (usage != null) {
                            val p = usage.optInt("prompt_tokens", 0)
                            val comp = usage.optInt("completion_tokens", 0)
                            if (p > 0 || comp > 0) {
                                usageSent = true
                                onUsage.invoke(p, comp)
                            }
                        }
                    }
                    val delta = obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")
                    val content = delta.str("content")
                    val reasoning = delta.str("reasoning_content")
                    if (content.isNotEmpty() || reasoning.isNotEmpty()) onDelta(content, reasoning)
                }
            }
        } finally {
            call = null
        }
    }

    // ---------------- 余额 ----------------

    suspend fun balance(baseUrl: String, apiKey: String): Balance = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url(balanceEndpoint(baseUrl))
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .get()
            .build()
        val c = http.newCall(req)
        call = c
        try {
            c.execute().use { resp ->
                val raw = try {
                    resp.body?.string().orEmpty()
                } catch (_: Exception) {
                    ""
                }
                if (!resp.isSuccessful) throw ApiException(errorMessage(resp.code, raw), resp.code)
                val o = JSONObject(raw)
                val info = o.optJSONArray("balance_infos")?.optJSONObject(0)
                Balance(
                    available = o.optBoolean("is_available", false),
                    currency = info.str("currency"),
                    total = info.str("total_balance"),
                    granted = info.str("granted_balance"),
                    toppedUp = info.str("topped_up_balance")
                )
            }
        } finally {
            call = null
        }
    }

    // ---------------- 测试连接 ----------------

    suspend fun test(baseUrl: String, apiKey: String, model: String): String = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("max_tokens", 16)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "只回复两个字：在的")))
        }.toString()
        val req = Request.Builder()
            .url(endpoint(baseUrl))
            .addHeader("Authorization", "Bearer ${apiKey.trim()}")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val c = http.newCall(req)
        call = c
        try {
            val resp = c.execute()
            val raw = try {
                resp.body?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (!resp.isSuccessful) throw ApiException(errorMessage(resp.code, raw), resp.code)
            val obj = JSONObject(raw)
            obj.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")
                ?.str("content").orEmpty().ifBlank { "连接成功" }
        } finally {
            call = null
        }
    }

    private fun errorMessage(code: Int, raw: String): String {
        val detail = try {
            JSONObject(raw).optJSONObject("error").str("message")
        } catch (_: Exception) {
            ""
        }
        return when {
            detail.isNotBlank() -> detail
            code == 401 -> "API Key 无效或已失效（HTTP 401）"
            code == 402 -> "账户余额不足（HTTP 402）"
            code == 403 -> "没有权限访问该模型（HTTP 403）"
            code == 404 -> "接口地址不对，或该接口不支持这个功能（HTTP 404）"
            code == 429 -> "请求太频繁或额度用尽（HTTP 429）"
            else -> "请求失败（HTTP $code）"
        }
    }
}
