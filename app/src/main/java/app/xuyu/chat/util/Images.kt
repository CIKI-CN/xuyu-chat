package app.xuyu.chat.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Base64
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

object Images {

    /** 从相册选的图：按最长边缩放 + 按 EXIF 摆正方向 */
    fun decodePicked(ctx: Context, uri: Uri, maxSide: Int): Bitmap? {
        val bytes = try {
            ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        } ?: return null
        return try {
            decodeBytes(bytes, maxSide)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun savePicked(ctx: Context, uri: Uri, dest: File, maxSide: Int, quality: Int): Boolean {
        val bmp = decodePicked(ctx, uri, maxSide) ?: return false
        return try {
            dest.parentFile?.mkdirs()
            dest.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, quality, it) }
            bmp.recycle()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun decodeFile(path: String?, maxSide: Int = 0): Bitmap? {
        if (path.isNullOrBlank()) return null
        val f = File(path)
        if (!f.exists()) return null
        return try {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, opts)
            var sample = 1
            if (maxSide > 0) {
                while (maxOf(opts.outWidth, opts.outHeight) / (sample * 2) >= maxSide) sample *= 2
            }
            val o2 = BitmapFactory.Options().apply { inSampleSize = sample }
            val bmp = BitmapFactory.decodeFile(path, o2) ?: return null
            if (maxSide > 0) scaleTo(bmp, maxSide) else bmp
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    private fun decodeBytes(bytes: ByteArray, maxSide: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return scaleTo(rotateByExif(bmp, bytes), maxSide)
    }

    private fun rotateByExif(bmp: Bitmap, bytes: ByteArray): Bitmap {
        return try {
            val exif = ExifInterface(ByteArrayInputStream(bytes))
            val orientation =
                exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            val m = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    m.postRotate(90f)
                    m.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    m.postRotate(270f)
                    m.postScale(-1f, 1f)
                }
                else -> return bmp
            }
            val out = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (out !== bmp) bmp.recycle()
            out
        } catch (e: Exception) {
            bmp
        }
    }

    fun scaleTo(bmp: Bitmap, maxSide: Int): Bitmap {
        val long = maxOf(bmp.width, bmp.height)
        if (long <= maxSide) return bmp
        val ratio = maxSide.toFloat() / long
        val w = maxOf(1, (bmp.width * ratio).toInt())
        val h = maxOf(1, (bmp.height * ratio).toInt())
        val out = Bitmap.createScaledBitmap(bmp, w, h, true)
        if (out !== bmp) bmp.recycle()
        return out
    }

    fun base64Jpeg(bmp: Bitmap, maxSide: Int, quality: Int): String {
        val scaled = scaleTo(bmp, maxSide)
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        if (scaled !== bmp) scaled.recycle()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    /** 分享角色卡用：webp 比 jpeg 小一半，文本能短很多 */
    fun base64Thumb(bmp: Bitmap, maxSide: Int, quality: Int): String {
        val scaled = scaleTo(bmp, maxSide)
        val bos = ByteArrayOutputStream()
        val ok = try {
            scaled.compress(Bitmap.CompressFormat.WEBP, quality, bos)
        } catch (e: Exception) {
            false
        }
        if (!ok || bos.size() == 0) {
            bos.reset()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        }
        if (scaled !== bmp) scaled.recycle()
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    fun bitmapFromBase64(b64: String): Bitmap? = try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }

    fun saveBase64(b64: String, dest: File): Boolean = try {
        dest.parentFile?.mkdirs()
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        dest.writeBytes(bytes)
        true
    } catch (e: Exception) {
        false
    }
}
