package app.xuyu.chat.net

import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * 联网搜索。
 * 默认走 DeepSeek 官方 Search 接口（同一把 Key）；也支持博查 / Tavily / 自建 SearXNG。
 */
class SearchClient {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val json = "application/json; charset=utf-8".toMediaType()

    suspend fun search(cfg: SearchConfig, query: String, chat: ApiConfig): List<Source> =
        withContext(Dispatchers.IO) {
            val q = query.replace("\n", " ").trim().take(200)
            if (q.isBlank()) return@withContext emptyList()
            when (cfg.provider) {
                "bocha" -> bocha(cfg, q)
                "tavily" -> tavily(cfg, q)
                "searxng" -> searxng(cfg, q)
                else -> deepseek(cfg, q, chat)
            }
        }

    /** DeepSeek 官方：POST /v1/tools/search，和对话共用 API Key */
    private fun deepseek(cfg: SearchConfig, q: String, chat: ApiConfig): List<Source> {
        val key = chat.apiKey.trim()
        if (key.isBlank()) throw ApiException("还没有填 API Key，联网搜索用的是同一把")
        val base = cfg.baseUrl.trim().ifBlank { chat.baseUrl }
        val body = JSONObject().apply {
            put("query", q)
            put("count", cfg.maxResults.coerceIn(1, 50))
            put("need_content", false)
        }.toString()
        val req = Request.Builder()
            .url(deepseekEndpoint(base))
            .addHeader("Authorization", "Bearer $key")
            .post(body.toRequestBody(json))
            .build()
        val root = JSONObject(execute(req))
        val arr = root.optJSONArray("results") ?: return emptyList()
        val out = mutableListOf<Source>()
        for (i in 0 until minOf(arr.length(), cfg.maxResults)) {
            val o = arr.optJSONObject(i) ?: continue
            val url = o.str("url").trim()
            val site = o.str("site_name").trim()
            val title = o.str("title").trim().ifBlank { site }.ifBlank { url }
            val snippet = o.str("snippet").trim()
            val summary = o.str("summary").trim()
            if (url.isBlank() && title.isBlank()) continue
            out += Source(
                title = title,
                url = url,
                snippet = snippet,
                detail = summary,
                site = site
            )
        }
        return out
    }

    private fun deepseekEndpoint(baseUrl: String): String {
        var b = baseUrl.trim().ifBlank { "https://api.deepseek.com" }
        if (!b.startsWith("http://") && !b.startsWith("https://")) b = "https://$b"
        b = b.trimEnd('/')
        if (b.endsWith("/chat/completions")) b = b.removeSuffix("/chat/completions")
        return if (b.endsWith("/v1")) "$b/tools/search" else "$b/v1/tools/search"
    }

    // ---------------- 第三方 ----------------

    private fun bocha(cfg: SearchConfig, q: String): List<Source> {
        val url = cfg.baseUrl.trim().ifBlank { "https://api.bochaai.com/v1/web-search" }
        val body = JSONObject().apply {
            put("query", q)
            put("summary", true)
            put("count", cfg.maxResults.coerceIn(1, 10))
        }.toString()
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")
            .post(body.toRequestBody(json))
            .build()
        return parse(execute(req), cfg.maxResults, "博查")
    }

    private fun tavily(cfg: SearchConfig, q: String): List<Source> {
        val url = cfg.baseUrl.trim().ifBlank { "https://api.tavily.com/search" }
        val body = JSONObject().apply {
            put("query", q)
            put("max_results", cfg.maxResults.coerceIn(1, 10))
            put("search_depth", "basic")
            put("include_answer", false)
            put("api_key", cfg.apiKey.trim())
        }.toString()
        val req = Request.Builder()
            .url(url)
            .addHeader("Authorization", "Bearer ${cfg.apiKey.trim()}")
            .post(body.toRequestBody(json))
            .build()
        return parse(execute(req), cfg.maxResults, "Tavily")
    }

    private fun searxng(cfg: SearchConfig, q: String): List<Source> {
        val base = cfg.baseUrl.trim().trimEnd('/')
        if (base.isBlank()) throw ApiException("SearXNG 需要填服务地址")
        val url = "$base/search?q=" + URLEncoder.encode(q, "UTF-8") +
            "&format=json&language=zh-CN&safesearch=0"
        val req = Request.Builder().url(url).get().build()
        return parse(execute(req), cfg.maxResults, "SearXNG")
    }

    // ---------------- 通用 ----------------

    private fun execute(req: Request): String {
        return http.newCall(req).execute().use { resp ->
            val raw = try {
                resp.body?.string().orEmpty()
            } catch (_: Exception) {
                ""
            }
            if (!resp.isSuccessful) {
                val detail = try {
                    val o = JSONObject(raw)
                    o.str("message").ifBlank { o.str("msg") }.ifBlank {
                        o.optJSONObject("error").str("message")
                    }
                } catch (_: Exception) {
                    ""
                }
                throw ApiException(
                    when {
                        detail.isNotBlank() -> detail
                        resp.code == 401 || resp.code == 403 -> "搜索鉴权失败（HTTP ${resp.code}）"
                        resp.code == 402 -> "余额不足，搜不了（HTTP 402）"
                        resp.code == 404 -> "这个接口没有 /tools/search（HTTP 404）"
                        resp.code == 429 -> "搜索请求太频繁（HTTP 429）"
                        else -> "搜索请求失败（HTTP ${resp.code}）"
                    },
                    resp.code
                )
            }
            raw
        }
    }

    private fun parse(raw: String, limit: Int, provider: String): List<Source> {
        val root = try {
            JSONObject(raw)
        } catch (e: Exception) {
            throw ApiException("$provider 返回的内容看不懂")
        }
        if (root.has("code") && !root.isNull("code")) {
            val code = root.optInt("code", 200)
            if (code != 200) {
                throw ApiException(root.str("msg").ifBlank { "$provider 返回错误 $code" })
            }
        }
        val arr = findResults(root) ?: return emptyList()
        val out = mutableListOf<Source>()
        for (i in 0 until minOf(arr.length(), limit)) {
            val o = arr.optJSONObject(i) ?: continue
            val title = o.str("title").ifBlank { o.str("name") }.trim()
            val url = o.str("url").ifBlank { o.str("link") }.trim()
            val snippet = o.str("content")
                .ifBlank { o.str("snippet") }
                .trim()
            val detail = o.str("summary")
                .ifBlank { o.str("description") }
                .ifBlank { snippet }
                .trim()
            if (title.isBlank() && url.isBlank()) continue
            out += Source(
                title = title.ifBlank { url },
                url = url,
                snippet = snippet.take(600),
                detail = detail.take(2000)
            )
        }
        return out
    }

    private fun findResults(root: JSONObject): JSONArray? {
        root.optJSONArray("results")?.let { return it }
        root.optJSONObject("data")?.let { d ->
            d.optJSONArray("results")?.let { return it }
            d.optJSONObject("webPages")?.optJSONArray("value")?.let { return it }
        }
        root.optJSONObject("webPages")?.optJSONArray("value")?.let { return it }
        return null
    }
}
