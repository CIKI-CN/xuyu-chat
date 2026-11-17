package app.xuyu.chat

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.view.View
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.xuyu.chat.data.ApiConfig
import app.xuyu.chat.data.Appearance
import app.xuyu.chat.data.Example
import app.xuyu.chat.data.Message
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Quota
import app.xuyu.chat.data.SearchConfig
import app.xuyu.chat.data.Settings
import app.xuyu.chat.data.Source
import app.xuyu.chat.ui.ChatScreen
import app.xuyu.chat.ui.DisclaimerScreen
import app.xuyu.chat.ui.GuideScreen
import app.xuyu.chat.ui.ModeScreen
import app.xuyu.chat.ui.SettingsScreen
import app.xuyu.chat.ui.XyTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 没有真机也能看一眼界面：用 Robolectric 的 native 渲染把 Compose 画出来存成 PNG。
 * 产物：E:\91\xuyu-chat\screenshots\
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @get:Rule
    val rule = createComposeRule()

    private val app: Application get() = ApplicationProvider.getApplicationContext()

    private fun outDir(): File = File("E:\\91\\xuyu-chat\\screenshots").apply { mkdirs() }

    private fun makeAvatar(): String {
        val f = File(app.filesDir, "xuyu/images/shot_avatar.png")
        f.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 320f, 320f,
            0xFFFFB7C5.toInt(), 0xFF8FB8FF.toInt(), Shader.TileMode.CLAMP
        )
        c.drawCircle(160f, 160f, 160f, paint)
        paint.shader = null
        paint.color = 0xFFFFFFFF.toInt()
        c.drawCircle(120f, 145f, 16f, paint)
        c.drawCircle(200f, 145f, 16f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 8f
        c.drawArc(120f, 150f, 200f, 220f, 20f, 140f, false, paint)
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return f.absolutePath
    }

    private fun makeBackground(): String {
        val f = File(app.filesDir, "xuyu/images/shot_bg.png")
        f.parentFile?.mkdirs()
        val bmp = Bitmap.createBitmap(720, 1280, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val paint = Paint()
        paint.shader = LinearGradient(
            0f, 0f, 720f, 1280f,
            intArrayOf(0xFFDCE9FF.toInt(), 0xFFFFE6EE.toInt(), 0xFFE8F3FF.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        c.drawRect(0f, 0f, 720f, 1280f, paint)
        bmp.compress(Bitmap.CompressFormat.PNG, 92, f.outputStream())
        bmp.recycle()
        return f.absolutePath
    }

    private fun seed(vm: ChatViewModel, themeMode: Int, withBg: Boolean) {
        val avatar = makeAvatar()
        val bg = if (withBg) makeBackground() else null
        val persona = Persona(
            name = "小柚子",
            tagline = "住在窗台的猫，擅长接住你的坏情绪",
            avatarPath = avatar,
            systemPrompt = "你是小柚子。",
            greeting = "喵，你来啦。今天怎么样？",
            examples = listOf(Example("在吗", "在，刚睡醒。"))
        )
        vm.applySettings(
            settings(
                persona = persona,
                api = ApiConfig(apiKey = "sk-demo", model = "deepseek-flash"),
                appearance = Appearance(themeMode = themeMode, bgPath = bg, bgOpacity = 0.55f)
            )
        )
        val s = vm.store.activeSession()!!
        vm.store.appendMessage(s.id, Message(role = "user", content = "今天被领导骂了，有点难受"))
        vm.store.appendMessage(
            s.id,
            Message(
                role = "assistant",
                content = "喵……先过来坐会儿。\n\n被骂这件事，难受是真的，但不代表你做得很差。**别人发火的时候，说的往往是他的情绪，不是你的水平。**\n\n想说说具体发生了什么吗？我陪你捋一捋。"
            )
        )
        vm.store.appendMessage(s.id, Message(role = "user", content = "给我写段代码冷静一下"))
        vm.store.appendMessage(
            s.id,
            Message(
                role = "assistant",
                content = "好，写个短的：\n\n```kotlin\nfun calm(n: Int): List<String> =\n    (1..n).map { \"深呼吸 \$it\" }\n\ncalm(3).forEach(::println)\n```\n\n跑一下，三行输出，慢慢来。"
            )
        )
        vm.store.appendMessage(s.id, Message(role = "user", content = "DeepSeek 最近有什么更新？"))
        vm.store.appendMessage(
            s.id,
            Message(
                role = "assistant",
                content = "查到几条，大概是这样：\n\n1. **模型迭代**：最近的版本主要在长文本和代码上做了加强 [1]\n2. **价格**：输入输出的价格表有过调整 [2]\n\n想让我展开哪条？",
                sources = listOf(
                    Source(
                        "DeepSeek 官方文档：模型与定价",
                        "https://platform.deepseek.com/docs",
                        "模型列表、上下文长度与价格说明",
                        "模型列表、上下文长度与价格说明",
                        "DeepSeek"
                    ),
                    Source(
                        "DeepSeek 发布新版本，上下文更长",
                        "https://example.com/news/1",
                        "新版本在长文本任务上的表现有所提升",
                        "新版本在长文本任务上的表现有所提升",
                        "示例新闻"
                    )
                )
            )
        )
    }

    @Test
    fun chatLight() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        render("chat-light.png") {
            XyTheme(1) { ChatScreen(vm, onOpenSettings = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun chatDarkWithBackground() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 2, withBg = true)
        render("chat-dark-bg.png") {
            XyTheme(2) { ChatScreen(vm, onOpenSettings = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun settingsLight() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        render("settings-light.png") {
            XyTheme(1) { SettingsScreen(vm, onBack = {}, onOpenGuide = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun settingsQuota() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        vm.store.addUsage(9800, 6200)
        vm.applySettings(
            vm.store.settings.value.copy(
                quota = Quota(enabled = true, period = 0, limitTokens = 100_000, block = true)
            )
        )
        render("settings-quota.png", before = {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("用量与限额"))
            rule.waitForIdle()
        }) {
            XyTheme(1) { SettingsScreen(vm, onBack = {}, onOpenGuide = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun settingsSearch() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        vm.applySettings(
            vm.store.settings.value.copy(
                search = SearchConfig(enabled = true, provider = "deepseek")
            )
        )
        render("settings-search.png", before = {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("联网搜索"))
            rule.waitForIdle()
        }) {
            XyTheme(1) { SettingsScreen(vm, onBack = {}, onOpenGuide = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun modePicker() {
        render("mode.png") {
            XyTheme(1) { ModeScreen(current = "", onPick = {}, onCancel = null) }
        }
    }

    @Test
    fun settingsContext() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        render("settings-context.png", before = {
            rule.onAllNodes(hasScrollAction()).onFirst().performScrollToNode(hasText("上下文长度"))
            rule.waitForIdle()
        }) {
            XyTheme(1) { SettingsScreen(vm, onBack = {}, onOpenGuide = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun settingsRp() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        val base = vm.store.settings.value
        val a = base.activeFor(Settings.MODE_NORMAL).copy(
            id = "rp-a",
            name = "林照",
            tagline = "雨夜书店的老板",
            greeting = "……这么晚，还进来躲雨？"
        )
        val b = base.activeFor(Settings.MODE_NORMAL).copy(
            id = "rp-b",
            name = "阿绫",
            tagline = "住在隔壁的琴手",
            greeting = "别敲了，门没锁。"
        )
        vm.applySettings(
            base.copy(mode = Settings.MODE_RP, rpPersonas = listOf(a, b), rpActivePersonaId = a.id)
        )
        render("settings-rp.png") {
            XyTheme(1) { SettingsScreen(vm, onBack = {}, onOpenGuide = {}, onSwitchMode = {}) }
        }
    }

    @Test
    fun guidePage() {
        render("guide.png") {
            XyTheme(1) { GuideScreen(onClose = {}) }
        }
    }

    @Test
    fun disclaimerPage() {
        render("disclaimer.png") {
            XyTheme(1) { DisclaimerScreen(onAccept = {}, onExit = {}) }
        }
    }

    @Test
    fun drawerOpen() {
        val vm = ChatViewModel(app)
        seed(vm, themeMode = 1, withBg = false)
        render("drawer.png", before = {
            rule.onNodeWithContentDescription("会话列表").performClick()
            rule.mainClock.advanceTimeBy(600)
            rule.waitForIdle()
        }) {
            XyTheme(1) { ChatScreen(vm, onOpenSettings = {}, onSwitchMode = {}) }
        }
    }

    private fun render(
        name: String,
        before: () -> Unit = {},
        content: @androidx.compose.runtime.Composable () -> Unit
    ) {
        var root: View? = null
        rule.setContent {
            val v = LocalView.current
            SideEffect { root = v }
            content()
        }
        rule.waitForIdle()
        before()
        rule.mainClock.advanceTimeBy(600)
        rule.waitForIdle()

        val view = root ?: error("no view")
        val density = view.resources.displayMetrics.density
        val w = if (view.width > 0) view.width else (411 * density).toInt()
        val h = if (view.height > 0) view.height else (891 * density).toInt()
        view.measure(
            View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, w, h)

        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val f = File(outDir(), name)
        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("SCREENSHOT=" + f.absolutePath + " " + bmp.width + "x" + bmp.height)
    }
}
