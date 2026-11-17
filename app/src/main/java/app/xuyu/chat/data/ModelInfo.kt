package app.xuyu.chat.data

/** 各模型的上下文窗口，用来决定「带多少历史」的上限 */
object ModelInfo {

    fun contextWindow(model: String): Int {
        val m = model.trim().lowercase()
        return when {
            m.startsWith("deepseek-flash") -> 1_000_000
            m.startsWith("deepseek-v4") -> 1_000_000
            // 老的 deepseek-chat / reasoner 现在也路由到 V4.1-Flash
            m.startsWith("deepseek-chat") -> 1_000_000
            m.startsWith("deepseek-reasoner") -> 1_000_000
            m.startsWith("deepseek") -> 128_000
            m.startsWith("gpt-4.1") -> 1_000_000
            m.startsWith("gpt-4o") -> 128_000
            m.startsWith("gpt") -> 128_000
            m.startsWith("o1") || m.startsWith("o3") || m.startsWith("o4") -> 200_000
            m.startsWith("claude") -> 200_000
            m.startsWith("gemini") -> 1_000_000
            m.startsWith("qwen") -> 131_072
            m.startsWith("kimi") || m.startsWith("moonshot") -> 131_072
            m.startsWith("glm") -> 128_000
            m.startsWith("llama") || m.startsWith("qwen2") -> 131_072
            else -> 128_000
        }
    }

    /** 1000000 -> "1M"，131072 -> "131K" */
    fun label(tokens: Int): String = when {
        tokens >= 1_000_000 && tokens % 1_000_000 == 0 -> "${tokens / 1_000_000}M"
        tokens >= 1_000_000 -> String.format(java.util.Locale.US, "%.1fM", tokens / 1_000_000f)
        tokens >= 1000 -> "${tokens / 1000}K"
        else -> tokens.toString()
    }
}
