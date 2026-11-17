package app.xuyu.chat.net

import org.json.JSONObject

/**
 * org.json 的 optString() 碰到 JSON null 会返回字符串 "null"。
 * DeepSeek 的流式 chunk 里 content / reasoning_content 经常是 null，
 * 直接 optString 就会把 "null" 当成正文拼进回答里（截图里那串 null 就是这么来的）。
 * 所有取字符串的地方统一走这里。
 */
internal fun JSONObject?.str(key: String, default: String = ""): String {
    if (this == null) return default
    val v = opt(key) ?: return default
    return if (v is String) v else default
}
