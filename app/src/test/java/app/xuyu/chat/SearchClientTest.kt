package app.xuyu.chat

import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.net.ApiException
import app.xuyu.chat.net.SearchClient
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
class SearchClientTest {

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

    /** 官方 /v1/tools/search 的真实返回结构（title 可能为空，靠 site_name 补） */
    @Test
    fun parsesDeepSeekOfficialShape() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[
                   {"rerank_score":2.3,"title":"","site_name":"DeepSeek",
                    "url":"https://api-docs.deepseek.com/zh-cn/news/news260424.md",
                    "snippet":"短摘要","summary":"长摘要，够模型读","content":"","authority":3,"date":null},
                   {"rerank_score":1.1,"title":"某篇新闻","site_name":"网易",
                    "url":"https://www.163.com/x.html","snippet":"第二篇短摘要","summary":"第二篇长摘要"}
                ]}"""
            )
        )
        val r = SearchClient().search(
            SearchConfig(provider = "deepseek", maxResults = 5),
            "DeepSeek V4 发布",
            ApiConfig(apiKey = "sk-ds", baseUrl = server.url("/").toString())
        )
        assertEquals(2, r.size)
        assertEquals("DeepSeek", r[0].title)
        assertEquals("长摘要，够模型读", r[0].detail)
        assertEquals("短摘要", r[0].snippet)
        assertEquals("网易", r[1].site)
        assertEquals("某篇新闻", r[1].title)

        val req = server.takeRequest()
        assertEquals("/v1/tools/search", req.path)
        assertEquals("Bearer sk-ds", req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("\"query\":\"DeepSeek V4 发布\""))
        assertTrue(body.contains("\"count\":5"))
    }

    @Test
    fun parsesBochaShape() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"code":200,"data":{"webPages":{"value":[
                   {"name":"标题一","url":"https://a.example.com/1","snippet":"摘要一","summary":"更长摘要"},
                   {"name":"标题二","url":"https://b.example.com/2","snippet":"摘要二"}
                ]}}}"""
            )
        )
        val r = SearchClient().search(
            SearchConfig(
                provider = "bocha",
                apiKey = "sk-search",
                baseUrl = server.url("/v1/web-search").toString(),
                maxResults = 5
            ),
            "deepseek",
            ApiConfig()
        )
        assertEquals(2, r.size)
        assertEquals("标题一", r[0].title)
        assertEquals("https://a.example.com/1", r[0].url)
        assertEquals("更长摘要", r[0].detail)
        val req = server.takeRequest()
        assertEquals("/v1/web-search", req.path)
        assertEquals("Bearer sk-search", req.getHeader("Authorization"))
    }

    @Test
    fun parsesTavilyShape() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[
                   {"title":"Result A","url":"https://x.com/a","content":"content A","score":0.9}
                ]}"""
            )
        )
        val r = SearchClient().search(
            SearchConfig(provider = "tavily", apiKey = "tvly-x", baseUrl = server.url("/search").toString()),
            "hello",
            ApiConfig()
        )
        assertEquals(1, r.size)
        assertEquals("Result A", r[0].title)
        assertEquals("content A", r[0].snippet)
    }

    @Test
    fun parsesSearxngShape() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"results":[{"title":"SX","url":"https://s.example.com","content":"sx content"}]}"""
            )
        )
        val r = SearchClient().search(
            SearchConfig(provider = "searxng", baseUrl = server.url("/").toString()),
            "测试",
            ApiConfig()
        )
        assertEquals(1, r.size)
        assertEquals("SX", r[0].title)
        val req = server.takeRequest()
        assertTrue(req.path!!.startsWith("/search?q="))
        assertTrue(req.path!!.contains("format=json"))
    }

    @Test
    fun missingKeyFailsWithClearMessage() = runBlocking {
        try {
            SearchClient().search(
                SearchConfig(provider = "deepseek"),
                "q",
                ApiConfig(apiKey = "")
            )
            fail("应该抛错")
        } catch (e: ApiException) {
            assertTrue(e.message!!.contains("API Key"))
        }
    }

    @Test
    fun surfacesBadKey() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"error":{"message":"invalid api key"}}"""))
        try {
            SearchClient().search(
                SearchConfig(provider = "bocha", apiKey = "bad", baseUrl = server.url("/s").toString()),
                "q",
                ApiConfig()
            )
            fail("应该抛错")
        } catch (e: ApiException) {
            assertEquals("invalid api key", e.message)
        }
    }
}
