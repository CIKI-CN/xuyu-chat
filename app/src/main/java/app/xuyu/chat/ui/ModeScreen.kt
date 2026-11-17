package app.xuyu.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.data.Settings

/**
 * 第一次进入 / 主动切换时选模式。两种模式的会话与角色卡完全分开。
 */
@Composable
fun ModeScreen(current: String, onPick: (String) -> Unit, onCancel: (() -> Unit)?) {
    val xy = LocalXy.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(xy.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(horizontal = 22.dp)
    ) {
        Spacer(Modifier.height(46.dp))
        Text("选一个模式", fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
        Spacer(Modifier.height(8.dp))
        Text(
            "两种模式的聊天记录和角色卡完全分开存放，随时可以在设置里切换。",
            fontSize = 12.5.sp,
            lineHeight = 19.sp,
            color = xy.textFaint
        )
        Spacer(Modifier.height(28.dp))

        ModeCard(
            icon = Icons.Filled.Person,
            title = "日常聊天",
            sub = "就是现在这样",
            points = listOf("一张人设卡，简单直接", "联网搜索、余额、限额都有", "适合日常问答和闲聊"),
            accent = Color(0xFF4D6BFE),
            selected = current == Settings.MODE_NORMAL,
            onClick = { onPick(Settings.MODE_NORMAL) }
        )
        Spacer(Modifier.height(14.dp))
        ModeCard(
            icon = Icons.Filled.Face,
            title = "角色扮演",
            sub = "深度模式",
            points = listOf("多个角色卡，随时切换", "场景 / 我的角色 / 说话风格", "长记忆、继续写、换开场白"),
            accent = Color(0xFFB05CD6),
            selected = current == Settings.MODE_RP,
            onClick = { onPick(Settings.MODE_RP) }
        )

        Spacer(Modifier.weight(1f))

        if (onCancel != null) {
            Text(
                "取消",
                fontSize = 13.sp,
                color = xy.textDim,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onCancel() }
                    .padding(vertical = 12.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
        Spacer(Modifier.height(18.dp))
    }
}

@Composable
private fun ModeCard(
    icon: ImageVector,
    title: String,
    sub: String,
    points: List<String>,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val xy = LocalXy.current
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) accent.copy(alpha = if (xy.dark) 0.16f else 0.07f) else xy.surface,
        border = BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) accent else xy.line),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(21.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
                    Text(sub, fontSize = 11.sp, color = xy.textFaint)
                }
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text("当前", fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(12.dp))
            points.forEach { p ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("·", color = accent, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
                    Text(p, fontSize = 12.sp, color = xy.textDim, lineHeight = 18.sp)
                }
            }
        }
    }
}
