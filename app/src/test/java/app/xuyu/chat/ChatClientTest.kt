package app.xuyu.chat

import app.xuyu.chat.net.ApiException
import app.xuyu.chat.net.ChatClient
import app.xuyu.chat.net.WireMessage
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ChatClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun parsesSseStream() = runBlocking {
        val body = """
            data: {"choices":[{"delta":{"content":"你"}}]}

            data: {"choices":[{"delta":{"content":"好"}}]}

            data: {"choices":[{"delta":{"reasoning_content":"先想一想"}}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        )

        val content = StringBuilder()
        val reasoning = StringBuilder()
        ChatClient().stream(
            baseUrl = server.url("/").toString(),
            apiKey = "sk-abc",
            model = "deepseek-chat",
            messages = listOf(WireMessage("system", "你是猫"), WireMessage("user", "在吗")),
            temperature = 0.7f,
            maxTokens = 256
        ) { c, r ->
            content.append(c)
            reasoning.append(r)
        }

        assertEquals("你好", content.toString())
        assertEquals("先想一想", reasoning.toString())

        val req = server.takeRequest()
        assertEquals("/chat/completions", req.path)
        assertEquals("Bearer sk-abc", req.getHeader("Authorization"))
        val sent = req.body.readUtf8()
        assertTrue(sent.contains("\"stream\":true"))
        assertTrue(sent.contains("你是猫"))
        assertTrue(sent.contains("\"max_tokens\":256"))
    }

    @Test
    fun surfacesApiError() = runBlocking {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{\"error\":{\"message\":\"Authentication Fails\"}}")
        )
        try {
            ChatClient().stream(
                baseUrl = server.url("/").toString(),
                apiKey = "bad",
                model = "deepseek-chat",
                messages = listOf(WireMessage("user", "hi")),
                temperature = 0.5f,
                maxTokens = 16
            ) { _, _ -> }
            fail("应该抛错")
        } catch (e: ApiException) {
            assertEquals("Authentication Fails", e.message)
        }
    }

    /** 回归：DeepSeek 的 chunk 里 content / reasoning_content 常常是 null，不能变成字面量 "null" */
    @Test
    fun nullFieldsNeverBecomeText() = runBlocking {
        val body = """
            data: {"choices":[{"delta":{"role":"assistant","content":null,"reasoning_content":"先想一想"}}]}

            data: {"choices":[{"delta":{"content":"答","reasoning_content":null}}]}

            data: {"choices":[{"delta":{"content":"案","reasoning_content":null}}]}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        )
        val content = StringBuilder()
        val reasoning = StringBuilder()
        ChatClient().stream(
            baseUrl = server.url("/").toString(),
            apiKey = "sk-abc",
            model = "deepseek-flash",
            messages = listOf(WireMessage("user", "hi")),
            temperature = 0.5f,
            maxTokens = 64
        ) { c, r ->
            content.append(c)
            reasoning.append(r)
        }
        assertEquals("答案", content.toString())
        assertEquals("先想一想", reasoning.toString())
    }

    @Test
    fun parsesBalance() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"is_available":true,"balance_infos":[{"currency":"CNY","total_balance":"6.05","granted_balance":"0.00","topped_up_balance":"6.05"}]}"""
            )
        )
        val b = ChatClient().balance(server.url("/").toString(), "sk-x")
        assertEquals("CNY", b.currency)
        assertEquals("6.05", b.total)
        assertTrue(b.available)
        assertEquals("¥6.05（充值 6.05）", b.pretty())
        assertEquals("/user/balance", server.takeRequest().path)
    }

    @Test
    fun reportsUsageFromFinalChunk() = runBlocking {
        val body = """
            data: {"choices":[{"delta":{"content":"好"}}]}

            data: {"choices":[{"delta":{},"finish_reason":"stop"}],"usage":{"prompt_tokens":12,"completion_tokens":7,"total_tokens":19}}

            data: [DONE]

        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/event-stream")
                .setBody(body)
        )
        var prompt = 0
        var completion = 0
        ChatClient().stream(
            baseUrl = server.url("/").toString(),
            apiKey = "sk-abc",
            model = "deepseek-chat",
            messages = listOf(WireMessage("user", "hi")),
            temperature = 0.5f,
            maxTokens = 16,
            onUsage = { p, c ->
                prompt = p
                completion = c
            }
        ) { _, _ -> }
        assertEquals(12, prompt)
        assertEquals(7, completion)
        assertTrue(server.takeRequest().body.readUtf8().contains("include_usage"))
    }

    @Test
    fun buildsEndpointForCommonBaseUrls() {
        val c = ChatClient()
        assertEquals("https://api.deepseek.com/chat/completions", c.endpoint("https://api.deepseek.com"))
        assertEquals("https://api.deepseek.com/chat/completions", c.endpoint("https://api.deepseek.com/"))
        assertEquals("https://api.openai.com/v1/chat/completions", c.endpoint("https://api.openai.com/v1"))
        assertEquals("http://10.0.2.2:11434/v1/chat/completions", c.endpoint("http://10.0.2.2:11434/v1"))
        assertEquals("https://x.com/v1/chat/completions", c.endpoint("x.com/v1"))
        assertEquals(
            "https://x.com/chat/completions",
            c.endpoint("https://x.com/chat/completions")
        )
    }
}
