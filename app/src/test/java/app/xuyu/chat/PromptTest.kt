package app.xuyu.chat

import app.xuyu.chat.data.Prompt
import app.xuyu.chat.data.Example
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Source
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptTest {

    @Test
    fun systemPromptContainsPersonaBits() {
        val p = Persona(
            name = "阿岩",
            tagline = "刀子嘴豆腐心",
            systemPrompt = "说话很冲但会记得对方的生日。",
            examples = listOf(Example("我失恋了", "哦。……过来，我请你吃串。"))
        )
        val s = Prompt.system(p)
        assertTrue(s.contains("阿岩"))
        assertTrue(s.contains("刀子嘴豆腐心"))
        assertTrue(s.contains("说话很冲"))
        assertTrue(s.contains("我失恋了"))
        assertTrue(s.contains("过来，我请你吃串"))
        assertTrue(s.contains("不要提到自己是 AI"))
    }

    @Test
    fun emptyPersonaStillWorks() {
        val s = Prompt.system(Persona(name = "", tagline = "", systemPrompt = "", examples = emptyList()))
        assertTrue(s.isNotBlank())
        assertTrue(s.contains("AI"))
    }

    @Test
    fun searchContextIsWellFormed() {
        val block = Prompt.searchContext(
            listOf(
                Source("DeepSeek 发布 V4", "https://example.com/v4", "摘要一"),
                Source("另一篇", "https://example.com/b", "")
            )
        )
        assertTrue(block.contains("[1] DeepSeek 发布 V4"))
        assertTrue(block.contains("URL: https://example.com/v4"))
        assertTrue(block.contains("[2] 另一篇"))
        assertTrue(block.contains("不要编造"))
        assertTrue("没有资料时应该是空串", Prompt.searchContext(emptyList()).isEmpty())
    }
}
