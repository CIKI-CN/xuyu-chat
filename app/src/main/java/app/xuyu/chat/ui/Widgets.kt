package app.xuyu.chat.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.xuyu.chat.util.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun rememberBitmap(path: String?, maxSide: Int = 0): ImageBitmap? {
    val state by produceState<ImageBitmap?>(initialValue = null, path, maxSide) {
        value = if (path.isNullOrBlank()) null else withContext(Dispatchers.IO) {
            Images.decodeFile(path, maxSide)?.asImageBitmap()
        }
    }
    return state
}

@Composable
fun Avatar(path: String?, name: String, size: Dp) {
    val bmp = rememberBitmap(path, 256)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF6E86FF), Color(0xFF9AAEFF))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text(
                text = name.take(1).ifBlank { "絮" },
                color = Color.White,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SectionCard(title: String, subtitle: String? = null, content: @Composable () -> Unit) {
    val xy = LocalXy.current
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 7.dp)) {
        Text(
            title,
            color = xy.textDim,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 6.dp, bottom = 6.dp)
        )
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = xy.surface,
            border = BorderStroke(1.dp, xy.line),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                if (subtitle != null) {
                    Text(subtitle, color = xy.textFaint, fontSize = 12.sp)
                    Spacer(Modifier.height(10.dp))
                }
                content()
            }
        }
    }
}

@Composable
fun FieldLabel(text: String, hint: String? = null) {
    val xy = LocalXy.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = xy.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        if (hint != null) {
            Spacer(Modifier.width(6.dp))
            Text(hint, color = xy.textFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun SettingRow(
    title: String,
    desc: String? = null,
    clickable: Boolean = false,
    onClick: () -> Unit = {},
    trailing: @Composable (() -> Unit)? = null
) {
    val xy = LocalXy.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = clickable, onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = xy.text, fontSize = 14.sp)
            if (!desc.isNullOrBlank()) {
                Text(desc, color = xy.textFaint, fontSize = 11.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        if (trailing != null) trailing()
    }
}

@Composable
fun XyChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val xy = LocalXy.current
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) xy.primary else xy.surfaceAlt,
        border = if (selected) null else BorderStroke(1.dp, xy.line),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable { onClick() }
    ) {
        Text(
            text,
            color = if (selected) xy.onPrimary else xy.textDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun ChipRow(items: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Column {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { (value, label) ->
                    XyChip(text = label, selected = selected == value) { onSelect(value) }
                }
            }
        }
    }
}
