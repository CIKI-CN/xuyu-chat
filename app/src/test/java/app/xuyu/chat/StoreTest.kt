package app.xuyu.chat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.xuyu.chat.data.Message
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Quota
import app.xuyu.chat.data.Settings
import app.xuyu.chat.data.Store
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class StoreTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun startsWithOneSessionAndGreeting() {
        val store = Store(ctx)
        val session = store.activeSession()
        assertNotNull(session)
        assertTrue(session!!.messages.isNotEmpty())
        assertEquals("assistant", session.messages.first().role)
    }

    @Test
    fun persistsMessagesAndSettings() {
        val store = Store(ctx)
        val session = store.activeSession()!!
        store.appendMessage(session.id, Message(role = "user", content = "你好呀，今天的风好大"))
        store.saveSettings(settings(persona = Persona(name = "阿黄", systemPrompt = "测试人设")))

        Thread.sleep(500)

        val reopened = Store(ctx)
        assertEquals("阿黄", reopened.settings.value.activeFor(app.xuyu.chat.data.Settings.MODE_NORMAL).name)
        assertEquals("测试人设", reopened.settings.value.activeFor(app.xuyu.chat.data.Settings.MODE_NORMAL).systemPrompt)
        val found = reopened.sessions.value.flatMap { it.messages }.any { it.content == "你好呀，今天的风好大" }
        assertTrue("消息应该被持久化", found)
        assertEquals("你好呀，今天的风好大", reopened.sessions.value.first { it.id == session.id }.title)
    }

    @Test
    fun deletingLastSessionKeepsOne() {
        val store = Store(ctx)
        val first = store.activeSession()!!
        store.appendMessage(first.id, Message(role = "user", content = "hi"))
        store.deleteSession(first.id)
        assertTrue(store.sessions.value.isNotEmpty())
        assertTrue(store.sessions.value.first().messages.isEmpty())
    }

    @Test
    fun consentStartsEmptyAndPersists() {
        val store = Store(ctx)
        assertEquals(0, store.consent.value.versionCode)
        assertEquals(0, store.consent.value.disclaimerVersion)
        assertFalse(store.consent.value.guideSeen)

        store.acceptConsent(5, 1)
        store.markGuideSeen()
        Thread.sleep(400)

        val reopened = Store(ctx)
        assertEquals(5, reopened.consent.value.versionCode)
        assertEquals(1, reopened.consent.value.disclaimerVersion)
        assertTrue(reopened.consent.value.guideSeen)

        // 新版本重新同意后，指南不应该再弹
        reopened.acceptConsent(6, 1)
        Thread.sleep(300)
        assertTrue(Store(ctx).consent.value.guideSeen)
    }

    @Test
    fun rpPersonasAreSeparateFromNormal() {
        val store = Store(ctx)
        val normal = store.settings.value.activeFor(Settings.MODE_NORMAL)
        val rp = normal.copy(id = "rp-1", name = "雨夜书店老板")
        store.savePersona(Settings.MODE_RP, rp)

        assertEquals("雨夜书店老板", store.settings.value.activeFor(Settings.MODE_RP).name)
        assertEquals(normal.name, store.settings.value.activeFor(Settings.MODE_NORMAL).name)
        assertEquals(1, store.settings.value.personas.size)
        assertEquals(1, store.settings.value.rpPersonas.size)
    }

    @Test
    fun sessionsCarryTheirMode() {
        val store = Store(ctx)
        val s = store.createSession(Settings.MODE_RP, "p1", "喵")
        assertEquals(Settings.MODE_RP, s.mode)
        assertEquals("p1", s.personaId)
        assertEquals(1, store.sessions.value.count { it.mode == Settings.MODE_RP })
    }

    @Test
    fun usageAccumulates() {
        val store = Store(ctx)
        store.addUsage(100, 50)
        store.addUsage(30, 20)
        assertEquals(200L, store.usage.value.totalTokens)
        assertEquals(2, store.usage.value.requests)
        assertEquals(200L, store.usedTokens(Quota(period = 0)))
        assertEquals(200L, store.usedTokens(Quota(period = 1)))
    }

    @Test
    fun usageSurvivesRestart() {
        val store = Store(ctx)
        store.addUsage(400, 100)
        Thread.sleep(400)
        val reopened = Store(ctx)
        assertEquals(500L, reopened.usage.value.totalTokens)
        assertEquals(500L, reopened.usedTokens(Quota(period = 0)))
    }

    @Test
    fun truncateFromRemovesTail() {
        val store = Store(ctx)
        val s = store.activeSession()!!
        store.appendMessage(s.id, Message(role = "user", content = "q1"))
        store.appendMessage(s.id, Message(role = "assistant", content = "a1"))
        val sid = store.session(s.id)!!
        store.truncateFrom(s.id, sid.messages.size - 1)
        val after = store.session(s.id)!!
        assertEquals("q1", after.messages.last().content)
    }
}
