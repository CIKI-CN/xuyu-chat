package app.xuyu.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.data.Links

/**
 * 上手指南：怎么拿 Key、怎么充值、大概多少钱、报错怎么看。
 * 入口：设置 → 接口；首次启动（同意免责声明后）自动弹一次。
 */
@Composable
fun GuideScreen(onClose: () -> Unit) {
    val xy = LocalXy.current
    BackHandler { onClose() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(xy.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "关闭", tint = xy.textDim)
            }
            Column {
                Text(
                    "上手 · 拿到 Key 再开聊",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = xy.text
                )
                Text("建 Key → 充值 → 粘进来，三分钟", fontSize = 11.sp, color = xy.textFaint)
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
        ) {
            FlowDiagram()

            GuideSection("1. 创建 API Key") {
                Bullet("用你自己的 DeepSeek 账号登录开放平台")
                Bullet("左侧「API Keys」→ 创建，名字随便起")
                Bullet("Key 只显示一次，关掉窗口就再也看不到，当场复制")
                Bullet("丢了不用慌：删掉重建一把，再换到 App 里")
                Spacer(Modifier.height(10.dp))
                LinkButton("去创建 Key", Links.KEYS)
            }

            GuideSection("2. 充点钱") {
                Bullet("按量计费、先充后用；余额为 0 时请求会直接返回 402")
                Bullet("充值后一般即时到账，余额和用量都在开放平台看")
                Bullet("本应用不代收任何费用，也不经手你的钱")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LinkButton("去充值", Links.TOP_UP)
                    GhostLinkButton("看余额", Links.USAGE)
                }
            }

            GuideSection("3. 大概多少钱") {
                PriceRow("deepseek-flash", "输入 ¥1~2 / 百万", "输出 ¥4~8 / 百万")
                PriceRow("deepseek-v4-pro", "输入 ¥4.5~9 / 百万", "输出 ¥13.5~27 / 百万")
                PriceRow("联网搜索", "¥5 / 1000 次", "")
                Spacer(Modifier.height(6.dp))
                Bullet("区间是「空闲 ~ 高峰」：工作日 9-12、14-18 点为高峰，其余时段半价")
                Bullet("缓存命中时输入更便宜（flash 低至 ¥0.02 / 百万）")
                Bullet("一次中等长度的对话通常几厘到几分钱")
                Spacer(Modifier.height(10.dp))
                GhostLinkButton("看官方价格页", Links.PRICING)
            }

            GuideSection("4. 报错对照") {
                ErrorRow("401", "Key 无效或填错了", "重新复制一把，注意别带空格")
                ErrorRow("402", "余额不足", "去充值，充完直接用")
                ErrorRow("403", "没权限用这个模型", "换成 deepseek-flash 试试")
                ErrorRow("429", "请求太频繁 / 额度用尽", "等一会儿，或降低频率")
                ErrorRow("404", "地址不对", "检查 Base URL，别多写 /v1/chat")
            }

            GuideSection("5. Key 别乱贴") {
                Bullet("Key 等于你的钱包：别发群、别截图、别写进公开仓库")
                Bullet("本应用只把它存在本机私有目录，请求时才发给你配置的地址")
                Bullet("分享角色卡会连带 Key 一起发出去，分享前想清楚")
                Bullet("怀疑泄露就立刻去开放平台删掉重建")
            }

            Spacer(Modifier.height(28.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(xy.surface)
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
        ) {
            Button(
                onClick = onClose,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("开始使用", fontSize = 15.sp)
            }
            Text(
                "之后可以在 设置 → 接口 里再打开",
                fontSize = 10.sp,
                color = xy.textFaint,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
            )
        }
    }
}

@Composable
private fun FlowDiagram() {
    val xy = LocalXy.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(xy.surfaceAlt)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FlowStep("1", "登录", "开放平台", Modifier.weight(1f))
        Chevron()
        FlowStep("2", "建 Key", "复制保存", Modifier.weight(1f))
        Chevron()
        FlowStep("3", "充值", "按量扣费", Modifier.weight(1f))
        Chevron()
        FlowStep("4", "粘进来", "设置→接口", Modifier.weight(1f))
    }
}

@Composable
private fun FlowStep(no: String, title: String, sub: String, modifier: Modifier) {
    val xy = LocalXy.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(xy.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(no, color = xy.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Text(title, color = xy.text, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Text(sub, color = xy.textFaint, fontSize = 9.sp, maxLines = 1)
    }
}

@Composable
private fun Chevron() {
    val xy = LocalXy.current
    Text("›", color = xy.textFaint, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 1.dp))
}

@Composable
private fun GuideSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    val xy = LocalXy.current
    Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    val xy = LocalXy.current
    Row(modifier = Modifier.padding(vertical = 3.dp)) {
        Text("·", fontSize = 13.sp, color = xy.textFaint, modifier = Modifier.padding(end = 6.dp))
        Text(text, fontSize = 12.5.sp, lineHeight = 19.sp, color = xy.textDim)
    }
}

@Composable
private fun PriceRow(name: String, input: String, output: String) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, fontSize = 12.sp, color = xy.text, modifier = Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(input, fontSize = 11.sp, color = xy.textDim)
            if (output.isNotBlank()) Text(output, fontSize = 11.sp, color = xy.textDim)
        }
    }
}

@Composable
private fun ErrorRow(code: String, title: String, hint: String) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            code,
            fontSize = 11.sp,
            color = Color.White,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(if (code == "401" || code == "402") Color(0xFFE5484D) else xy.textFaint)
                .padding(horizontal = 5.dp, vertical = 2.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = xy.text)
            Text(hint, fontSize = 11.sp, color = xy.textFaint)
        }
    }
}

@Composable
private fun LinkButton(text: String, url: String) {
    val uri = LocalUriHandler.current
    Button(
        onClick = {
            try {
                uri.openUri(url)
            } catch (_: Exception) {
            }
        },
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}

@Composable
private fun GhostLinkButton(text: String, url: String) {
    val uri = LocalUriHandler.current
    OutlinedButton(
        onClick = {
            try {
                uri.openUri(url)
            } catch (_: Exception) {
            }
        },
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(text, fontSize = 12.sp)
    }
}
