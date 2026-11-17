package app.xuyu.chat.data

/** 免责声明文案。改内容时把 DISCLAIMER_VERSION +1，老用户会重新看到一次。 */
object Legal {

    const val DISCLAIMER_VERSION = 1

    data class Section(val title: String, val body: String)

    const val INTRO =
        "本应用是一个聊天客户端。它本身不提供 AI 模型，所有回答都由你自己配置的第三方模型服务生成。" +
            "开始使用前，请阅读下面这些内容。"

    val SECTIONS = listOf(
        Section(
            "一、AI 生成的内容可能出错",
            "大模型存在「幻觉」，可能给出不准确、过时甚至完全编造的信息；联网搜索的摘要与引用也可能与原文不符。" +
                "所有回答仅供参考，重要信息请自行核实。"
        ),
        Section(
            "二、不构成专业建议",
            "本应用输出的任何内容都不构成医疗、法律、金融、心理等领域的专业意见。" +
                "涉及这类问题，请咨询有资质的专业人士，不要仅凭模型回答做决定。"
        ),
        Section(
            "三、费用与额度由你承担",
            "API Key、模型调用费用、联网搜索费用与账号额度，均由你自己的服务账号承担。" +
                "本应用不代收任何费用，也不对因配置错误、额度耗尽、第三方调价造成的损失负责。"
        ),
        Section(
            "四、搜索内容来自第三方",
            "联网搜索返回的标题、摘要与链接来自搜索引擎抓取的第三方网页，版权归原网站所有。" +
                "引用不代表本应用认可其观点；转载或商用请自行取得授权。"
        ),
        Section(
            "五、数据怎么存",
            "聊天记录、角色设定、API Key 只保存在本机应用私有目录，不会上传到本应用的服务器（本应用没有服务器）。" +
                "但你发出的提问与上下文会发送给你配置的模型服务商，其处理方式适用该服务商的隐私政策。"
        ),
        Section(
            "六、使用规范",
            "请勿使用本应用制作、传播违法违规内容，或从事侵害他人合法权益的行为。" +
                "未成年人应在监护人指导下使用。"
        ),
        Section(
            "七、责任限制",
            "在法律允许的范围内，因使用或无法使用本应用而产生的任何直接或间接损失，由使用者自行承担。"
        )
    )

    const val FOOTER = "点击「同意并继续」表示你已阅读、理解并接受上述内容。"

    fun plainText(): String {
        val sb = StringBuilder()
        sb.append(INTRO).append("\n\n")
        SECTIONS.forEach { s ->
            sb.append(s.title).append('\n').append(s.body).append("\n\n")
        }
        sb.append(FOOTER)
        return sb.toString()
    }
}
