package app.xuyu.chat

import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.ChatConfig
import app.xuyu.chat.data.ModelInfo
import app.xuyu.chat.data.Quota
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ConfigTest {

    /** 公开发布版：包里不能带任何 Key */
    @Test
    fun shipsWithoutApiKey() {
        assertEquals("", ApiConfig().apiKey)
        assertEquals("", Settings().api.apiKey)
        assertEquals("https://api.deepseek.com", ApiConfig().baseUrl)
        assertEquals("deepseek-flash", ApiConfig().model)
        assertEquals("deepseek", SearchConfig().provider)
    }

    @Test
    fun quotaDefaultsAreOff() {
        val q = Quota()
        assertFalse(q.enabled)
        assertEquals(0, q.period)
        assertEquals(200_000, q.limitTokens)
    }

    @Test
    fun contextWindowFollowsModel() {
        // DeepSeek V4 系列都是 1M
        assertEquals(1_000_000, ModelInfo.contextWindow("deepseek-flash"))
        assertEquals(1_000_000, ModelInfo.contextWindow("deepseek-v4-pro"))
        assertEquals(1_000_000, ModelInfo.contextWindow("deepseek-chat"))
        assertEquals(200_000, ModelInfo.contextWindow("claude-sonnet-4"))
        assertEquals(128_000, ModelInfo.contextWindow("gpt-4o-mini"))
        assertEquals(131_072, ModelInfo.contextWindow("qwen-max"))
        assertEquals(128_000, ModelInfo.contextWindow("some-unknown-model"))

        // 比例换算
        val cfg = ChatConfig(contextRatio = 0.1f)
        assertEquals(100_000, cfg.historyTokens(1_000_000))
        assertEquals(12_800, cfg.historyTokens(128_000))
        // 极小比例也有下限，避免把历史裁成 0
        assertEquals(2_000, ChatConfig(contextRatio = 0.000001f).historyTokens(1_000_000))
        assertEquals("1M", ModelInfo.label(1_000_000))
        assertEquals("131K", ModelInfo.label(131_072))
    }
}
