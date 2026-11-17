package app.xuyu.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Path
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * 把自适应图标按「圆形遮罩」真实渲染一遍，再用字符画出来人眼检查形状/留白。
 * 产物：screenshots/icon.png、screenshots/icon-round.png
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class IconPreviewTest {

    private val ctx: Context get() = ApplicationProvider.getApplicationContext()
    private val size = 432

    private fun drawIcon(round: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.rgb(238, 241, 246))
        if (round) {
            val clip = Path().apply { addCircle(size / 2f, size / 2f, size * 36f / 108f, Path.Direction.CW) }
            canvas.clipPath(clip)
        }
        val bg = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_background)!!
        bg.setBounds(0, 0, size, size)
        bg.draw(canvas)
        val fg = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)!!
        fg.setBounds(0, 0, size, size)
        fg.draw(canvas)
        return bmp
    }

    @Test
    fun renderAndDescribe() {
        val composite = drawIcon(round = true)
        val out = File("E:\\91\\xuyu-chat\\screenshots").apply { mkdirs() }
        FileOutputStream(File(out, "icon-round.png")).use {
            composite.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        FileOutputStream(File(out, "icon.png")).use {
            drawIcon(round = false).compress(Bitmap.CompressFormat.PNG, 100, it)
        }

        // 前景单独画在绿底上，方便分辨「白色气泡」和「蓝色声波」
        val fgOnly = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(fgOnly)
        c.drawColor(Color.rgb(0, 200, 0))
        val fg = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_foreground)!!
        fg.setBounds(0, 0, size, size)
        fg.draw(c)

        val cols = 46
        val step = size.toFloat() / cols
        val sb = StringBuilder()
        var whiteCount = 0
        var minX = size; var maxX = 0; var minY = size; var maxY = 0

        for (row in 0 until cols) {
            for (col in 0 until cols) {
                val x = (( col + 0.5f) * step).toInt().coerceIn(0, size - 1)
                val y = ((row + 0.5f) * step).toInt().coerceIn(0, size - 1)
                val p = fgOnly.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                val ch = when {
                    r > 215 && g > 215 && b > 215 -> {
                        whiteCount++
                        if (x < minX) minX = x
                        if (x > maxX) maxX = x
                        if (y < minY) minY = y
                        if (y > maxY) maxY = y
                        '#'
                    }
                    b > r + 70 && b > 150 -> 'o'
                    g > r + 70 && g > b + 70 -> '.'
                    else -> '?'
                }
                sb.append(ch)
            }
            sb.append('\n')
        }
        println("ICON MAP")
        println(sb)

        // 1 格 = 1 个 108 单位，裁到气泡区域，看细节
        val x0 = 28; val x1 = 82; val y0 = 30; val y1 = 84
        val zoom = StringBuilder()
        var yy = y0
        while (yy < y1) {
            var xx = x0
            while (xx < x1) {
                val px = (xx * size / 108f).toInt().coerceIn(0, size - 1)
                val py = (yy * size / 108f).toInt().coerceIn(0, size - 1)
                val p = fgOnly.getPixel(px, py)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                zoom.append(
                    when {
                        r > 215 && g > 215 && b > 215 -> '#'
                        kotlin.math.abs(r - 77) < 26 && kotlin.math.abs(g - 107) < 26 && kotlin.math.abs(b - 254) < 26 -> 'O'
                        b > r + 70 && b > 150 -> 'o'
                        g > r + 70 && g > b + 70 -> '.'
                        else -> '?'
                    }
                )
                xx++
            }
            zoom.append('\n')
            yy++
        }
        println("BUBBLE ZOOM (x $x0..$x1, y $y0..$y1, 1 char = 1 unit)")
        println(zoom)

        // 缩到 48px（桌面图标大小）再看一眼还认不认得出
        val small = Bitmap.createScaledBitmap(composite, 48, 48, true)
        val smallMap = StringBuilder()
        for (y in 0 until 48) {
            for (x in 0 until 48) {
                val p = small.getPixel(x, y)
                val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
                val lum = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
                smallMap.append(
                    when {
                        lum > 225 -> '#'
                        lum > 170 -> '+'
                        lum > 120 -> '-'
                        lum > 80 -> '.'
                        else -> ' '
                    }
                )
            }
            smallMap.append('\n')
        }
        println("48px PREVIEW")
        println(smallMap)
        val scale = 108f / size
        println(
            "white bbox in 108-units: x=%.1f..%.1f  y=%.1f..%.1f  (safe zone 21..87)".format(
                minX * scale, maxX * scale, minY * scale, maxY * scale
            )
        )
        println("white coverage: %.1f%%".format(whiteCount * 100.0 / (cols * cols)))
        println("saved: " + File(out, "icon-round.png").absolutePath)
    }
}
