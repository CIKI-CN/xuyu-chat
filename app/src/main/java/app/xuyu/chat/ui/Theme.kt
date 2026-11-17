package app.xuyu.chat.ui

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

data class XyColors(
    val dark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceAlt: Color,
    val primary: Color,
    val onPrimary: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val userBubble: Color,
    val userText: Color,
    val line: Color,
    val codeBg: Color,
    val reasoningBg: Color
)

val LightXy = XyColors(
    dark = false,
    bg = Color(0xFFF6F8FC),
    surface = Color(0xFFFFFFFF),
    surfaceAlt = Color(0xFFEFF3FA),
    primary = Color(0xFF4D6BFE),
    onPrimary = Color.White,
    text = Color(0xFF1B1F27),
    textDim = Color(0xFF6B7280),
    textFaint = Color(0xFF9AA1AC),
    userBubble = Color(0xFFE7EEFF),
    userText = Color(0xFF14203A),
    line = Color(0xFFE3E8F1),
    codeBg = Color(0xFFF1F4F9),
    reasoningBg = Color(0xFFF3F5F9)
)

val DarkXy = XyColors(
    dark = true,
    bg = Color(0xFF0E1116),
    surface = Color(0xFF171B22),
    surfaceAlt = Color(0xFF212630),
    primary = Color(0xFF7C93FF),
    onPrimary = Color(0xFF0B1020),
    text = Color(0xFFE9ECF2),
    textDim = Color(0xFF9BA3B0),
    textFaint = Color(0xFF6E7684),
    userBubble = Color(0xFF26324D),
    userText = Color(0xFFE9EEFF),
    line = Color(0xFF262C36),
    codeBg = Color(0xFF1B2029),
    reasoningBg = Color(0xFF1A1F27)
)

val LocalXy = staticCompositionLocalOf { LightXy }

@Composable
fun XyTheme(themeMode: Int, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        1 -> false
        2 -> true
        else -> isSystemInDarkTheme()
    }
    val xy = if (dark) DarkXy else LightXy
    val scheme = if (dark) {
        darkColorScheme(
            primary = xy.primary,
            onPrimary = xy.onPrimary,
            background = xy.bg,
            onBackground = xy.text,
            surface = xy.surface,
            onSurface = xy.text,
            surfaceVariant = xy.surfaceAlt,
            onSurfaceVariant = xy.textDim,
            outline = xy.line
        )
    } else {
        lightColorScheme(
            primary = xy.primary,
            onPrimary = xy.onPrimary,
            background = xy.bg,
            onBackground = xy.text,
            surface = xy.surface,
            onSurface = xy.text,
            surfaceVariant = xy.surfaceAlt,
            onSurfaceVariant = xy.textDim,
            outline = xy.line
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    CompositionLocalProvider(LocalXy provides xy) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
