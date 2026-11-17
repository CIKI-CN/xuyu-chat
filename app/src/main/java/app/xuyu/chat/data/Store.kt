package app.xuyu.chat.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** token 用量：按天/按月各自记一笔，累计不重置 */
data class UsageState(
    val dayKey: String = "",
    val dayTokens: Long = 0,
    val monthKey: String = "",
    val monthTokens: Long = 0,
    val totalTokens: Long = 0,
    val requests: Int = 0
)

/** 免责声明的同意记录：版本号或文案版本变了就重新弹 */
data class Consent(
    val versionCode: Int = 0,
    val disclaimerVersion: Int = 0,
    /** 上手指南只在第一次自动弹一次 */
    val guideSeen: Boolean = false
)

/**
 * 极简本地存储：settings.json + sessions.json，全部落在 filesDir。
 * 只有本地，没有任何上传。
 */
class Store(context: Context) {

    private val root = File(context.filesDir, "xuyu").apply { mkdirs() }
    private val settingsFile = File(root, "settings.json")
    private val sessionsFile = File(root, "sessions.json")
    private val usageFile = File(root, "usage.json")
    private val metaFile = File(root, "meta.json")
    val imagesDir: File = File(root, "images").apply { mkdirs() }

    private val io = Executors.newSingleThreadExecutor { r ->
        Thread(r, "xuyu-store").apply { isDaemon = true }
    }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val loaded = readSessions()
    private val _sessions = MutableStateFlow(loaded.first)
    val sessions: StateFlow<List<Session>> = _sessions.asStateFlow()

    private val _activeId = MutableStateFlow(loaded.second)
    val activeId: StateFlow<String?> = _activeId.asStateFlow()

    private val _usage = MutableStateFlow(readUsage())
    val usage: StateFlow<UsageState> = _usage.asStateFlow()

    private val _consent = MutableStateFlow(readConsent())
    val consent: StateFlow<Consent> = _consent.asStateFlow()

    fun acceptConsent(versionCode: Int, disclaimerVersion: Int) {
        updateConsent(
            _consent.value.copy(versionCode = versionCode, disclaimerVersion = disclaimerVersion)
        )
    }

    fun markGuideSeen() {
        if (_consent.value.guideSeen) return
        updateConsent(_consent.value.copy(guideSeen = true))
    }

    private fun updateConsent(c: Consent) {
        _consent.value = c
        val o = JSONObject()
            .put("versionCode", c.versionCode)
            .put("disclaimerVersion", c.disclaimerVersion)
            .put("guideSeen", c.guideSeen)
        writeAsync(metaFile, o.toString())
    }

    private fun readConsent(): Consent = try {
        if (!metaFile.exists()) Consent()
        else {
            val o = JSONObject(metaFile.readText())
            Consent(
                versionCode = o.optInt("versionCode", 0),
                disclaimerVersion = o.optInt("disclaimerVersion", 0),
                guideSeen = o.optBoolean("guideSeen", false)
            )
        }
    } catch (e: Exception) {
        Consent()
    }

    init {
        val list = _sessions.value
        if (list.isEmpty()) {
            val p = _settings.value.activeFor(Settings.MODE_NORMAL)
            createSession(Settings.MODE_NORMAL, p.id, p.greeting)
        } else if (_activeId.value == null || list.none { it.id == _activeId.value }) {
            _activeId.value = list.first().id
            persistActive()
        }
    }

    // ---------- 设置 ----------

    fun saveSettings(s: Settings) {
        _settings.value = s
        writeAsync(settingsFile, s.toJson().toString())
    }

    // ---------- 角色卡 ----------

    /** RP 模式第一次进来时，用正常模式那张卡复制一份当起点 */
    fun ensureRpPersona() {
        val s = _settings.value
        if (s.rpPersonas.isNotEmpty()) return
        val base = s.activeFor(Settings.MODE_NORMAL)
        val rp = base.copy(id = UUID.randomUUID().toString())
        saveSettings(s.copy(rpPersonas = listOf(rp), rpActivePersonaId = rp.id))
    }

    fun savePersona(mode: String, p: Persona) {
        val s = _settings.value
        if (mode == Settings.MODE_RP) {
            val list = if (s.rpPersonas.any { it.id == p.id }) {
                s.rpPersonas.map { if (it.id == p.id) p else it }
            } else {
                s.rpPersonas + p
            }
            saveSettings(s.copy(rpPersonas = list, rpActivePersonaId = p.id))
        } else {
            // 正常模式只有一张卡
            saveSettings(s.copy(personas = listOf(p), activePersonaId = p.id))
        }
    }

    fun deletePersona(mode: String, id: String) {
        val s = _settings.value
        if (mode == Settings.MODE_RP) {
            if (s.rpPersonas.size <= 1) return
            val list = s.rpPersonas.filterNot { it.id == id }
            val active = if (s.rpActivePersonaId == id) list.first().id else s.rpActivePersonaId
            saveSettings(s.copy(rpPersonas = list, rpActivePersonaId = active))
        }
    }

    fun setActivePersona(mode: String, id: String) {
        val s = _settings.value
        if (mode == Settings.MODE_RP) {
            if (s.rpPersonas.none { it.id == id }) return
            saveSettings(s.copy(rpActivePersonaId = id))
        } else {
            if (s.personas.none { it.id == id }) return
            saveSettings(s.copy(activePersonaId = id))
        }
    }

    // ---------- 会话 ----------

    fun activeSession(): Session? = _sessions.value.firstOrNull { it.id == _activeId.value }

    fun session(id: String): Session? = _sessions.value.firstOrNull { it.id == id }

    fun setActive(id: String) {
        if (_activeId.value == id) return
        _activeId.value = id
        persistActive()
    }

    fun createSession(mode: String, personaId: String?, greeting: String? = null): Session {
        val now = System.currentTimeMillis()
        val msgs = if (!greeting.isNullOrBlank()) {
            listOf(Message(role = "assistant", content = greeting, ts = now))
        } else emptyList()
        val s = Session(
            id = UUID.randomUUID().toString(),
            title = "新对话",
            messages = msgs,
            createdAt = now,
            updatedAt = now,
            personaId = personaId,
            mode = mode
        )
        _sessions.value = listOf(s) + _sessions.value
        _activeId.value = s.id
        persistSessions()
        persistActive()
        return s
    }

    fun deleteSession(id: String) {
        val list = _sessions.value
        if (list.size <= 1) {
            // 保留最后一个会话，只清空内容
            clearSession(id)
            return
        }
        _sessions.value = list.filterNot { it.id == id }
        if (_activeId.value == id) {
            _activeId.value = _sessions.value.firstOrNull()?.id
            persistActive()
        }
        persistSessions()
    }

    fun clearSession(id: String) {
        update(id) { it.copy(messages = emptyList(), updatedAt = System.currentTimeMillis()) }
    }

    private fun update(id: String, block: (Session) -> Session) {
        _sessions.value = _sessions.value.map { if (it.id == id) block(it) else it }
        persistSessions()
    }

    fun appendMessage(sessionId: String, msg: Message) {
        update(sessionId) { s ->
            var title = s.title
            if (title == "新对话" && msg.role == "user" && msg.content.isNotBlank()) {
                title = msg.content.replace("\n", " ").trim().take(16)
            }
            s.copy(
                title = title,
                messages = s.messages + msg,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    fun replaceMessages(sessionId: String, messages: List<Message>) {
        update(sessionId) { it.copy(messages = messages, updatedAt = System.currentTimeMillis()) }
    }

    fun deleteMessage(sessionId: String, messageId: String) {
        update(sessionId) { s ->
            s.copy(messages = s.messages.filterNot { it.id == messageId }, updatedAt = System.currentTimeMillis())
        }
    }

    /** 改一条消息的内容（编辑） */
    fun updateMessageContent(sessionId: String, messageId: String, content: String) {
        update(sessionId) { s ->
            s.copy(
                messages = s.messages.map { if (it.id == messageId) it.copy(content = content) else it },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /** 往一条消息末尾追加（继续生成） */
    fun appendToMessage(sessionId: String, messageId: String, extra: String) {
        if (extra.isEmpty()) return
        update(sessionId) { s ->
            s.copy(
                messages = s.messages.map {
                    if (it.id == messageId) it.copy(content = it.content + extra) else it
                },
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    /** 删除 index 及之后的全部消息（重新生成用） */
    fun truncateFrom(sessionId: String, index: Int) {
        update(sessionId) { s ->
            s.copy(
                messages = if (index <= 0) emptyList() else s.messages.take(index),
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    // ---------- 用量 ----------

    /** 当前周期已经用掉的 token（周期换了就是 0） */
    fun usedTokens(quota: Quota): Long {
        val u = _usage.value
        return if (quota.period == 0) {
            if (u.dayKey == dayKey(System.currentTimeMillis())) u.dayTokens else 0L
        } else {
            if (u.monthKey == monthKey(System.currentTimeMillis())) u.monthTokens else 0L
        }
    }

    fun addUsage(promptTokens: Int, completionTokens: Int) {
        val add = (promptTokens.coerceAtLeast(0) + completionTokens.coerceAtLeast(0)).toLong()
        if (add <= 0) return
        val now = System.currentTimeMillis()
        val day = dayKey(now)
        val month = monthKey(now)
        _usage.value = _usage.value.let { u ->
            UsageState(
                dayKey = day,
                dayTokens = (if (u.dayKey == day) u.dayTokens else 0L) + add,
                monthKey = month,
                monthTokens = (if (u.monthKey == month) u.monthTokens else 0L) + add,
                totalTokens = u.totalTokens + add,
                requests = u.requests + 1
            )
        }
        val u = _usage.value
        val o = JSONObject()
            .put("dayKey", u.dayKey)
            .put("dayTokens", u.dayTokens)
            .put("monthKey", u.monthKey)
            .put("monthTokens", u.monthTokens)
            .put("totalTokens", u.totalTokens)
            .put("requests", u.requests)
        writeAsync(usageFile, o.toString())
    }

    private fun readUsage(): UsageState = try {
        if (!usageFile.exists()) UsageState()
        else {
            val o = JSONObject(usageFile.readText())
            UsageState(
                dayKey = o.optString("dayKey"),
                dayTokens = o.optLong("dayTokens", 0),
                monthKey = o.optString("monthKey"),
                monthTokens = o.optLong("monthTokens", 0),
                totalTokens = o.optLong("totalTokens", 0),
                requests = o.optInt("requests", 0)
            )
        }
    } catch (e: Exception) {
        UsageState()
    }

    private fun dayKey(t: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    private fun monthKey(t: Long): String = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(t))

    // ---------- 读写 ----------

    private fun readSettings(): Settings = try {
        if (settingsFile.exists()) Settings.fromJson(JSONObject(settingsFile.readText())) else Settings()
    } catch (e: Exception) {
        Settings()
    }

    private fun readSessions(): Pair<List<Session>, String?> = try {
        if (!sessionsFile.exists()) emptyList<Session>() to null
        else {
            val o = JSONObject(sessionsFile.readText())
            val arr = o.optJSONArray("sessions") ?: JSONArray()
            val list = mutableListOf<Session>()
            for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { list += Session.fromJson(it) }
            list.sortedByDescending { it.updatedAt } to o.optString("active").takeIf { it.isNotBlank() }
        }
    } catch (e: Exception) {
        emptyList<Session>() to null
    }

    private fun persistSessions() {
        val arr = JSONArray()
        _sessions.value.forEach { arr.put(it.toJson()) }
        val root = JSONObject()
            .put("sessions", arr)
            .put("active", _activeId.value.orEmpty())
        writeAsync(sessionsFile, root.toString())
    }

    private fun persistActive() {
        // active 跟着会话文件一起存，简单起见整体重写
        persistSessions()
    }

    private fun writeAsync(file: File, content: String) {
        io.execute {
            try {
                val tmp = File(file.parentFile, file.name + ".tmp")
                tmp.writeText(content)
                if (!tmp.renameTo(file)) {
                    file.writeText(content)
                    tmp.delete()
                }
            } catch (_: Exception) {
            }
        }
    }
}
