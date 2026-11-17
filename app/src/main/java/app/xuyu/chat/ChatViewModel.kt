package app.xuyu.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.CardCodec
import app.xuyu.chat.data.Message
import app.xuyu.chat.data.ModelInfo
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Prompt
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Session
import app.xuyu.chat.data.Settings
import app.xuyu.chat.data.Source
import app.xuyu.chat.data.Store
import app.xuyu.chat.net.Balance
import app.xuyu.chat.net.ChatClient
import app.xuyu.chat.net.SearchClient
import app.xuyu.chat.net.WireMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class ChatViewModel(app: Application) : AndroidViewModel(app) {

    val store = Store(app)
    private val client = ChatClient()
    private val searchClient = SearchClient()

    data class Stream(
        val messageId: String,
        val sessionId: String,
        val text: String = "",
        val reasoning: String = "",
        val model: String = "",
        val searching: Boolean = false,
        /** 追加到这条已有消息（继续写） */
        val appendTo: String? = null,
        /** 顶部的小提示，比如「正在换个开场白…」 */
        val hint: String? = null
    )

    private val _stream = MutableStateFlow<Stream?>(null)
    val stream: StateFlow<Stream?> = _stream.asStateFlow()

    val pendingImport = MutableStateFlow<String?>(null)

    private var job: Job? = null

    @Volatile
    private var stopping = false

    @Volatile
    private var generation = 0

    val busy: Boolean
        get() = job?.isActive == true

    val mode: String
        get() = store.settings.value.mode.ifBlank { Settings.MODE_NORMAL }

    // ---------------- 会话 ----------------

    fun sessionsFor(mode: String): List<Session> =
        store.sessions.value.filter { it.mode == mode }.sortedByDescending { it.updatedAt }

    fun activeSession(mode: String): Session? {
        val list = sessionsFor(mode)
        return list.firstOrNull { it.id == store.activeId.value } ?: list.firstOrNull()
    }

    /** 当前模式的会话（测试和部分 UI 用） */
    fun activeSession(): Session? = activeSession(mode)

    /** 进/切模式：保证这个模式下至少有一个会话，并把它设成当前 */
    fun openMode(mode: String) {
        val list = sessionsFor(mode)
        if (list.isEmpty()) {
            val s = store.settings.value
            val p = s.activeFor(mode)
            store.createSession(mode, p.id, p.greeting)
        } else if (list.none { it.id == store.activeId.value }) {
            store.setActive(list.first().id)
        }
    }

    fun newChat() {
        stop()
        val m = mode
        val p = personaFor(null, m)
        store.createSession(m, p.id, p.greeting)
    }

    fun selectSession(id: String) {
        if (id == store.activeId.value) return
        stop()
        store.setActive(id)
    }

    fun deleteSession(id: String) {
        if (store.activeId.value == id) stop()
        store.deleteSession(id)
        val m = mode
        if (sessionsFor(m).isEmpty()) {
            val p = personaFor(null, m)
            store.createSession(m, p.id, p.greeting)
        }
    }

    fun clearCurrent() {
        stop()
        store.activeId.value?.let { store.clearSession(it) }
    }

    fun deleteMessage(messageId: String) {
        store.activeId.value?.let { store.deleteMessage(it, messageId) }
    }

    fun editMessage(messageId: String, content: String) {
        store.activeId.value?.let { store.updateMessageContent(it, messageId, content) }
    }

    fun applySettings(s: Settings) = store.saveSettings(s)

    /** 会话对应的角色；没有就退回当前模式的主角色 */
    fun personaFor(session: Session?, mode: String = this.mode): Persona {
        val s = store.settings.value
        val list = s.personasFor(mode)
        return session?.personaId?.let { id -> list.firstOrNull { it.id == id } }
            ?: s.activeFor(mode)
    }

    // ---------------- 发送 ----------------

    fun send(text: String) {
        val t = text.trim()
        if (t.isEmpty() || busy) return
        val m = mode
        val session = activeSession(m)
            ?: store.createSession(m, personaFor(null, m).id, personaFor(null, m).greeting)
        val s = store.settings.value
        store.appendMessage(session.id, Message(role = "user", content = t))
        if (s.api.apiKey.isBlank()) {
            store.appendMessage(
                session.id,
                Message(
                    role = "assistant",
                    error = true,
                    content = "还没有填 API Key。\n打开右上角的「设置 → 接口」，填上你自己的 Key 就能开始聊了。"
                )
            )
            return
        }
        if (quotaBlocked(s)) {
            store.appendMessage(
                session.id,
                Message(role = "assistant", error = true, content = quotaNotice(s))
            )
            return
        }
        startStream(session.id)
    }

    /** 继续：接着最后一条回答往下写，结果拼进同一条消息 */
    fun continueLast() {
        if (busy) return
        val m = mode
        val sid = activeSession(m)?.id ?: return
        val session = store.session(sid) ?: return
        val last = session.messages.lastOrNull() ?: return
        if (last.role != "assistant" || last.error) return
        startStream(sid, appendTo = last.id, tailUser = "（继续，接着你上面那段往下写，不要重复已经说过的内容，也不要重新开场。）")
    }

    /** 重写：把最后一条回答删掉，换一种写法重新生成 */
    fun rewriteLast() {
        if (busy) return
        val m = mode
        val sid = activeSession(m)?.id ?: return
        val session = store.session(sid) ?: return
        val index = session.messages.indexOfLast { it.role == "assistant" }
        if (index < 0 || session.messages.take(index).none { it.role == "user" }) return
        store.truncateFrom(sid, index)
        startStream(sid, extraSystem = "这次换一种写法，不要和上一次雷同。")
    }

    /** 换一个开场白（只在还没有用户发言时可用） */
    fun rerollGreeting() {
        if (busy) return
        val m = mode
        val sid = activeSession(m)?.id ?: return
        val session = store.session(sid) ?: return
        if (session.messages.any { it.role == "user" }) return
        val persona = personaFor(session, m)
        store.replaceMessages(sid, emptyList())
        startStream(
            sid,
            greeting = true,
            extraSystem = "现在只写一句「${persona.name}」的开场白，不超过 60 字。直接输出这句话本身，不要加引号、不要解释、不要动作描写。"
        )
    }

    fun regenerate() {
        if (busy) return
        val m = mode
        val sid = activeSession(m)?.id ?: return
        val session = store.session(sid) ?: return
        val lastAssistant = session.messages.indexOfLast { it.role == "assistant" }
        if (lastAssistant < 0) return
        if (session.messages.take(lastAssistant).none { it.role == "user" }) return
        if (session.messages.drop(lastAssistant + 1).any { it.role == "user" }) return
        store.truncateFrom(sid, lastAssistant)
        startStream(sid)
    }

    fun stop() {
        stopping = true
        generation++
        client.cancel()
    }

    /** 限额是否已经卡住发送 */
    fun quotaBlocked(s: Settings = store.settings.value): Boolean {
        val q = s.quota
        return q.enabled && q.block && q.limitTokens > 0 && store.usedTokens(q) >= q.limitTokens
    }

    fun quotaWarnOnly(s: Settings = store.settings.value): Boolean {
        val q = s.quota
        return q.enabled && !q.block && q.limitTokens > 0 && store.usedTokens(q) >= q.limitTokens
    }

    fun quotaNotice(s: Settings = store.settings.value): String {
        val q = s.quota
        val used = store.usedTokens(q)
        val period = if (q.period == 0) "今天" else "这个月"
        return "已到你自己设的额度：${period}用了 $used token，上限 ${q.limitTokens}。\n" +
            "去「设置 → 用量与限额」调高上限，或者切到另一个周期。"
    }

    private fun startStream(
        sessionId: String,
        appendTo: String? = null,
        tailUser: String? = null,
        extraSystem: String? = null,
        greeting: Boolean = false
    ) {
        val s = store.settings.value
        val session = store.session(sessionId) ?: return
        val m = session.mode
        val persona = personaFor(session, m)
        val model = persona.modelOverride.ifBlank { s.api.model }
        val assistantId = UUID.randomUUID().toString()
        val needSearch = s.search.enabled && !greeting && appendTo == null
        stopping = false
        val myGen = ++generation
        _stream.value = Stream(
            messageId = assistantId,
            sessionId = sessionId,
            model = model,
            searching = needSearch,
            appendTo = appendTo,
            hint = if (greeting) "正在换个开场白…" else null
        )

        job = viewModelScope.launch(Dispatchers.IO) {
            if (myGen != generation) {
                if (_stream.value?.messageId == assistantId) _stream.value = null
                return@launch
            }

            var sources: List<Source> = emptyList()
            var note: String? = null
            if (needSearch) {
                try {
                    val q = session.messages.lastOrNull { it.role == "user" }?.content.orEmpty()
                    sources = searchClient.search(s.search, q, s.api)
                    if (sources.isEmpty()) note = "没搜到相关网页，这次按普通回答。"
                } catch (e: Exception) {
                    note = "联网搜索没成功：${e.message}"
                }
                if (_stream.value?.messageId == assistantId) {
                    _stream.value = _stream.value?.copy(searching = false)
                }
            }

            val history = buildWire(
                persona = persona,
                mode = m,
                messages = session.messages,
                sources = sources,
                tailUser = tailUser,
                extraSystem = extraSystem
            )
            val acc = StringBuilder()
            val rsn = StringBuilder()
            var failure: String? = null
            var lastEmit = 0L
            try {
                client.stream(
                    baseUrl = s.api.baseUrl,
                    apiKey = s.api.apiKey,
                    model = model,
                    messages = history,
                    temperature = persona.temperature,
                    maxTokens = persona.maxTokens,
                    thinking = persona.thinking,
                    onUsage = { prompt, completion -> store.addUsage(prompt, completion) }
                ) { content, reasoning ->
                    if (content.isNotEmpty()) acc.append(content)
                    if (reasoning.isNotEmpty()) rsn.append(reasoning)
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= 70L) {
                        lastEmit = now
                        _stream.value = Stream(
                            messageId = assistantId,
                            sessionId = sessionId,
                            text = acc.toString(),
                            reasoning = rsn.toString(),
                            model = model,
                            appendTo = appendTo,
                            hint = if (greeting) "正在换个开场白…" else null
                        )
                    }
                }
            } catch (e: CancellationException) {
                // 用户停止
            } catch (e: Exception) {
                if (!stopping) failure = e.message ?: "网络异常"
            }

            val text = acc.toString().trim()
            val reason = rsn.toString().trim()

            when {
                text.isEmpty() && failure == null -> {
                    // 被停止且一个字都没吐出来：什么也不留
                }
                text.isEmpty() -> {
                    store.appendMessage(
                        sessionId,
                        Message(role = "assistant", content = failure ?: "请求失败", error = true, sources = sources, note = note)
                    )
                }
                appendTo != null -> {
                    store.appendToMessage(sessionId, appendTo, text)
                }
                failure == null -> {
                    store.appendMessage(
                        sessionId,
                        Message(role = "assistant", content = text, reasoning = reason, sources = sources, note = note)
                    )
                }
                else -> {
                    store.appendMessage(
                        sessionId,
                        Message(
                            role = "assistant",
                            content = text + "\n\n—— 中断：" + failure,
                            reasoning = reason,
                            sources = sources,
                            note = note
                        )
                    )
                }
            }
            _stream.value = null
        }
    }

    private fun buildWire(
        persona: Persona,
        mode: String,
        messages: List<Message>,
        sources: List<Source> = emptyList(),
        tailUser: String? = null,
        extraSystem: String? = null
    ): List<WireMessage> {
        val sb = StringBuilder()
        sb.append(Prompt.system(persona, mode))
        sb.append(Prompt.searchContext(sources))
        if (!extraSystem.isNullOrBlank()) sb.append("\n\n").append(extraSystem)
        val out = mutableListOf(WireMessage("system", sb.toString()))

        // 按模型的上下文窗口 × 比例，从最近的消息往回装
        val window = ModelInfo.contextWindow(persona.modelOverride.ifBlank { store.settings.value.api.model })
        val budget = store.settings.value.chat.historyTokens(window)
        var used = 0
        val picked = ArrayDeque<Message>()
        for (message in messages.asReversed()) {
            if (message.error || message.content.isBlank()) continue
            if (message.role != "user" && message.role != "assistant") continue
            val cost = estimateTokens(message.content)
            if (picked.isNotEmpty() && used + cost > budget) break
            used += cost
            picked.addFirst(message)
        }
        picked.forEach { out += WireMessage(it.role, it.content) }
        if (tailUser != null) out += WireMessage("user", tailUser)
        return out
    }

    /** 粗略估 token：中文约 0.6 token/字，英文更低；够用就行 */
    private fun estimateTokens(text: String): Int =
        (text.length * 0.6).toInt().coerceAtLeast(1) + 8

    // ---------------- 导入 / 测试 / 导出 ----------------

    suspend fun importCard(text: String): Result<Settings> = withContext(Dispatchers.IO) {
        runCatching {
            val imported = CardCodec.decode(getApplication(), text)
            val s = store.settings.value
            val m = s.mode.ifBlank { Settings.MODE_NORMAL }
            val p = imported.persona.copy(id = UUID.randomUUID().toString())
            val next = if (m == Settings.MODE_RP) {
                s.copy(
                    rpPersonas = s.rpPersonas + p,
                    rpActivePersonaId = p.id,
                    api = imported.api,
                    appearance = imported.appearance
                )
            } else {
                s.copy(
                    personas = listOf(p),
                    activePersonaId = p.id,
                    api = imported.api,
                    appearance = imported.appearance
                )
            }
            store.saveSettings(next)
            next
        }
    }

    suspend fun testApi(baseUrl: String, apiKey: String, model: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching { client.test(baseUrl, apiKey, model) }
        }

    suspend fun queryBalance(baseUrl: String, apiKey: String): Result<Balance> =
        withContext(Dispatchers.IO) {
            runCatching { client.balance(baseUrl, apiKey) }
        }

    suspend fun testSearch(cfg: SearchConfig, chat: ApiConfig): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val r = searchClient.search(cfg, "DeepSeek 最新进展", chat)
            if (r.isEmpty()) "连上了，但没返回结果" else "搜到 ${r.size} 条 · 第一条：${r[0].title.take(28)}"
        }
    }

    /** 导出成 Markdown */
    fun exportMarkdown(session: Session, persona: Persona): String {
        val sb = StringBuilder()
        sb.append("# 与「").append(persona.name).append("」的对话\n\n")
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
        sb.append("导出于 ").append(fmt.format(Date())).append("\n\n---\n\n")
        session.messages.forEach { m ->
            if (m.content.isBlank()) return@forEach
            if (m.role == "user") {
                sb.append("**我**：").append(m.content).append("\n\n")
            } else {
                sb.append("**").append(persona.name).append("**：").append(m.content).append("\n\n")
            }
            if (m.sources.isNotEmpty()) {
                sb.append("> 参考：")
                sb.append(m.sources.joinToString("；") { s -> s.title + " " + s.url })
                sb.append("\n\n")
            }
        }
        return sb.toString()
    }

    override fun onCleared() {
        client.cancel()
        super.onCleared()
    }
}
