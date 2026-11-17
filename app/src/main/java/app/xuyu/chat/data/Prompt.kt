package app.xuyu.chat.data

/** 把「人设卡」拼成真正的 system prompt */
object Prompt {

    fun system(persona: Persona, mode: String = Settings.MODE_NORMAL): String {
        val sb = StringBuilder()
        sb.append("你现在扮演「").append(persona.name.ifBlank { "AI" }).append("」。\n")
        if (persona.tagline.isNotBlank()) sb.append(persona.tagline).append('\n')

        if (mode == Settings.MODE_RP) {
            if (persona.userRole.isNotBlank()) {
                sb.append("\n【我的角色】\n").append(persona.userRole.trim()).append('\n')
            }
            if (persona.scene.isNotBlank()) {
                sb.append("\n【场景 / 世界观】\n").append(persona.scene.trim()).append('\n')
            }
        }

        if (persona.systemPrompt.isNotBlank()) {
            sb.append("\n【角色设定】\n").append(persona.systemPrompt.trim()).append('\n')
        }
        if (persona.examples.isNotEmpty()) {
            sb.append("\n【对话示例】\n")
            persona.examples.forEach { ex ->
                if (ex.user.isNotBlank()) sb.append("用户：").append(ex.user.trim()).append('\n')
                if (ex.ai.isNotBlank()) sb.append(persona.name).append("：").append(ex.ai.trim()).append('\n')
            }
        }

        sb.append("\n【要求】\n")
        sb.append("1. 始终以「${persona.name}」的第一人称说话，口语化、自然，不要写成机械的条目清单。\n")
        if (persona.stayInCharacter) {
            sb.append("2. 不要提到自己是 AI、语言模型或助手，不要跳出角色，也不要解释你在扮演。\n")
        } else {
            sb.append("2. 尽量保持在角色里，但对方明确问到时可以坦诚你是 AI。\n")
        }
        sb.append("3. 长度：").append(lengthRule(persona.replyStyle)).append('\n')
        if (persona.allowActions) {
            sb.append("4. 可以用 *星号包裹* 的动作、神态、环境描写来表现角色，但别喧宾夺主。\n")
        } else {
            sb.append("4. 只写角色说出口的话，不要加动作、神态或旁白描写。\n")
        }
        sb.append("5. 不要每次都用同样的开场白；对方问到你不知道的事，就坦率说不确定，不要编造。\n")
        return sb.toString()
    }

    private fun lengthRule(style: Int): String = when (style) {
        0 -> "短一点，每次 1~3 句，像即时聊天。"
        2 -> "可以长一些，多写几段，把动作、心理和环境写透；但也别为了长而注水。"
        else -> "中等，每次 2~5 句，该展开的时候再展开。"
    }

    /** 把检索结果拼成一段塞进 system prompt */
    fun searchContext(sources: List<Source>): String {
        if (sources.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n\n【联网资料】下面是搜索引擎刚刚检索到的内容，可能不完整、也可能过时：\n")
        sources.forEachIndexed { index, s ->
            sb.append('[').append(index + 1).append("] ").append(s.title).append('\n')
            if (s.url.isNotBlank()) sb.append("URL: ").append(s.url).append('\n')
            val text = s.detail.ifBlank { s.snippet }
            if (text.isNotBlank()) sb.append(text.trim()).append('\n')
            sb.append('\n')
        }
        sb.append("回答时优先依据这些资料；用到哪条就在句末标 [编号]。资料里没提到的，直说不确定，不要编造。\n")
        return sb.toString()
    }
}
