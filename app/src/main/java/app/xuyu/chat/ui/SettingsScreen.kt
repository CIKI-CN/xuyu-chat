package app.xuyu.chat.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.ChatViewModel
import app.xuyu.chat.data.CardCodec
import app.xuyu.chat.data.Example
import app.xuyu.chat.data.ModelInfo
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Settings
import app.xuyu.chat.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun SettingsScreen(
    vm: ChatViewModel,
    onBack: () -> Unit,
    onOpenGuide: () -> Unit,
    onSwitchMode: () -> Unit
) {
    val saved by vm.store.settings.collectAsState()
    val xy = LocalXy.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var draft by remember { mutableStateOf(saved) }
    val active = draft.activeFor(draft.mode)
    var showKey by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testOk by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var editExample by remember { mutableStateOf<Int?>(null) }
    var includeAvatar by remember { mutableStateOf(true) }
    var includeBg by remember { mutableStateOf(false) }
    var payloadLen by remember { mutableStateOf(0) }
    var balanceText by remember { mutableStateOf<String?>(null) }
    var balanceError by remember { mutableStateOf<String?>(null) }
    var balanceLoading by remember { mutableStateOf(false) }
    var searchTesting by remember { mutableStateOf(false) }
    var searchResult by remember { mutableStateOf<String?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }

    val usage by vm.store.usage.collectAsState()

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun goBack() {
        vm.applySettings(draft)
        onBack()
    }

    BackHandler { goBack() }

    // 输入停下来 400ms 自动存
    LaunchedEffect(draft) {
        delay(400)
        vm.applySettings(draft)
    }

    val pickAvatar = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val f = File(ctx.filesDir, "xuyu/images/avatar_${System.currentTimeMillis()}.jpg")
                val ok = withContext(Dispatchers.IO) { Images.savePicked(ctx, uri, f, 720, 88) }
                if (ok) {
                    draft = draft.withPersona(active.copy(avatarPath = f.absolutePath))
                } else {
                    toast("这张图读取失败，换一张试试")
                }
            }
        }
    }

    val pickBg = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val f = File(ctx.filesDir, "xuyu/images/bg_${System.currentTimeMillis()}.jpg")
                val ok = withContext(Dispatchers.IO) { Images.savePicked(ctx, uri, f, 1800, 86) }
                if (ok) {
                    draft = draft.copy(appearance = draft.appearance.copy(bgPath = f.absolutePath))
                } else {
                    toast("这张图读取失败，换一张试试")
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(xy.bg)
            .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
    ) {
        // 顶栏
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { goBack() }) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = xy.textDim)
            }
            Text("设置", fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
            Spacer(Modifier.weight(1f))
            Text("自动保存", fontSize = 11.sp, color = xy.textFaint, modifier = Modifier.padding(end = 14.dp))
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 30.dp)
        ) {
            // ---------------- 模式 ----------------
            item {
                SectionCard(title = "模式") {
                    SettingRow(
                        title = if (draft.mode == Settings.MODE_RP) "角色扮演" else "日常聊天",
                        desc = "两种模式的聊天记录和角色卡分开存",
                        clickable = true,
                        onClick = onSwitchMode,
                        trailing = {
                            Text("切换 ›", fontSize = 12.sp, color = xy.primary)
                        }
                    )
                }
            }

            // ---------------- 角色 ----------------
            item {
                SectionCard(title = "角色 · 人设卡") {
                    if (draft.mode == Settings.MODE_RP) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            draft.rpPersonas.forEach { p ->
                                PersonaChip(
                                    persona = p,
                                    selected = p.id == active.id,
                                    onClick = { draft = draft.copy(rpActivePersonaId = p.id) }
                                )
                            }
                            XyChip(text = "＋ 新建", selected = false) {
                                val np = active.copy(
                                    id = java.util.UUID.randomUUID().toString(),
                                    name = "新角色",
                                    avatarPath = null,
                                    greeting = "……"
                                )
                                draft = draft.copy(
                                    rpPersonas = draft.rpPersonas + np,
                                    rpActivePersonaId = np.id
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "共 ${draft.rpPersonas.size} 张卡 · 每个会话绑定一张",
                                fontSize = 11.sp,
                                color = xy.textFaint,
                                modifier = Modifier.weight(1f)
                            )
                            if (draft.rpPersonas.size > 1) {
                                TextButton(
                                    onClick = {
                                        val rest = draft.rpPersonas.filterNot { it.id == active.id }
                                        draft = draft.copy(
                                            rpPersonas = rest,
                                            rpActivePersonaId = rest.first().id
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                                ) { Text("删除这个角色", fontSize = 11.sp, color = Color(0xFFE5484D)) }
                            }
                        }
                        Divider(color = xy.line, modifier = Modifier.padding(vertical = 10.dp))
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(active.avatarPath, active.name, 62.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("头像", color = xy.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(
                                "会显示在聊天和分享的角色卡里",
                                color = xy.textFaint,
                                fontSize = 11.sp
                            )
                            Row(modifier = Modifier.padding(top = 6.dp)) {
                                TextButton(
                                    onClick = {
                                        pickAvatar.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                ) { Text("更换", fontSize = 12.sp) }
                                if (!active.avatarPath.isNullOrBlank()) {
                                    TextButton(
                                        onClick = {
                                            draft = draft.withPersona(active.copy(avatarPath = null))
                                        },
                                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                    ) { Text("移除", fontSize = 12.sp, color = xy.textDim) }
                                }
                            }
                        }
                    }

                    XyField(
                        value = active.name,
                        onChange = { draft = draft.withPersona(active.copy(name = it)) },
                        label = "AI 名字",
                        placeholder = "絮语"
                    )

                    XyField(
                        value = active.tagline,
                        onChange = { draft = draft.withPersona(active.copy(tagline = it)) },
                        label = "一句话简介",
                        placeholder = "一个愿意陪你慢慢聊天的 AI"
                    )

                    XyField(
                        value = active.systemPrompt,
                        onChange = { draft = draft.withPersona(active.copy(systemPrompt = it)) },
                        label = "详细设定",
                        placeholder = "背景故事、性格、说话方式…",
                        singleLine = false,
                        minLines = 6
                    )

                    XyField(
                        value = active.greeting,
                        onChange = { draft = draft.withPersona(active.copy(greeting = it)) },
                        label = "开场白",
                        placeholder = "开新对话时 TA 说的第一句话",
                        singleLine = false,
                        minLines = 2
                    )

                    if (draft.mode == Settings.MODE_RP) {
                        XyField(
                            value = active.userRole,
                            onChange = { draft = draft.withPersona(active.copy(userRole = it)) },
                            label = "我的角色",
                            placeholder = "我是谁：身份、和 TA 的关系、现在的处境",
                            singleLine = false,
                            minLines = 3
                        )
                        XyField(
                            value = active.scene,
                            onChange = { draft = draft.withPersona(active.copy(scene = it)) },
                            label = "场景 / 世界观",
                            placeholder = "时间地点、正在发生什么、有什么规矩",
                            singleLine = false,
                            minLines = 3
                        )
                    }
                }
            }

            // ---------------- 示例对话 ----------------
            item {
                SectionCard(
                    title = "示例对话",
                    subtitle = "给 1~3 组范例，TA 说话的味道会更稳（可留空）"
                ) {
                    active.examples.forEachIndexed { index, ex ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "我：" + ex.user.replace("\n", " "),
                                    fontSize = 12.sp,
                                    color = xy.textDim,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    active.name + "：" + ex.ai.replace("\n", " "),
                                    fontSize = 12.sp,
                                    color = xy.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }
                            IconButton(onClick = { editExample = index }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Edit, null, tint = xy.textFaint, modifier = Modifier.size(15.dp))
                            }
                            IconButton(
                                onClick = {
                                    val list = active.examples.toMutableList()
                                    list.removeAt(index)
                                    draft = draft.withPersona(active.copy(examples = list))
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Filled.Delete, null, tint = xy.textFaint, modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                    TextButton(
                        onClick = { editExample = -1 },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp)
                    ) {
                        Text("+ 添加一组", fontSize = 13.sp)
                    }
                }
            }

            // ---------------- 参数 ----------------
            item {
                SectionCard(title = "生成参数") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("温度", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.weight(1f))
                        Text(
                            String.format("%.1f", active.temperature),
                            fontSize = 13.sp,
                            color = xy.textDim
                        )
                    }
                    Slider(
                        value = active.temperature,
                        onValueChange = {
                            draft = draft.withPersona(active.copy(temperature = it))
                        },
                        valueRange = 0f..1.5f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "低一点更稳，高一点更有性格",
                        fontSize = 11.sp,
                        color = xy.textFaint
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("单次回复上限", fontSize = 13.sp, color = xy.text)
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        items = listOf(
                            "1024" to "1024",
                            "2048" to "2048",
                            "4096" to "4096",
                            "8192" to "8192"
                        ),
                        selected = active.maxTokens.toString(),
                        onSelect = {
                            draft = draft.withPersona(active.copy(maxTokens = it.toIntOrNull() ?: 2048))
                        }
                    )
                    XyField(
                        value = active.modelOverride,
                        onChange = { draft = draft.withPersona(active.copy(modelOverride = it)) },
                        label = "这个角色专用的模型",
                        placeholder = "留空就跟着接口里的模型走"
                    )

                    SettingRow(
                        title = "思考模式",
                        desc = if (active.thinking) "先想再答，更准但更慢、更费 token" else "直接回答，快且省",
                        trailing = {
                            Switch(
                                checked = active.thinking,
                                onCheckedChange = {
                                    draft = draft.withPersona(active.copy(thinking = it))
                                }
                            )
                        }
                    )

                    if (draft.mode == Settings.MODE_RP) {
                        Spacer(Modifier.height(6.dp))
                        Text("回复长度", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = listOf("0" to "短", "1" to "中", "2" to "长"),
                            selected = active.replyStyle.toString(),
                            onSelect = {
                                draft = draft.withPersona(active.copy(replyStyle = it.toIntOrNull() ?: 1))
                            }
                        )
                        SettingRow(
                            title = "允许动作描写",
                            desc = "用 *星号* 写神态、动作、环境",
                            trailing = {
                                Switch(
                                    checked = active.allowActions,
                                    onCheckedChange = {
                                        draft = draft.withPersona(active.copy(allowActions = it))
                                    }
                                )
                            }
                        )
                        SettingRow(
                            title = "严格不出戏",
                            desc = "不许提 AI、不许解释自己在扮演",
                            trailing = {
                                Switch(
                                    checked = active.stayInCharacter,
                                    onCheckedChange = {
                                        draft = draft.withPersona(active.copy(stayInCharacter = it))
                                    }
                                )
                            }
                        )
                    }
                }
            }

            // ---------------- 上下文 ----------------
            item {
                SectionCard(
                    title = "上下文长度",
                    subtitle = "按模型的窗口大小取比例；超出就从最早的开始丢"
                ) {
                    val modelName = active.modelOverride.ifBlank { draft.api.model }
                    val window = ModelInfo.contextWindow(modelName)
                    val budget = draft.chat.historyTokens(window)
                    Text(
                        "当前模型 $modelName · 窗口 ${ModelInfo.label(window)}",
                        fontSize = 12.sp,
                        color = xy.textDim
                    )
                    Spacer(Modifier.height(10.dp))
                    ChipRow(
                        items = listOf(
                            "0.05" to "5%",
                            "0.1" to "10%",
                            "0.25" to "25%",
                            "0.5" to "50%"
                        ),
                        selected = draft.chat.contextRatio.toString(),
                        onSelect = {
                            draft = draft.copy(
                                chat = draft.chat.copy(contextRatio = it.toFloatOrNull() ?: 0.1f)
                            )
                        }
                    )
                    Text(
                        "实际带约 ${ModelInfo.label(budget)} token（约 ${(budget / 10000).coerceAtLeast(1)} 万字）",
                        fontSize = 12.sp,
                        color = xy.text
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "带得越多，每次请求的输入费用越高：DeepSeek 输入约 ¥1~2 / 百万 token（缓存命中更低）。" +
                            "1M 窗口按 10% 算就是 10 万 token，约 ¥0.1~0.2 一次。",
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        color = xy.textFaint
                    )
                }
            }

            // ---------------- 接口 ----------------
            item {
                SectionCard(title = "接口 · API") {
                    XyField(
                        value = draft.api.baseUrl,
                        onChange = { draft = draft.copy(api = draft.api.copy(baseUrl = it)) },
                        label = "Base URL",
                        placeholder = "https://api.deepseek.com",
                        keyboardType = KeyboardType.Uri
                    )
                    ChipRow(
                        items = listOf(
                            "https://api.deepseek.com" to "DeepSeek",
                            "https://api.openai.com/v1" to "OpenAI",
                            "http://10.0.2.2:11434/v1" to "本地 Ollama"
                        ),
                        selected = draft.api.baseUrl,
                        onSelect = { draft = draft.copy(api = draft.api.copy(baseUrl = it)) }
                    )

                    XyField(
                        value = draft.api.apiKey,
                        onChange = { draft = draft.copy(api = draft.api.copy(apiKey = it)) },
                        label = "API Key",
                        placeholder = "sk-...",
                        visual = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            TextButton(onClick = { showKey = !showKey }) {
                                Text(if (showKey) "隐藏" else "显示", fontSize = 12.sp)
                            }
                        }
                    )
                    TextButton(
                        onClick = onOpenGuide,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp, vertical = 0.dp)
                    ) {
                        Text("怎么获取 Key / 怎么充值 →", fontSize = 12.sp)
                    }

                    XyField(
                        value = draft.api.model,
                        onChange = { draft = draft.copy(api = draft.api.copy(model = it)) },
                        label = "模型",
                        placeholder = "deepseek-chat"
                    )
                    ChipRow(
                        items = listOf(
                            "deepseek-flash" to "deepseek-flash",
                            "deepseek-v4-pro" to "deepseek-v4-pro",
                            "deepseek-chat" to "deepseek-chat（旧）"
                        ),
                        selected = draft.api.model,
                        onSelect = { draft = draft.copy(api = draft.api.copy(model = it)) }
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Button(
                            onClick = {
                                if (testing) return@Button
                                testing = true
                                testResult = null
                                scope.launch {
                                    val r = vm.testApi(draft.api.baseUrl, draft.api.apiKey, draft.api.model)
                                    testing = false
                                    r.onSuccess {
                                        testOk = true
                                        testResult = "连接成功，模型回了：$it"
                                    }.onFailure {
                                        testOk = false
                                        testResult = "失败：${it.message}"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            enabled = !testing && draft.api.apiKey.isNotBlank()
                        ) {
                            Text(if (testing) "测试中…" else "测试连接", fontSize = 13.sp)
                        }
                        if (testResult != null) {
                            Spacer(Modifier.width(10.dp))
                            Text(
                                testResult!!,
                                fontSize = 11.sp,
                                color = if (testOk) Color(0xFF18964B) else Color(0xFFE5484D),
                                modifier = Modifier.weight(1f),
                                maxLines = 3
                            )
                        }
                    }
                    Text(
                        "Key 只存在这台手机上，不会上传到任何别的地方",
                        fontSize = 11.sp,
                        color = xy.textFaint,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    Divider(color = xy.line, modifier = Modifier.padding(vertical = 12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("账户余额", fontSize = 13.sp, color = xy.text)
                            Text(
                                balanceText ?: balanceError ?: "只有 DeepSeek 官方接口能查",
                                fontSize = 11.sp,
                                color = when {
                                    balanceError != null -> Color(0xFFE5484D)
                                    balanceText != null -> xy.text
                                    else -> xy.textFaint
                                },
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (balanceLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = xy.primary
                            )
                        } else {
                            TextButton(onClick = {
                                if (draft.api.apiKey.isBlank()) {
                                    toast("先把 API Key 填上")
                                    return@TextButton
                                }
                                balanceLoading = true
                                balanceError = null
                                scope.launch {
                                    vm.queryBalance(draft.api.baseUrl, draft.api.apiKey)
                                        .onSuccess {
                                            balanceText = it.pretty() + if (it.available) "" else "（账户不可用）"
                                            balanceError = null
                                        }
                                        .onFailure {
                                            balanceText = null
                                            balanceError = it.message ?: "查询失败"
                                        }
                                    balanceLoading = false
                                }
                            }) { Text(if (balanceText == null) "查询" else "刷新", fontSize = 12.sp) }
                        }
                    }
                }
            }

            // ---------------- 用量与限额 ----------------
            item {
                SectionCard(
                    title = "用量与限额",
                    subtitle = "按接口返回的 token 统计；不返回用量的接口这里会一直是 0"
                ) {
                    SettingRow(
                        title = "启用限额",
                        desc = if (draft.quota.enabled) "到了上限会拦着（或只提醒）" else "关着就不限制",
                        trailing = {
                            Switch(
                                checked = draft.quota.enabled,
                                onCheckedChange = { draft = draft.copy(quota = draft.quota.copy(enabled = it)) }
                            )
                        }
                    )

                    if (draft.quota.enabled) {
                        Text("周期", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = listOf("0" to "每天", "1" to "每个月"),
                            selected = draft.quota.period.toString(),
                            onSelect = { draft = draft.copy(quota = draft.quota.copy(period = it.toIntOrNull() ?: 0)) }
                        )

                        Text("上限（token）", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = listOf(
                                "100000" to "10 万",
                                "500000" to "50 万",
                                "2000000" to "200 万"
                            ),
                            selected = draft.quota.limitTokens.toString(),
                            onSelect = { draft = draft.copy(quota = draft.quota.copy(limitTokens = it.toIntOrNull() ?: 0)) }
                        )
                        XyField(
                            value = draft.quota.limitTokens.toString(),
                            onChange = { v ->
                                val n = v.filter { it.isDigit() }.take(9).toIntOrNull() ?: 0
                                draft = draft.copy(quota = draft.quota.copy(limitTokens = n))
                            },
                            label = "自定义上限",
                            placeholder = "直接填数字，比如 300000",
                            keyboardType = KeyboardType.Number
                        )

                        SettingRow(
                            title = "超限后禁止发送",
                            desc = if (draft.quota.block) "到上限就发不出去" else "只提示一下，不拦着",
                            trailing = {
                                Switch(
                                    checked = draft.quota.block,
                                    onCheckedChange = { draft = draft.copy(quota = draft.quota.copy(block = it)) }
                                )
                            }
                        )
                    }

                    val used = vm.store.usedTokens(draft.quota)
                    val limit = draft.quota.limitTokens.coerceAtLeast(0)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (draft.quota.period == 0) "今天已用" else "本月已用",
                            fontSize = 12.sp,
                            color = xy.textDim
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            fmtTokens(used) + if (limit > 0) " / ${fmtTokens(limit.toLong())}" else "",
                            fontSize = 12.sp,
                            color = if (limit > 0 && used >= limit) Color(0xFFE5484D) else xy.text
                        )
                    }
                    if (limit > 0) {
                        LinearProgressIndicator(
                            progress = (used.toFloat() / limit.toFloat()).coerceIn(0f, 1f),
                            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                            color = if (used >= limit) Color(0xFFE5484D) else xy.primary,
                            trackColor = xy.surfaceAlt
                        )
                    }
                    Text(
                        "累计 ${fmtTokens(usage.totalTokens)} token · ${usage.requests} 次请求\n" +
                            "参考：一次中等长度的对话大约 2000~8000 token",
                        fontSize = 11.sp,
                        color = xy.textFaint,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            // ---------------- 联网搜索 ----------------
            item {
                SectionCard(
                    title = "联网搜索",
                    subtitle = "回答前先查网页，把摘要塞给模型；需要你自己的搜索 Key（博查 / Tavily 都有免费额度）"
                ) {
                    SettingRow(
                        title = "启用",
                        desc = if (draft.search.enabled) "每次提问先搜 ${draft.search.maxResults} 条网页" else "关着就是纯聊天",
                        trailing = {
                            Switch(
                                checked = draft.search.enabled,
                                onCheckedChange = { draft = draft.copy(search = draft.search.copy(enabled = it)) }
                            )
                        }
                    )

                    if (draft.search.enabled) {
                        Text("服务商", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = listOf(
                                "deepseek" to "DeepSeek 官方",
                                "bocha" to "博查",
                                "tavily" to "Tavily",
                                "searxng" to "SearXNG"
                            ),
                            selected = draft.search.provider,
                            onSelect = { draft = draft.copy(search = draft.search.copy(provider = it)) }
                        )

                        if (draft.search.provider == "deepseek") {
                            Text(
                                "用「接口」里那把 DeepSeek Key，不用另外申请；按 ¥5 / 1000 次计费",
                                fontSize = 11.sp,
                                color = xy.textFaint,
                                lineHeight = 16.sp,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        } else if (draft.search.provider != "searxng") {
                            XyField(
                                value = draft.search.apiKey,
                                onChange = { draft = draft.copy(search = draft.search.copy(apiKey = it)) },
                                label = "搜索 API Key",
                                placeholder = if (draft.search.provider == "bocha") "在 open.bochaai.com 申请" else "tvly-..."
                            )
                        }
                        XyField(
                            value = draft.search.baseUrl,
                            onChange = { draft = draft.copy(search = draft.search.copy(baseUrl = it)) },
                            label = "服务地址（可留空）",
                            placeholder = when (draft.search.provider) {
                                "tavily" -> "https://api.tavily.com/search"
                                "searxng" -> "https://你的实例/search"
                                "bocha" -> "https://api.bochaai.com/v1/web-search"
                                else -> "留空就用上面的接口地址"
                            }
                        )

                        Text("每次取几条", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.height(8.dp))
                        ChipRow(
                            items = listOf("3" to "3 条", "5" to "5 条", "8" to "8 条"),
                            selected = draft.search.maxResults.toString(),
                            onSelect = {
                                draft = draft.copy(search = draft.search.copy(maxResults = it.toIntOrNull() ?: 5))
                            }
                        )

                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            Button(
                                onClick = {
                                    if (searchTesting) return@Button
                                    searchTesting = true
                                    searchResult = null
                                    scope.launch {
                                        vm.testSearch(draft.search.copy(enabled = true), draft.api)
                                            .onSuccess { searchResult = it }
                                            .onFailure { searchResult = "失败：${it.message}" }
                                        searchTesting = false
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                enabled = !searchTesting
                            ) { Text(if (searchTesting) "搜索中…" else "测试搜索", fontSize = 13.sp) }
                            if (searchResult != null) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    searchResult!!,
                                    fontSize = 11.sp,
                                    color = if (searchResult!!.startsWith("失败")) Color(0xFFE5484D) else xy.textDim,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Text(
                            "DeepSeek 官方：和对话共用同一把 Key，¥5 / 1000 次\n" +
                                "博查：open.bochaai.com（中文结果，有免费额度）\n" +
                                "Tavily：tavily.com（英文为主，免费 1000 次/月）\n" +
                                "SearXNG：需要自己的实例，且允许 JSON 输出\n" +
                                "搜索失败不会挡住聊天，只会退化成不联网回答",
                            fontSize = 11.sp,
                            color = xy.textFaint,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }

            // ---------------- 外观 ----------------
            item {
                SectionCard(title = "外观") {
                    Text("主题", fontSize = 13.sp, color = xy.text)
                    Spacer(Modifier.height(8.dp))
                    ChipRow(
                        items = listOf("0" to "跟随系统", "1" to "浅色", "2" to "深色"),
                        selected = draft.appearance.themeMode.toString(),
                        onSelect = {
                            draft = draft.copy(appearance = draft.appearance.copy(themeMode = it.toIntOrNull() ?: 0))
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(xy.surfaceAlt)
                        ) {
                            val bg = rememberBitmap(draft.appearance.bgPath, 200)
                            if (bg != null) {
                                Image(
                                    bg,
                                    null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("聊天背景", fontSize = 13.sp, color = xy.text)
                            Text(
                                if (draft.appearance.bgPath.isNullOrBlank()) "还没选图" else "已设置",
                                fontSize = 11.sp,
                                color = xy.textFaint
                            )
                        }
                        TextButton(
                            onClick = {
                                pickBg.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            }
                        ) { Text("选择", fontSize = 12.sp) }
                        if (!draft.appearance.bgPath.isNullOrBlank()) {
                            TextButton(
                                onClick = {
                                    draft = draft.copy(appearance = draft.appearance.copy(bgPath = null))
                                }
                            ) { Text("移除", fontSize = 12.sp, color = xy.textDim) }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("背景不透明度", fontSize = 13.sp, color = xy.text)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "${(draft.appearance.bgOpacity * 100).toInt()}%",
                            fontSize = 13.sp,
                            color = xy.textDim
                        )
                    }
                    Slider(
                        value = draft.appearance.bgOpacity,
                        onValueChange = {
                            draft = draft.copy(appearance = draft.appearance.copy(bgOpacity = it))
                        },
                        valueRange = 0.05f..1f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "背景太花可以把不透明度调低，字会更好认",
                        fontSize = 11.sp,
                        color = xy.textFaint
                    )
                }
            }

            // ---------------- 分享 ----------------
            item {
                SectionCard(
                    title = "分享角色卡",
                    subtitle = "把 TA 的名字、头像、人设、开场白、示例、参数连 API Key 一起打包成一段文本，发微信/QQ 都行"
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("带上头像", fontSize = 13.sp, color = xy.text)
                            Text("会让分享文本长一点点", fontSize = 11.sp, color = xy.textFaint)
                        }
                        Switch(checked = includeAvatar, onCheckedChange = { includeAvatar = it })
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("带上背景图", fontSize = 13.sp, color = xy.text)
                            Text("文本会变得很长，可能发不出去", fontSize = 11.sp, color = Color(0xFFE08A00))
                        }
                        Switch(checked = includeBg, onCheckedChange = { includeBg = it })
                    }

                    Row(modifier = Modifier.padding(top = 10.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    val payload = withContext(Dispatchers.IO) {
                                        CardCodec.encode(ctx, draft, active, includeAvatar, includeBg)
                                    }
                                    payloadLen = payload.length
                                    val text = CardCodec.shareText(active, draft.api.apiKey, payload)
                                    val intent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, text)
                                    }
                                    ctx.startActivity(Intent.createChooser(intent, "分享角色卡"))
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("分享出去", fontSize = 13.sp) }
                        Spacer(Modifier.width(10.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    val payload = withContext(Dispatchers.IO) {
                                        CardCodec.encode(ctx, draft, active, includeAvatar, includeBg)
                                    }
                                    payloadLen = payload.length
                                    clipboard.setText(
                                        AnnotatedString(CardCodec.shareText(active, draft.api.apiKey, payload))
                                    )
                                    toast("角色卡已复制，直接发出去就行")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) { Text("复制文本", fontSize = 13.sp) }
                    }
                    if (payloadLen > 0) {
                        Text(
                            "这张卡约 $payloadLen 个字符" + if (payloadLen > 6000) "（有点长，微信可能会折叠，建议关掉头像或背景）" else "",
                            fontSize = 11.sp,
                            color = if (payloadLen > 6000) Color(0xFFE08A00) else xy.textFaint,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Divider(color = xy.line, modifier = Modifier.padding(vertical = 12.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("导入别人的角色卡", fontSize = 13.sp, color = xy.text)
                            Text(
                                "朋友把文本发给你，粘贴进来就能一键还原",
                                fontSize = 11.sp,
                                color = xy.textFaint
                            )
                        }
                        TextButton(onClick = { showImport = true }) { Text("导入", fontSize = 13.sp) }
                    }
                }
            }

            item {
                SectionCard(title = "关于") {
                    SettingRow(
                        title = "版本",
                        desc = "絮语 v1.4 · versionCode 5"
                    )
                    SettingRow(
                        title = "免责声明",
                        desc = "点开看全文",
                        clickable = true,
                        onClick = { showDisclaimer = true }
                    )
                    Text(
                        "聊天记录、API Key、角色卡只存在本机；没有服务器、没有埋点。",
                        fontSize = 11.sp,
                        color = xy.textFaint,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }

            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    // 示例对话编辑
    editExample?.let { index ->
        ExampleDialog(
            title = if (index < 0) "添加示例对话" else "编辑示例对话",
            initial = if (index >= 0) active.examples.getOrNull(index) ?: Example() else Example(),
            personaName = active.name,
            onDismiss = { editExample = null },
            onSave = { ex ->
                val list = active.examples.toMutableList()
                if (index >= 0 && index < list.size) list[index] = ex else list.add(ex)
                draft = draft.withPersona(active.copy(examples = list))
                editExample = null
            }
        )
    }

    // 导入
    if (showImport) {
        ImportDialog(
            onDismiss = { showImport = false },
            onImport = { text ->
                scope.launch {
                    val r = vm.importCard(text)
                    r.onSuccess { s ->
                        draft = s
                        showImport = false
                        toast("已导入「${s.activeFor(s.mode).name}」")
                        vm.newChat()
                        onBack()
                    }.onFailure {
                        toast(it.message ?: "导入失败")
                    }
                }
            }
        )
    }

    if (showDisclaimer) {
        DisclaimerDialog(onDismiss = { showDisclaimer = false })
    }
}

@Composable
private fun XyField(    value: String,
    onChange: (String) -> Unit,
    label: String,
    placeholder: String = "",
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    visual: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null
) {
    val xy = LocalXy.current
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        FieldLabel(label)
        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = xy.textFaint, fontSize = 13.sp) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else minLines,
            textStyle = TextStyle(fontSize = 14.sp, color = xy.text, lineHeight = 20.sp),
            shape = RoundedCornerShape(12.dp),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboardType),
            visualTransformation = visual,
            trailingIcon = trailing,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = xy.primary,
                unfocusedBorderColor = xy.line,
                cursorColor = xy.primary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun ExampleDialog(
    title: String,
    initial: Example,
    personaName: String,
    onDismiss: () -> Unit,
    onSave: (Example) -> Unit
) {
    val xy = LocalXy.current
    var user by remember { mutableStateOf(initial.user) }
    var ai by remember { mutableStateOf(initial.ai) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontSize = 16.sp) },
        text = {
            Column {
                Text("用户说", fontSize = 12.sp, color = xy.textDim)
                OutlinedTextField(
                    value = user,
                    onValueChange = { user = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    minLines = 2,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(Modifier.height(10.dp))
                Text("$personaName 回", fontSize = 12.sp, color = xy.textDim)
                OutlinedTextField(
                    value = ai,
                    onValueChange = { ai = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    textStyle = TextStyle(fontSize = 13.sp),
                    minLines = 2,
                    shape = RoundedCornerShape(10.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(Example(user, ai)) }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun fmtTokens(n: Long): String = String.format(java.util.Locale.US, "%,d", n)

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String) -> Unit) {
    val xy = LocalXy.current
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入角色卡", fontSize = 16.sp) },
        text = {
            Column {
                Text(
                    "把朋友发来的那一整段文本粘贴进来（含 xuyu:// 开头那串也没关系）",
                    fontSize = 12.sp,
                    color = xy.textDim
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    textStyle = TextStyle(fontSize = 12.sp),
                    minLines = 6,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    placeholder = { Text("粘贴到这里", fontSize = 12.sp, color = xy.textFaint) }
                )
                Row(modifier = Modifier.padding(top = 8.dp)) {
                    TextButton(
                        onClick = {
                            val c = clipboard.getText()?.text
                            if (!c.isNullOrBlank()) text = c
                        },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) { Text("从剪贴板粘贴", fontSize = 12.sp) }
                }
                Text(
                    "导入会覆盖当前的角色设置和接口设置",
                    fontSize = 11.sp,
                    color = Color(0xFFE08A00)
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(text) },
                enabled = text.isNotBlank()
            ) { Text("导入") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 把改动写回当前模式下的这张卡 */
private fun Settings.withPersona(p: Persona): Settings =
    if (mode == Settings.MODE_RP) {
        copy(rpPersonas = rpPersonas.map { if (it.id == p.id) p else it })
    } else {
        copy(personas = listOf(p), activePersonaId = p.id)
    }

@Composable
private fun PersonaChip(persona: Persona, selected: Boolean, onClick: () -> Unit) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) xy.primary else xy.surfaceAlt)
            .clickable { onClick() }
            .padding(start = 4.dp, end = 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(persona.avatarPath, persona.name, 22.dp)
        Spacer(Modifier.width(6.dp))
        Text(
            persona.name,
            fontSize = 12.sp,
            color = if (selected) xy.onPrimary else xy.textDim,
            maxLines = 1
        )
    }
}
