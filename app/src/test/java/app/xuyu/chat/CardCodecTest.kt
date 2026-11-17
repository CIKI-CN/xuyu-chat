package app.xuyu.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.Appearance
import app.xuyu.chat.data.CardCodec
import app.xuyu.chat.data.Example
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CardCodecTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    private fun makeAvatar(): File {
        val f = File(ctx.filesDir, "xuyu/images/test_avatar.png")
        f.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888)
        for (x in 0 until 300) {
            for (y in 0 until 300) {
                bmp.setPixel(x, y, Color.rgb(x % 256, y % 256, (x + y) % 256))
            }
        }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f
    }

    private fun sample(avatar: File?): Settings = settings(
        persona = Persona(
            name = "小柚子",
            tagline = "爱讲冷笑话的猫",
            avatarPath = avatar?.absolutePath,
            systemPrompt = "你是小柚子，一只住在窗台的猫。\n说话懒洋洋的，但很会安慰人。",
            greeting = "喵，你来啦。",
            examples = listOf(Example("在吗", "在，刚睡醒。"), Example("讲个笑话", "为什么猫不玩手机？因为爪子太短。")),
            temperature = 0.9f,
            maxTokens = 4096
        ),
        api = ApiConfig(baseUrl = "https://api.deepseek.com", apiKey = "sk-test-1234567890", model = "deepseek-chat"),
        appearance = Appearance(themeMode = 2, bgPath = null, bgOpacity = 0.62f)
    )

    @Test
    fun roundTripWithAvatar() {
        val avatar = makeAvatar()
        val s = sample(avatar)
        val payload = CardCodec.encode(ctx, s, s.activeFor(s.mode), includeAvatar = true, includeBg = false)
        println("PAYLOAD_LENGTH=" + payload.length)

        assertTrue(payload.startsWith(CardCodec.PREFIX))

        val share = CardCodec.shareText(s.activeFor(s.mode), s.api.apiKey, payload)
        val decoded = CardCodec.decode(ctx, share)

        assertEquals("小柚子", decoded.persona.name)
        assertEquals("爱讲冷笑话的猫", decoded.persona.tagline)
        assertEquals(2, decoded.persona.examples.size)
        assertEquals("在，刚睡醒。", decoded.persona.examples[0].ai)
        assertEquals(0.9f, decoded.persona.temperature, 0.001f)
        assertEquals(4096, decoded.persona.maxTokens)
        assertEquals("sk-test-1234567890", decoded.api.apiKey)
        assertEquals(2, decoded.appearance.themeMode)
        assertEquals(0.62f, decoded.appearance.bgOpacity, 0.001f)
        assertNotNull("头像应该被还原成文件", decoded.persona.avatarPath)
        assertTrue(File(decoded.persona.avatarPath!!).length() > 100)
    }

    @Test
    fun payloadStaysSmallWithoutAvatar() {
        val s = sample(null)
        val payload = CardCodec.encode(ctx, s, s.activeFor(s.mode), includeAvatar = false, includeBg = false)
        println("PAYLOAD_NO_AVATAR_LENGTH=" + payload.length)
        assertTrue("纯文本角色卡应该很短，实际 " + payload.length, payload.length < 900)
    }

    @Test
    fun extractFromMessyChatText() {
        val s = sample(null)
        val payload = CardCodec.encode(ctx, s, s.activeFor(s.mode), includeAvatar = false, includeBg = false)
        val messy = "给你看看这个角色\n\n" + payload + "\n\n复制这段就能导入了"
        assertEquals(payload.removePrefix(CardCodec.PREFIX), CardCodec.extractPayload(messy))
        assertEquals(payload.removePrefix(CardCodec.PREFIX), CardCodec.extractPayload(payload))
        assertEquals(payload.removePrefix(CardCodec.PREFIX), CardCodec.extractPayload("  " + payload + "  \n"))
    }

    @Test
    fun truncatedPayloadFailsLoudly() {
        val s = sample(null)
        val payload = CardCodec.encode(ctx, s, s.activeFor(s.mode), includeAvatar = false, includeBg = false)
        val cut = payload.substring(0, payload.length - 20)
        try {
            CardCodec.decode(ctx, cut)
            fail("截断的角色卡不应该能导入")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("截断") || e.message!!.contains("损坏"))
        }
    }
}
