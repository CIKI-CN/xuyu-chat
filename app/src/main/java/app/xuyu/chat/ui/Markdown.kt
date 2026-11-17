package app.xuyu.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private sealed interface MdBlock {
    data class Para(val text: String) : MdBlock
    data class Code(val lang: String, val code: String) : MdBlock
}

private fun parseBlocks(src: String): List<MdBlock> {
    val out = mutableListOf<MdBlock>()
    val parts = src.split("```")
    parts.forEachIndexed { index, raw ->
        if (index % 2 == 1) {
            val body = raw.trim('\n')
            val firstNl = body.indexOf('\n')
            val maybeLang = if (firstNl in 0..12) body.substring(0, firstNl).trim() else ""
            val lang = if (maybeLang.matches(Regex("[A-Za-z0-9+#._-]{0,12}"))) maybeLang else ""
            val code = if (lang.isNotEmpty() && firstNl >= 0) body.substring(firstNl + 1) else body
            out += MdBlock.Code(lang, code.trimEnd())
        } else {
            val t = raw.trim('\n')
            if (t.isNotBlank()) out += MdBlock.Para(t)
        }
    }
    return out
}

private fun inlineMd(src: String, codeBg: Color): AnnotatedString = buildAnnotatedString {
    var i = 0
    val plain = StringBuilder()
    fun flush() {
        if (plain.isNotEmpty()) {
            append(plain.toString())
            plain.clear()
        }
    }
    while (i < src.length) {
        if (src.startsWith("**", i)) {
            val end = src.indexOf("**", i + 2)
            if (end > i + 2) {
                flush()
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(src.substring(i + 2, end))
                }
                i = end + 2
                continue
            }
        }
        if (src[i] == '`') {
            val end = src.indexOf('`', i + 1)
            if (end > i + 1) {
                flush()
                withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = codeBg)
                ) {
                    append(src.substring(i + 1, end))
                }
                i = end + 1
                continue
            }
        }
        plain.append(src[i])
        i++
    }
    flush()
}

@Composable
fun MarkdownText(
    text: String,
    color: Color,
    codeBg: Color,
    modifier: Modifier = Modifier,
    fontSize: Int = 15
) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier) {
        blocks.forEachIndexed { idx, block ->
            when (block) {
                is MdBlock.Para -> {
                    Text(
                        text = remember(block.text, codeBg) { inlineMd(block.text, codeBg) },
                        color = color,
                        fontSize = fontSize.sp,
                        lineHeight = (fontSize + 8).sp,
                        modifier = Modifier.padding(top = if (idx == 0) 0.dp else 6.dp)
                    )
                }
                is MdBlock.Code -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 2.dp)
                            .background(codeBg, RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        if (block.lang.isNotEmpty()) {
                            Text(
                                block.lang,
                                color = color.copy(alpha = 0.5f),
                                fontSize = 10.sp,
                                modifier = Modifier.padding(bottom = 5.dp)
                            )
                        }
                        Text(
                            block.code,
                            color = color,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 19.sp,
                            modifier = Modifier.horizontalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}
