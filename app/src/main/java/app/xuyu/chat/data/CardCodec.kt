package app.xuyu.chat.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import app.xuyu.chat.util.Images
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.Deflater
import java.util.zip.Inflater

/** 解出来的一张卡 */
data class CardPackage(
    val persona: Persona,
    val api: ApiConfig,
    val appearance: Appearance
)

/**
 * 「角色卡 + API Key」的编解码。
 *
 * 产物是一段可以复制/微信发送的文本：
 *   xuyu://import?d=<base64url(deflate(json))>
 * 对方在 App 里粘贴即可还原名字、头像、人设、示例对话、参数、API Key、背景与主题。
 */
object CardCodec {

    const val PREFIX = "xuyu://import?d="
    private const val VERSION = 2

    private fun imagesDir(ctx: Context): File = File(ctx.filesDir, "xuyu/images").apply { mkdirs() }

    fun encode(
        ctx: Context,
        settings: Settings,
        persona: Persona,
        includeAvatar: Boolean,
        includeBg: Boolean
    ): String {
        val root = JSONObject()
        root.put("v", VERSION)
        root.put("p", persona.copy(avatarPath = null).toJson())
        root.put("api", settings.api.toJson())
        root.put("ap", settings.appearance.copy(bgPath = null).toJson())

        if (includeAvatar) {
            Images.decodeFile(persona.avatarPath, 200)?.let { bmp ->
                root.put("av", Images.base64Thumb(bmp, 88, 52))
                bmp.recycle()
            }
        }
        if (includeBg) {
            Images.decodeFile(settings.appearance.bgPath, 1600)?.let { bmp ->
                root.put("bg", Images.base64Thumb(bmp, 900, 58))
                bmp.recycle()
            }
        }

        val raw = root.toString().toByteArray(Charsets.UTF_8)
        val out = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        try {
            deflater.setInput(raw)
            deflater.finish()
            val buf = ByteArray(16 * 1024)
            while (!deflater.finished()) {
                val n = deflater.deflate(buf)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        } finally {
            deflater.end()
        }
        val b64 = Base64.encodeToString(out.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        return PREFIX + b64
    }

    fun shareText(persona: Persona, apiKey: String, payload: String): String {
        val sb = StringBuilder()
        sb.append("「").append(persona.name).append("」的角色卡")
        if (persona.tagline.isNotBlank()) sb.append(" —— ").append(persona.tagline)
        sb.append("\n\n")
        sb.append("在「絮语」App 里打开 设置 → 导入角色卡，把下面这一整段原样粘贴进去就好，")
        sb.append("名字、头像、人设、场景、开场白、示例对话和 API Key 都会一起还原。\n\n")
        if (apiKey.isNotBlank()) {
            sb.append("（这张卡里带了 API Key，可以直接开聊；如果不想共享额度，去平台重置一下就行）\n\n")
        }
        sb.append("---------- 以下整段复制 ----------\n")
        sb.append(payload)
        return sb.toString()
    }

    fun decode(ctx: Context, text: String): CardPackage {
        val payload = extractPayload(text) ?: throw IllegalArgumentException("没有找到角色卡内容")
        val compressed = try {
            Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
        } catch (e: Exception) {
            throw IllegalArgumentException("角色卡内容损坏（base64 解析失败）")
        }
        val raw = try {
            inflate(compressed)
        } catch (e: Exception) {
            throw IllegalArgumentException("角色卡内容不完整，可能是复制时被截断了")
        }
        val root = try {
            JSONObject(String(raw, Charsets.UTF_8))
        } catch (e: Exception) {
            throw IllegalArgumentException("角色卡格式不对")
        }

        var persona = Persona.fromJson(root.optJSONObject("p"))
        val api = ApiConfig.fromJson(root.optJSONObject("api"))
        var appearance = Appearance.fromJson(root.optJSONObject("ap"))

        root.optString("av").takeIf { it.isNotBlank() }?.let { b64 ->
            val f = File(imagesDir(ctx), "avatar_${System.currentTimeMillis()}.jpg")
            if (Images.saveBase64(b64, f)) persona = persona.copy(avatarPath = f.absolutePath)
        }
        root.optString("bg").takeIf { it.isNotBlank() }?.let { b64 ->
            val f = File(imagesDir(ctx), "bg_${System.currentTimeMillis()}.jpg")
            if (Images.saveBase64(b64, f)) appearance = appearance.copy(bgPath = f.absolutePath)
        }
        return CardPackage(persona = persona, api = api, appearance = appearance)
    }

    private fun inflate(data: ByteArray): ByteArray {
        val inflater = Inflater(true)
        try {
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(16 * 1024)
            while (!inflater.finished()) {
                val n = inflater.inflate(buf)
                if (n == 0) {
                    throw IllegalStateException("truncated")
                } else {
                    out.write(buf, 0, n)
                }
            }
            return out.toByteArray()
        } finally {
            inflater.end()
        }
    }

    /** 从「粘贴的一大段文本」里把 payload 抠出来 */
    fun extractPayload(text: String): String? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val prefixAt = t.indexOf(PREFIX)
        if (prefixAt >= 0) {
            val rest = t.substring(prefixAt + PREFIX.length).takeWhile { isValidChar(it) }
            if (rest.length > 20) return rest
        }
        val idx = t.indexOf("d=")
        if (idx >= 0) {
            val rest = t.substring(idx + 2).takeWhile { isValidChar(it) }
            if (rest.length > 20) return rest
        }
        if (t.length > 20 && t.all { isValidChar(it) }) return t
        return null
    }

    private fun isValidChar(c: Char): Boolean =
        c.isLetterOrDigit() || c == '-' || c == '_'

    fun payloadFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri: Uri = intent.data ?: return null
                val d = uri.getQueryParameter("d")
                if (!d.isNullOrBlank()) return d
                return extractPayload(intent.dataString.orEmpty())
            }
            Intent.ACTION_SEND -> {
                val s = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return null
                return extractPayload(s)
            }
        }
        return null
    }
}
