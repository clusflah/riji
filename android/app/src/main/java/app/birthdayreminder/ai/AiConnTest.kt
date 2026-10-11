package app.birthdayreminder.ai

/**
 * AI 设置里“测试连接”的检查规则和结果文字（纯逻辑，可单独测试）。
 * 真正联网的两步在 AiClient.testBasic / testToolRoundTrip。
 */
object AiConnTest {
    /** 测试用的工具：什么都不做，只用来确认“调用工具 -> 回传结果 -> 模型继续回答”这一整圈是通的 */
    val PING_TOOL = ToolSpec("ping_tool", "连通性测试用的工具，调用后什么都不做，直接返回 ok")

    const val BASIC_PROMPT = "这是一次连通性测试，请只回复两个字：收到"
    const val TOOL_PROMPT = "这是一次连通性测试。请调用 ping_tool 工具，拿到结果后回复一句“工具正常”。"

    /** 开始测试前先检查输入，有问题返回提示文字，没问题返回 null */
    fun precheck(key: String, model: String, url: String): String? = when {
        key.isBlank() -> "请先填写 API Key"
        key.trim().contains(' ') -> "API Key 里有空格，请检查是否复制完整"
        model.isBlank() -> "请先填写模型名"
        url.isBlank() -> "请先填写接口地址"
        !url.trim().startsWith("https://") && !url.trim().startsWith("http://") -> "接口地址要以 https:// 开头"
        else -> null
    }

    /** 模型回复太长时截短，只用来展示“确实回话了” */
    fun clip(text: String, max: Int = 40): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        return if (t.length > max) t.take(max) + "…" else t
    }

    fun basicOk(ms: Long, reply: String): String =
        "✅ 连接正常（${ms} 毫秒）" + (if (reply.isNotBlank()) "，模型回复：“${clip(reply)}”" else "，模型没有返回文字")

    fun toolOk(ms: Long): String = "✅ 工具调用正常（${ms} 毫秒），AI 助手可以帮你添加和查看数据"

    /**
     * 第二步里“把工具结果回传”要发的完整对话：原问题 + 模型的工具调用（原样，含思考签名）+ 每个调用的结果。
     * 模型没有调用工具时返回 null。
     */
    fun followUpChat(first: AiReply): List<ChatMsg>? {
        if (first.toolCalls.isEmpty()) return null
        val chat = mutableListOf(ChatMsg("user", TOOL_PROMPT), ChatMsg("assistant", first.text, first.toolCalls))
        for (c in first.toolCalls) chat.add(ChatMsg("tool", callId = c.id, name = c.name, result = mapOf("ok" to true)))
        return chat
    }

    const val TOOL_NOT_CALLED = "⚠️ 基本对话正常，但模型这次没有调用测试工具。部分模型不支持工具调用，添加提醒、生日等可能用不了，可以换一个模型试试"
}
