package app.xuyu.chat.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.data.Legal

/**
 * 首次启动 / 版本更新后的免责声明页。
 * 必须点「同意并继续」才能进；按返回键等于不同意，直接退出。
 */
@Composable
fun DisclaimerScreen(onAccept: () -> Unit, onExit: () -> Unit) {
    val xy = LocalXy.current
    BackHandler { onExit() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(xy.bg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 22.dp, bottom = 10.dp)) {
            Text("免责声明", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = xy.text)
            Spacer(Modifier.height(6.dp))
            Text("使用前请先读一下，点同意之后不会再打扰你", fontSize = 12.sp, color = xy.textFaint)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp)
        ) {
            Text(Legal.INTRO, fontSize = 13.sp, lineHeight = 21.sp, color = xy.textDim)
            Legal.SECTIONS.forEach { section ->
                Spacer(Modifier.height(16.dp))
                Text(
                    section.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = xy.text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    section.body,
                    fontSize = 13.sp,
                    lineHeight = 21.sp,
                    color = xy.textDim
                )
            }
            Spacer(Modifier.height(18.dp))
            Text(Legal.FOOTER, fontSize = 12.sp, lineHeight = 18.sp, color = xy.textFaint)
            Spacer(Modifier.height(24.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(xy.surface)
                .padding(start = 18.dp, end = 18.dp, top = 12.dp, bottom = 10.dp)
        ) {
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(14.dp)
            ) {
                Text("同意并继续", fontSize = 15.sp)
            }
            TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("不同意，退出", fontSize = 13.sp, color = xy.textDim)
            }
        }
    }
}

/** 设置里随时能翻看的版本 */
@Composable
fun DisclaimerDialog(onDismiss: () -> Unit) {
    val xy = LocalXy.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("免责声明", fontSize = 16.sp) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(Legal.INTRO, fontSize = 12.sp, lineHeight = 18.sp, color = xy.textDim)
                Legal.SECTIONS.forEach { section ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        section.title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = xy.text
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(section.body, fontSize = 12.sp, lineHeight = 18.sp, color = xy.textDim)
                }
                Spacer(Modifier.height(14.dp))
                Text(Legal.FOOTER, fontSize = 11.sp, color = xy.textFaint)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
}
