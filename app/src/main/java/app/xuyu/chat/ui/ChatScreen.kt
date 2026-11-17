package app.xuyu.chat.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.ChatViewModel
import app.xuyu.chat.data.Appearance
import app.xuyu.chat.data.Message
import app.xuyu.chat.data.Persona
import app.xuyu.chat.data.Session
import app.xuyu.chat.data.Settings
import app.xuyu.chat.data.Source
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, onOpenSettings: () -> Unit, onSwitchMode: () -> Unit) {
    val settings by vm.store.settings.collectAsState()
    val allSessions by vm.store.sessions.collectAsState()
    val activeId by vm.store.activeId.collectAsState()
    val streamAll by vm.stream.collectAsState()

    val xy = LocalXy.current
    val mode = settings.mode.ifBlank { Settings.MODE_NORMAL }
    val sessions = remember(allSessions, mode) {
        allSessions.filter { it.mode == mode }.sortedByDescending { it.updatedAt }
    }
    val appearance = settings.appearance
    val session = sessions.firstOrNull { it.id == activeId } ?: sessions.firstOrNull()
    val persona = vm.personaFor(session, mode)
    val messages = session?.messages.orEmpty()
    // 只显示当前会话的流式消息，切走了就别串台
    val stream = streamAll?.takeIf { it.sessionId == session?.id }
    val usage by vm.store.usage.collectAsState()
    val usedTokens = remember(usage, settings.quota.period) { vm.store.usedTokens(settings.quota) }
    val noKey = settings.api.apiKey.isBlank()
    val quotaBlocked = settings.quota.enabled && settings.quota.block &&
        settings.quota.limitTokens > 0 && usedTokens >= settings.quota.limitTokens
    val quotaWarn = settings.quota.enabled && !settings.quota.block &&
        settings.quota.limitTokens > 0 && usedTokens >= settings.quota.limitTokens
    val model = persona.modelOverride.ifBlank { settings.api.model }
    val hasBg = !appearance.bgPath.isNullOrBlank()

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val ctx = LocalContext.current

    var input by rememberSaveable(session?.id) { mutableStateOf("") }
    var menuId by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<Session?>(null) }
    var editing by remember { mutableStateOf<Message?>(null) }

    fun sendNow() {
        val t = input.trim()
        if (t.isEmpty() || vm.busy || quotaBlocked) return
        input = ""
        vm.send(t)
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            DrawerContent(
                sessions = sessions,
                activeId = activeId,
                persona = persona,
                mode = mode,
                onNew = {
                    vm.newChat()
                    scope.launch { drawer.close() }
                },
                onSelect = {
                    vm.selectSession(it)
                    scope.launch { drawer.close() }
                },
                onDelete = { confirmDelete = it },
                onClear = {
                    vm.clearCurrent()
                    scope.launch { drawer.close() }
                },
                onSettings = {
                    scope.launch { drawer.close() }
                    onOpenSettings()
                },
                onSwitchMode = {
                    scope.launch { drawer.close() }
                    onSwitchMode()
                },
                onExport = {
                    scope.launch {
                        drawer.close()
                        if (session != null) {
                            val text = vm.exportMarkdown(session, persona)
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, text)
                            }
                            ctx.startActivity(Intent.createChooser(intent, "导出对话"))
                        }
                    }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            BackgroundLayer(appearance, xy)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
            ) {
                ChatHeader(
                    persona = persona,
                    model = model,
                    hasBg = hasBg,
                    onMenu = { scope.launch { drawer.open() } },
                    onNew = { vm.newChat() },
                    onSettings = onOpenSettings
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (messages.isEmpty() && stream == null) {
                        EmptyState(
                            persona = persona,
                            onPick = { text ->
                                input = text
                                sendNow()
                            }
                        )
                    } else {
                        androidx.compose.runtime.key(session?.id) {
                            MessageList(
                                messages = messages,
                                stream = stream,
                                persona = persona,
                                menuId = menuId,
                                onLongPress = { menuId = it.id },
                                onDismissMenu = { menuId = null },
                                onCopy = {
                                    clipboard.setText(AnnotatedString(it.content))
                                    menuId = null
                                },
                                onEdit = {
                                    editing = it
                                    menuId = null
                                },
                                onDelete = {
                                    vm.deleteMessage(it.id)
                                    menuId = null
                                },
                                onContinue = {
                                    vm.continueLast()
                                    menuId = null
                                },
                                onRewrite = {
                                    vm.rewriteLast()
                                    menuId = null
                                }
                            )
                        }
                    }
                }

                if (noKey) {
                    NoticeBar(
                        text = "还没有填 API Key，填完才能开聊",
                        action = "去设置",
                        onAction = onOpenSettings,
                        danger = false
                    )
                } else if (quotaBlocked) {
                    NoticeBar(
                        text = "已到「${if (settings.quota.period == 0) "今日" else "本月"}额度」，先歇会儿",
                        action = "调整",
                        onAction = onOpenSettings,
                        danger = true
                    )
                } else if (quotaWarn) {
                    NoticeBar(
                        text = "额度已经用超了，还能继续发",
                        action = "看看",
                        onAction = onOpenSettings,
                        danger = false
                    )
                }

                if (messages.none { it.role == "user" } && !noKey && stream == null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        TextButton(onClick = { vm.rerollGreeting() }, enabled = !vm.busy) {
                            Text("换个开场白", fontSize = 12.sp, color = xy.textDim)
                        }
                    }
                }

                InputBar(
                    value = input,
                    onValueChange = { input = it },
                    personaName = persona.name,
                    busy = vm.busy,
                    hasBg = hasBg,
                    blocked = quotaBlocked || noKey,
                    searchOn = settings.search.enabled,
                    onToggleSearch = {
                        val next = settings.search.copy(enabled = !settings.search.enabled)
                        vm.applySettings(settings.copy(search = next))
                        val ready = if (next.provider == "searxng") {
                            next.baseUrl.isNotBlank()
                        } else {
                            next.apiKey.isNotBlank()
                        }
                        Toast.makeText(
                            ctx,
                            when {
                                !next.enabled -> "联网搜索已关闭"
                                ready -> "联网搜索已开启"
                                else -> "开启了，但还没配搜索 Key"
                            },
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    onSend = { sendNow() },
                    onStop = { vm.stop() }
                )
            }
        }
    }

    confirmDelete?.let { s ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("删除这个对话？") },
            text = { Text("「${s.title}」和里面的消息都会被删掉，删了就找不回来了。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteSession(s.id)
                    confirmDelete = null
                }) { Text("删除", color = Color(0xFFE5484D)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("取消") }
            }
        )
    }

    editing?.let { msg ->
        EditMessageDialog(
            initial = msg.content,
            forUser = msg.role == "user",
            onDismiss = { editing = null },
            onSave = { text ->
                vm.editMessage(msg.id, text)
                editing = null
            }
        )
    }
}

@Composable
private fun EditMessageDialog(
    initial: String,
    forUser: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (forUser) "改一下我说的" else "改一下这条回答", fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                textStyle = TextStyle(fontSize = 13.sp, lineHeight = 19.sp),
                minLines = 6,
                maxLines = 14,
                shape = RoundedCornerShape(10.dp)
            )
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun BackgroundLayer(appearance: Appearance, xy: XyColors) {
    val bmp = rememberBitmap(appearance.bgPath, 1600)
    Box(modifier = Modifier.fillMaxSize().background(xy.bg)) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                alpha = appearance.bgOpacity.coerceIn(0.05f, 1f)
            )
            Box(modifier = Modifier.fillMaxSize().background(xy.bg.copy(alpha = 0.18f)))
        }
    }
}

@Composable
private fun ChatHeader(
    persona: Persona,
    model: String,
    hasBg: Boolean,
    onMenu: () -> Unit,
    onNew: () -> Unit,
    onSettings: () -> Unit
) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(xy.bg.copy(alpha = if (hasBg) 0.55f else 1f))
            .height(58.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onMenu) {
            Icon(Icons.Filled.Menu, contentDescription = "会话列表", tint = xy.textDim)
        }
        Avatar(persona.avatarPath, persona.name, 34.dp)
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                persona.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = xy.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                persona.tagline.ifBlank { model.ifBlank { "AI" } },
                fontSize = 11.sp,
                color = xy.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onNew) {
            Icon(Icons.Filled.Add, contentDescription = "新对话", tint = xy.textDim)
        }
        IconButton(onClick = onSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "设置", tint = xy.textDim)
        }
    }
}

@Composable
private fun MessageList(
    messages: List<Message>,
    stream: ChatViewModel.Stream?,
    persona: Persona,
    menuId: String?,
    onLongPress: (Message) -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: (Message) -> Unit,
    onEdit: (Message) -> Unit,
    onDelete: (Message) -> Unit,
    onContinue: () -> Unit,
    onRewrite: () -> Unit
) {
    val lastAssistantId = messages.lastOrNull { it.role == "assistant" && !it.error }?.id
    LazyColumn(
        reverseLayout = true,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 13.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (stream != null) {
            item(key = "streaming") {
                val st = stream
                AssistantView(
                    text = st.text,
                    reasoning = st.reasoning,
                    sources = emptyList(),
                    note = null,
                    searching = st.searching,
                    hint = st.hint,
                    streaming = true,
                    error = false,
                    persona = persona,
                    expanded = false,
                    onLongPress = {},
                    onDismissMenu = {},
                    onCopy = {},
                    onEdit = {},
                    onDelete = {},
                    onContinue = null,
                    onRewrite = null
                )
            }
        }
        items(messages.asReversed(), key = { it.id }) { msg ->
            if (msg.role == "user") {
                UserView(
                    msg = msg,
                    expanded = menuId == msg.id,
                    onLongPress = { onLongPress(msg) },
                    onDismissMenu = onDismissMenu,
                    onCopy = { onCopy(msg) },
                    onEdit = { onEdit(msg) },
                    onDelete = { onDelete(msg) }
                )
            } else {
                AssistantView(
                    text = msg.content,
                    reasoning = msg.reasoning,
                    sources = msg.sources,
                    note = msg.note,
                    searching = false,
                    hint = null,
                    streaming = false,
                    error = msg.error,
                    persona = persona,
                    expanded = menuId == msg.id,
                    onLongPress = { onLongPress(msg) },
                    onDismissMenu = onDismissMenu,
                    onCopy = { onCopy(msg) },
                    onEdit = { onEdit(msg) },
                    onDelete = { onDelete(msg) },
                    onContinue = if (msg.id == lastAssistantId) {
                        { onContinue() }
                    } else null,
                    onRewrite = if (msg.id == lastAssistantId) {
                        { onRewrite() }
                    } else null
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserView(
    msg: Message,
    expanded: Boolean,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val xy = LocalXy.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box {
            Surface(
                color = xy.userBubble,
                shape = RoundedCornerShape(18.dp, 18.dp, 5.dp, 18.dp),
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .combinedClickable(onClick = {}, onLongClick = onLongPress)
            ) {
                Text(
                    text = msg.content,
                    color = xy.userText,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
            MessageMenu(
                expanded = expanded,
                onDismiss = onDismissMenu,
                onCopy = onCopy,
                onEdit = onEdit,
                onContinue = null,
                onRewrite = null,
                onDelete = onDelete
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantView(
    text: String,
    reasoning: String,
    sources: List<Source>,
    note: String?,
    searching: Boolean,
    hint: String?,
    streaming: Boolean,
    error: Boolean,
    persona: Persona,
    expanded: Boolean,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onContinue: (() -> Unit)?,
    onRewrite: (() -> Unit)?
) {
    val xy = LocalXy.current
    val shown = if (streaming) TypewriterText(text, true) else text
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Avatar(persona.avatarPath, persona.name, 28.dp)
        Spacer(Modifier.width(9.dp))
        Box(modifier = Modifier.weight(1f)) {
            Column {
                if (reasoning.isNotBlank()) {
                    ReasoningBlock(reasoning, streaming)
                    Spacer(Modifier.height(4.dp))
                }
                if (streaming && searching && text.isBlank()) {
                    Text("正在联网搜索…", color = xy.textDim, fontSize = 13.sp)
                } else if (streaming && text.isBlank() && hint != null) {
                    Text(hint, color = xy.textDim, fontSize = 13.sp)
                } else if (streaming && text.isBlank()) {
                    Text("· · ·", color = xy.textFaint, fontSize = 15.sp, letterSpacing = 2.sp)
                } else {
                    MarkdownText(
                        text = shown,
                        color = if (error) Color(0xFFE5484D) else xy.text,
                        codeBg = xy.codeBg,
                        modifier = Modifier.combinedClickable(
                            enabled = !streaming,
                            onClick = {},
                            onLongClick = onLongPress
                        )
                    )
                }
                if (!note.isNullOrBlank()) {
                    Text(
                        note,
                        color = xy.textFaint,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
                if (sources.isNotEmpty()) {
                    SourcesBlock(sources)
                }
                if (!streaming && !error && text.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "AI 生成",
                        fontSize = 9.sp,
                        color = xy.textFaint,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(xy.surfaceAlt)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            MessageMenu(
                expanded = expanded,
                onDismiss = onDismissMenu,
                onCopy = onCopy,
                onEdit = onEdit,
                onContinue = onContinue,
                onRewrite = onRewrite,
                onDelete = onDelete
            )
        }
    }
}

@Composable
private fun SourcesBlock(sources: List<Source>) {
    val xy = LocalXy.current
    val uriHandler = LocalUriHandler.current
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(xy.surfaceAlt)
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            if (open) "收起参考来源" else "参考了 ${sources.size} 篇网页（点开看）",
            color = xy.textDim,
            fontSize = 11.sp
        )
        if (open) {
            sources.forEachIndexed { index, s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                        .clickable(enabled = s.url.isNotBlank()) {
                            try {
                                uriHandler.openUri(s.url)
                            } catch (_: Exception) {
                            }
                        }
                ) {
                    Text("[${index + 1}]", color = xy.textFaint, fontSize = 10.sp)
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(
                            s.title.ifBlank { s.url },
                            color = xy.text,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (s.url.isNotBlank()) {
                            Text(
                                s.site.ifBlank { hostOf(s.url) },
                                color = xy.textFaint,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun hostOf(url: String): String = try {
    java.net.URI(url).host ?: url
} catch (_: Exception) {
    url
}

@Composable
private fun ReasoningBlock(text: String, streaming: Boolean) {
    val xy = LocalXy.current
    var open by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(xy.reasoningBg)
            .clickable { open = !open }
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Text(
            text = if (streaming) "正在思考…（点开看）" else "已深度思考（点开看）",
            color = xy.textDim,
            fontSize = 11.sp
        )
        if (open) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = text,
                color = xy.textDim,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun MessageMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onCopy: () -> Unit,
    onEdit: () -> Unit,
    onContinue: (() -> Unit)?,
    onRewrite: (() -> Unit)?,
    onDelete: () -> Unit
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(text = { Text("复制") }, onClick = onCopy)
        DropdownMenuItem(text = { Text("编辑") }, onClick = onEdit)
        if (onContinue != null) {
            DropdownMenuItem(text = { Text("继续写") }, onClick = onContinue)
        }
        if (onRewrite != null) {
            DropdownMenuItem(text = { Text("重写") }, onClick = onRewrite)
        }
        DropdownMenuItem(
            text = { Text("删除", color = Color(0xFFE5484D)) },
            onClick = onDelete
        )
    }
}

@Composable
private fun TypewriterText(text: String, streaming: Boolean): String {
    var shown by remember { mutableIntStateOf(0) }
    LaunchedEffect(text, streaming) {
        if (shown > text.length) shown = text.length
        while (shown < text.length) {
            val lag = text.length - shown
            val step = when {
                !streaming -> 60
                lag > 600 -> 14
                lag > 150 -> 6
                else -> 3
            }
            shown = minOf(text.length, shown + step)
            delay(if (streaming) 16L else 4L)
        }
    }
    return text.substring(0, minOf(shown, text.length))
}

@Composable
private fun EmptyState(persona: Persona, onPick: (String) -> Unit) {
    val xy = LocalXy.current
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Avatar(persona.avatarPath, persona.name, 82.dp)
        Spacer(Modifier.height(16.dp))
        Text(persona.name, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
        if (persona.tagline.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                persona.tagline,
                fontSize = 13.sp,
                color = xy.textFaint,
                textAlign = TextAlign.Center
            )
        }
        Spacer(Modifier.height(22.dp))
        Text(
            persona.greeting.replace("\n", " "),
            fontSize = 14.sp,
            color = xy.textDim,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(26.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("今天有点累", "讲个冷笑话", "帮我想个周末计划").forEach { t ->
                Surface(
                    shape = RoundedCornerShape(50),
                    color = xy.surface,
                    border = BorderStroke(1.dp, xy.line),
                    modifier = Modifier.clickable { onPick(t) }
                ) {
                    Text(
                        t,
                        fontSize = 13.sp,
                        color = xy.textDim,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBar(text: String, action: String?, onAction: () -> Unit, danger: Boolean) {
    val xy = LocalXy.current
    val bg = when {
        danger && xy.dark -> Color(0xFF3A1E1E)
        danger -> Color(0xFFFFECEC)
        xy.dark -> Color(0xFF332C1A)
        else -> Color(0xFFFFF4E1)
    }
    val fg = when {
        danger && xy.dark -> Color(0xFFFFA8A8)
        danger -> Color(0xFFB3261E)
        xy.dark -> Color(0xFFE8C583)
        else -> Color(0xFF8A5A00)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable { onAction() }
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 11.sp, color = fg, modifier = Modifier.weight(1f))
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            Text(action, fontSize = 11.sp, color = fg, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    personaName: String,
    busy: Boolean,
    hasBg: Boolean,
    blocked: Boolean,
    searchOn: Boolean,
    onToggleSearch: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    val xy = LocalXy.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(xy.bg.copy(alpha = if (hasBg) 0.6f else 1f))
            .navigationBarsPadding()
            .imePadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = xy.surface,
            border = BorderStroke(1.dp, xy.line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (searchOn) xy.primary.copy(alpha = 0.12f) else Color.Transparent)
                        .clickable { onToggleSearch() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "联网搜索",
                        tint = if (searchOn) xy.primary else xy.textFaint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .heightIn(max = 132.dp),
                    textStyle = TextStyle(color = xy.text, fontSize = 15.sp, lineHeight = 21.sp),
                    cursorBrush = SolidColor(xy.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (!busy && value.isNotBlank()) onSend()
                    }),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    "和「$personaName」说点什么…",
                                    color = xy.textFaint,
                                    fontSize = 15.sp,
                                    modifier = Modifier.padding(top = 1.dp)
                                )
                            }
                            inner()
                        }
                    }
                )
                SendButton(busy = busy, enabled = value.isNotBlank() && !blocked, onSend = onSend, onStop = onStop)
            }
        }
    }
}

@Composable
private fun SendButton(busy: Boolean, enabled: Boolean, onSend: () -> Unit, onStop: () -> Unit) {
    val xy = LocalXy.current
    val active = busy || enabled
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(if (active) xy.primary else xy.surfaceAlt)
            .clickable(enabled = active) { if (busy) onStop() else onSend() },
        contentAlignment = Alignment.Center
    ) {
        if (busy) {
            Box(
                modifier = Modifier
                    .size(11.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(xy.onPrimary)
            )
        } else {
            Icon(
                Icons.Filled.Send,
                contentDescription = "发送",
                tint = if (enabled) xy.onPrimary else xy.textFaint,
                modifier = Modifier.size(17.dp)
            )
        }
    }
}

@Composable
private fun DrawerContent(
    sessions: List<Session>,
    activeId: String?,
    persona: Persona,
    mode: String,
    onNew: () -> Unit,
    onSelect: (String) -> Unit,
    onDelete: (Session) -> Unit,
    onClear: () -> Unit,
    onSettings: () -> Unit,
    onSwitchMode: () -> Unit,
    onExport: () -> Unit
) {
    val xy = LocalXy.current
    ModalDrawerSheet(
        drawerContainerColor = xy.surface,
        modifier = Modifier.width(302.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Vertical))
        ) {
            Row(
                modifier = Modifier.padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(persona.avatarPath, persona.name, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        persona.name,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = xy.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        if (mode == Settings.MODE_RP) "角色扮演模式" else "日常聊天模式",
                        fontSize = 11.sp,
                        color = if (mode == Settings.MODE_RP) Color(0xFFB05CD6) else xy.primary,
                        maxLines = 1
                    )
                }
            }
            Text(
                persona.tagline,
                fontSize = 11.sp,
                color = xy.textFaint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 74.dp, end = 18.dp, bottom = 10.dp)
            )

            Button(
                onClick = onNew,
                shape = RoundedCornerShape(13.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新对话", fontSize = 14.sp)
            }

            Text(
                "最近对话",
                fontSize = 11.sp,
                color = xy.textFaint,
                modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 6.dp)
            )

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(sessions, key = { it.id }) { s ->
                    val selected = s.id == activeId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) xy.surfaceAlt else Color.Transparent)
                            .clickable { onSelect(s.id) }
                            .padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                s.title,
                                fontSize = 14.sp,
                                color = xy.text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                s.preview(),
                                fontSize = 11.sp,
                                color = xy.textFaint,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        IconButton(onClick = { onDelete(s) }, modifier = Modifier.size(30.dp)) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "删除对话",
                                tint = xy.textFaint,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }

            Divider(color = xy.line)

            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                DrawerAction("导出这段对话", onExport)
                DrawerAction("切换模式", onSwitchMode)
                DrawerAction("清空当前对话", onClear)
                DrawerAction("设置 / 角色卡", onSettings)
            }
        }
    }
}

@Composable
private fun DrawerAction(text: String, onClick: () -> Unit) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 14.sp, color = xy.textDim)
    }
}
