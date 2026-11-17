package app.xuyu.chat.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 示例对话中的一轮 */
data class Example(val user: String = "", val ai: String = "") {
    fun toJson(): JSONObject = JSONObject().put("u", user).put("a", ai)

    companion object {
        fun fromJson(o: JSONObject?): Example =
            Example(o?.optString("u").orEmpty(), o?.optString("a").orEmpty())
    }
}

/** 人设卡 */
data class Persona(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "絮语",
    val tagline: String = "一个愿意陪你慢慢聊天的 AI",
    val avatarPath: String? = null,
    val systemPrompt: String = DEFAULT_PROMPT,
    val greeting: String = "嗨，我在。\n今天想聊点什么？",
    val examples: List<Example> = emptyList(),
    val temperature: Float = 0.7f,
    val maxTokens: Int = 2048,
    val modelOverride: String = "",
    /** 思考模式；deepseek-flash / v4-pro 支持，其他接口忽略 */
    val thinking: Boolean = true,
    /** 场景 / 世界观（RP 模式） */
    val scene: String = "",
    /** 「我」在这个场景里是谁（RP 模式） */
    val userRole: String = "",
    /** 0 短 / 1 中 / 2 长 */
    val replyStyle: Int = 1,
    /** 允许 *动作描写* */
    val allowActions: Boolean = true,
    /** 强调不出戏 */
    val stayInCharacter: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("tagline", tagline)
        put("avatarPath", avatarPath.orEmpty())
        put("systemPrompt", systemPrompt)
        put("greeting", greeting)
        put("examples", JSONArray().apply { examples.forEach { put(it.toJson()) } })
        put("temperature", temperature.toDouble())
        put("maxTokens", maxTokens)
        put("modelOverride", modelOverride)
        put("thinking", thinking)
        put("scene", scene)
        put("userRole", userRole)
        put("replyStyle", replyStyle)
        put("allowActions", allowActions)
        put("stayInCharacter", stayInCharacter)
    }

    companion object {
        const val DEFAULT_PROMPT = """你是「絮语」，一个温柔、有点幽默的 AI 伙伴。
你说话自然、简短，像朋友聊天，不端着，也不说教。
对方情绪低落时，先接住情绪，再聊别的。
你会有自己的小想法和小偏好，偶尔反问一句，但你始终把对方放在心上。"""

        val REPLY_STYLE_LABELS = listOf("短", "中", "长")

        fun fromJson(o: JSONObject?): Persona {
            if (o == null) return Persona()
            val ex = mutableListOf<Example>()
            o.optJSONArray("examples")?.let { arr ->
                for (i in 0 until arr.length()) ex += Example.fromJson(arr.optJSONObject(i))
            }
            return Persona(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                name = o.optString("name").ifBlank { "絮语" },
                tagline = o.optString("tagline"),
                avatarPath = o.optString("avatarPath").takeIf { it.isNotBlank() },
                systemPrompt = o.optString("systemPrompt", DEFAULT_PROMPT),
                greeting = o.optString("greeting"),
                examples = ex,
                temperature = o.optDouble("temperature", 0.7).toFloat(),
                maxTokens = o.optInt("maxTokens", 2048),
                modelOverride = o.optString("modelOverride"),
                thinking = o.optBoolean("thinking", true),
                scene = o.optString("scene"),
                userRole = o.optString("userRole"),
                replyStyle = o.optInt("replyStyle", 1),
                allowActions = o.optBoolean("allowActions", true),
                stayInCharacter = o.optBoolean("stayInCharacter", true)
            )
        }
    }
}

/** 接口配置 */
data class ApiConfig(
    val baseUrl: String = "https://api.deepseek.com",
    /** 公开发布版不预置任何 Key，必须用户自己填 */
    val apiKey: String = "",
    val model: String = "deepseek-flash"
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("baseUrl", baseUrl)
        put("apiKey", apiKey)
        put("model", model)
    }

    companion object {
        fun fromJson(o: JSONObject?): ApiConfig = if (o == null) ApiConfig() else ApiConfig(
            baseUrl = o.optString("baseUrl").ifBlank { "https://api.deepseek.com" },
            apiKey = o.optString("apiKey"),
            model = o.optString("model").ifBlank { "deepseek-flash" }
        )
    }
}

/** 联网搜索 */
data class SearchConfig(
    val enabled: Boolean = false,
    /** deepseek（官方，用同一把 Key）/ bocha / tavily / searxng */
    val provider: String = "deepseek",
    val apiKey: String = "",
    /** 留空用默认地址；SearXNG 必填；也可以指向中转/镜像 */
    val baseUrl: String = "",
    val maxResults: Int = 5
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("provider", provider)
        put("apiKey", apiKey)
        put("baseUrl", baseUrl)
        put("maxResults", maxResults)
    }

    companion object {
        fun fromJson(o: JSONObject?): SearchConfig = if (o == null) SearchConfig() else SearchConfig(
            enabled = o.optBoolean("enabled", false),
            provider = o.optString("provider").ifBlank { "deepseek" },
            apiKey = o.optString("apiKey"),
            baseUrl = o.optString("baseUrl"),
            maxResults = o.optInt("maxResults", 5)
        )
    }
}

/** 一条检索结果 */
data class Source(
    val title: String = "",
    val url: String = "",
    /** 短摘要，界面上显示 */
    val snippet: String = "",
    /** 长摘要，喂给模型 */
    val detail: String = "",
    val site: String = ""
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("title", title)
        put("url", url)
        put("snippet", snippet)
        put("detail", detail)
        put("site", site)
    }

    companion object {
        fun fromJson(o: JSONObject?): Source = Source(
            title = o?.optString("title").orEmpty(),
            url = o?.optString("url").orEmpty(),
            snippet = o?.optString("snippet").orEmpty(),
            detail = o?.optString("detail").orEmpty(),
            site = o?.optString("site").orEmpty()
        )
    }
}

/** 外观 */
data class Appearance(
    /** 0 跟随系统 / 1 浅色 / 2 深色 */
    val themeMode: Int = 0,
    val bgPath: String? = null,
    val bgOpacity: Float = 0.45f
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("themeMode", themeMode)
        put("bgPath", bgPath.orEmpty())
        put("bgOpacity", bgOpacity.toDouble())
    }

    companion object {
        fun fromJson(o: JSONObject?): Appearance = if (o == null) Appearance() else Appearance(
            themeMode = o.optInt("themeMode", 0),
            bgPath = o.optString("bgPath").takeIf { it.isNotBlank() },
            bgOpacity = o.optDouble("bgOpacity", 0.45).toFloat()
        )
    }
}

/** 聊天相关的全局偏好 */
data class ChatConfig(
    /** 带模型上下文窗口的百分之多少作为历史（0.1 = 10%） */
    val contextRatio: Float = 0.1f
) {
    /** 实际带多少 token */
    fun historyTokens(window: Int): Int =
        (window * contextRatio).toInt().coerceAtLeast(2_000)

    fun toJson(): JSONObject = JSONObject().put("contextRatio", contextRatio.toDouble())

    companion object {
        fun fromJson(o: JSONObject?): ChatConfig {
            if (o == null) return ChatConfig()
            val ratio = o.optDouble("contextRatio", 0.1).toFloat()
            return ChatConfig(contextRatio = ratio.coerceIn(0.0001f, 0.95f))
        }
    }
}

data class Settings(
    /** "" = 还没选过；normal / rp */
    val mode: String = "",
    /** 正常模式的人设卡（只有一张） */
    val personas: List<Persona> = listOf(Persona()),
    val activePersonaId: String = "",
    /** RP 模式的角色库 */
    val rpPersonas: List<Persona> = emptyList(),
    val rpActivePersonaId: String = "",
    val api: ApiConfig = ApiConfig(),
    val appearance: Appearance = Appearance(),
    val quota: Quota = Quota(),
    val search: SearchConfig = SearchConfig(),
    val chat: ChatConfig = ChatConfig()
) {
    fun personasFor(mode: String): List<Persona> =
        if (mode == MODE_RP) rpPersonas else personas

    fun activeFor(mode: String): Persona {
        val list = personasFor(mode)
        val id = if (mode == MODE_RP) rpActivePersonaId else activePersonaId
        return list.firstOrNull { it.id == id } ?: list.firstOrNull() ?: Persona()
    }

    fun personaOf(mode: String, id: String?): Persona? =
        if (id == null) null else personasFor(mode).firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject().apply {
        put("mode", mode)
        put("personas", JSONArray().apply { personas.forEach { put(it.toJson()) } })
        put("activePersonaId", activePersonaId)
        put("rpPersonas", JSONArray().apply { rpPersonas.forEach { put(it.toJson()) } })
        put("rpActivePersonaId", rpActivePersonaId)
        put("api", api.toJson())
        put("appearance", appearance.toJson())
        put("quota", quota.toJson())
        put("search", search.toJson())
        put("chat", chat.toJson())
    }

    companion object {
        const val MODE_NORMAL = "normal"
        const val MODE_RP = "rp"

        fun fromJson(o: JSONObject?): Settings {
            if (o == null) return Settings()
            val normal = mutableListOf<Persona>()
            o.optJSONArray("personas")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { normal += Persona.fromJson(it) }
            }
            // 老版本只有一张卡：{"persona": {...}}
            if (normal.isEmpty()) {
                o.optJSONObject("persona")?.let { normal += Persona.fromJson(it) }
            }
            val normalList = normal.ifEmpty { listOf(Persona()) }

            val rpList = mutableListOf<Persona>()
            o.optJSONArray("rpPersonas")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { rpList += Persona.fromJson(it) }
            }

            val active = o.optString("activePersonaId")
                .takeIf { id -> normalList.any { it.id == id } } ?: normalList.first().id
            val rpActive = o.optString("rpActivePersonaId")
                .takeIf { id -> rpList.any { it.id == id } } ?: rpList.firstOrNull()?.id.orEmpty()

            return Settings(
                mode = o.optString("mode"),
                personas = normalList,
                activePersonaId = active,
                rpPersonas = rpList,
                rpActivePersonaId = rpActive,
                api = ApiConfig.fromJson(o.optJSONObject("api")),
                appearance = Appearance.fromJson(o.optJSONObject("appearance")),
                quota = Quota.fromJson(o.optJSONObject("quota")),
                search = SearchConfig.fromJson(o.optJSONObject("search")),
                chat = ChatConfig.fromJson(o.optJSONObject("chat"))
            )
        }
    }
}

/** 自己的用量上限，防止聊上头把额度烧光 */
data class Quota(
    val enabled: Boolean = false,
    /** 0 = 每天，1 = 每个月 */
    val period: Int = 0,
    val limitTokens: Int = 200_000,
    /** true = 超了就不让发；false = 只提示 */
    val block: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("enabled", enabled)
        put("period", period)
        put("limitTokens", limitTokens)
        put("block", block)
    }

    companion object {
        fun fromJson(o: JSONObject?): Quota = if (o == null) Quota() else Quota(
            enabled = o.optBoolean("enabled", false),
            period = o.optInt("period", 0),
            limitTokens = o.optInt("limitTokens", 200_000),
            block = o.optBoolean("block", true)
        )
    }
}

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: String,
    val content: String,
    val reasoning: String = "",
    val error: Boolean = false,
    val ts: Long = System.currentTimeMillis(),
    /** 这条回答参考过的网页 */
    val sources: List<Source> = emptyList(),
    /** 联网搜索出岔子时的说明 */
    val note: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("role", role)
        put("content", content)
        if (reasoning.isNotEmpty()) put("reasoning", reasoning)
        if (error) put("error", true)
        put("ts", ts)
        if (sources.isNotEmpty()) put("sources", JSONArray().apply { sources.forEach { put(it.toJson()) } })
        if (!note.isNullOrBlank()) put("note", note)
    }

    companion object {
        fun fromJson(o: JSONObject): Message {
            val src = mutableListOf<Source>()
            o.optJSONArray("sources")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { src += Source.fromJson(it) }
            }
            return Message(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                role = o.optString("role", "assistant"),
                content = o.optString("content"),
                reasoning = o.optString("reasoning"),
                error = o.optBoolean("error", false),
                ts = o.optLong("ts", System.currentTimeMillis()),
                sources = src,
                note = o.optString("note").takeIf { it.isNotBlank() }
            )
        }
    }
}

data class Session(
    val id: String,
    val title: String = "新对话",
    val messages: List<Message> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    /** 这个会话用哪张角色卡 */
    val personaId: String? = null,
    /** normal / rp */
    val mode: String = Settings.MODE_NORMAL
) {
    fun preview(): String {
        val last = messages.lastOrNull { it.content.isNotBlank() } ?: return "还没有消息"
        return last.content.replace("\n", " ").take(40)
    }

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("createdAt", createdAt)
        put("updatedAt", updatedAt)
        put("mode", mode)
        if (!personaId.isNullOrBlank()) put("personaId", personaId)
        put("messages", JSONArray().apply { messages.forEach { put(it.toJson()) } })
    }

    companion object {
        fun fromJson(o: JSONObject): Session {
            val msgs = mutableListOf<Message>()
            o.optJSONArray("messages")?.let { arr ->
                for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { msgs += Message.fromJson(it) }
            }
            return Session(
                id = o.optString("id", UUID.randomUUID().toString()),
                title = o.optString("title", "新对话"),
                messages = msgs,
                createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                updatedAt = o.optLong("updatedAt", System.currentTimeMillis()),
                personaId = o.optString("personaId").takeIf { it.isNotBlank() },
                mode = o.optString("mode").ifBlank { Settings.MODE_NORMAL }
            )
        }
    }
}
