package app.xuyu.chat

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.ChatConfig
import app.xuyu.chat.data.Message
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Quota
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Settings
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 端到端：send() -> SSE -> 落盘 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatViewModelTest {

    private lateinit var server: MockWebServer
    private val app: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun sseBody(chunks: List<String>): String {
        val sb = StringBuilder()
        chunks.forEach { c ->
            sb.append("data: {\"choices\":[{\"delta\":{\"content\":\"").append(c).append("\"}}]}\n\n")
        }
        sb.append("data: [DONE]\n\n")
        return sb.toString()
    }

    @Test
    fun streamsAndCommitsAssistantMessage() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody(listOf("喵", "，", "我在", "呀")))
        )

        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子", systemPrompt = "你是猫"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "sk-test", model = "deepseek-chat")
            )
        )
        vm.newChat()
        vm.send("你在吗")

        withTimeout(15_000) {
            while (vm.store.activeSession()?.messages?.any { it.role == "assistant" && it.content == "喵，我在呀" } != true) {
                delay(50)
            }
        }

        val session = vm.store.activeSession()!!
        val last = session.messages.last()
        assertEquals("assistant", last.role)
        assertEquals("喵，我在呀", last.content)
        assertEquals("你在吗", session.messages.first { it.role == "user" }.content)

        val sent = server.takeRequest().body.readUtf8()
        assertTrue("system prompt 应该带上人设", sent.contains("小柚子"))
        assertTrue("应该带上用户这句", sent.contains("你在吗"))

        Thread.sleep(400)
        val reopened = app.filesDir.resolve("xuyu/sessions.json").readText()
        assertTrue("消息应该写进本地文件", reopened.contains("喵，我在呀"))
    }

    @Test
    fun longHistoryIsTrimmedByTokenBudget() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody(listOf("好的")))
        )

        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "sk-test", model = "deepseek-flash"),
                // deepseek-flash 窗口 1M，取 0.02% ≈ 200 token，方便验证裁剪
                chat = ChatConfig(contextRatio = 0.0002f)
            )
        )
        vm.newChat()
        val sid = vm.store.activeSession()!!.id
        // 很久以前的一句长话，预算塞不下
        vm.store.appendMessage(sid, Message(role = "user", content = "OLD".repeat(3000)))
        vm.store.appendMessage(sid, Message(role = "assistant", content = "嗯"))
        vm.send("最近好吗")

        withTimeout(15_000) {
            while (vm.store.activeSession()?.messages?.none { it.role == "assistant" && it.content == "好的" } != false) {
                delay(50)
            }
        }

        val body = server.takeRequest().body.readUtf8()
        assertTrue("最近一句应该在", body.contains("最近好吗"))
        assertTrue("超预算的老消息应该被丢掉", !body.contains("OLDOLDOLD"))
    }

    @Test
    fun quotaBlocksSending() = runBlocking {
        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "sk-test", model = "deepseek-chat"),
                quota = Quota(enabled = true, period = 0, limitTokens = 100, block = true)
            )
        )
        vm.store.addUsage(80, 40)
        vm.newChat()
        vm.send("在吗")

        val last = vm.store.activeSession()!!.messages.last()
        assertTrue(last.error)
        assertTrue(last.content.contains("额度"))
        assertEquals("不应该真的发出请求", 0, server.requestCount)
    }

    @Test
    fun searchResultsAreInjectedAndStored() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":200,"data":{"webPages":{"value":[{"name":"一篇资料","url":"https://ex.com/1","snippet":"资料摘要"}]}}}"""
            )
        )
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody(listOf("根据资料", "，答案是 1")))
        )

        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "sk-test", model = "deepseek-chat"),
                search = SearchConfig(
                    enabled = true,
                    provider = "bocha",
                    apiKey = "sk-search",
                    baseUrl = server.url("/v1/web-search").toString()
                )
            )
        )
        vm.newChat()
        vm.send("最近有什么新闻")

        withTimeout(15_000) {
            while (vm.store.activeSession()?.messages?.none { it.role == "assistant" && it.content.contains("答案是 1") } != false) {
                delay(50)
            }
        }

        val last = vm.store.activeSession()!!.messages.last()
        assertEquals(1, last.sources.size)
        assertEquals("一篇资料", last.sources[0].title)

        val searchReq = server.takeRequest()
        assertEquals("/v1/web-search", searchReq.path)

        val chatBody = server.takeRequest().body.readUtf8()
        assertTrue("system prompt 里应该带上检索资料", chatBody.contains("【联网资料】"))
        assertTrue(chatBody.contains("一篇资料"))
    }

    @Test
    fun searchFailureStillAnswers() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(500).setBody("""{"message":"boom"}"""))
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(sseBody(listOf("照样能聊")))
        )

        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "sk-test", model = "deepseek-chat"),
                search = SearchConfig(enabled = true, provider = "bocha", apiKey = "k", baseUrl = server.url("/s").toString())
            )
        )
        vm.newChat()
        vm.send("在吗")

        withTimeout(15_000) {
            while (vm.store.activeSession()?.messages?.none { it.role == "assistant" && it.content.contains("照样能聊") } != false) {
                delay(50)
            }
        }
        val last = vm.store.activeSession()!!.messages.last()
        assertTrue(last.sources.isEmpty())
        assertTrue("应该记下搜索失败的原因", last.note!!.contains("联网搜索没成功"))
    }

    @Test
    fun withoutApiKeyGivesFriendlyError() = runBlocking {
        val vm = ChatViewModel(app)
        vm.applySettings(
            settings(
                persona = Persona(name = "小柚子"),
                api = ApiConfig(baseUrl = server.url("/").toString(), apiKey = "", model = "deepseek-chat")
            )
        )
        vm.newChat()
        vm.send("在吗")
        val last = vm.store.activeSession()!!.messages.last()
        assertEquals("assistant", last.role)
        assertTrue(last.error)
        assertTrue(last.content.contains("API Key"))
    }
}
