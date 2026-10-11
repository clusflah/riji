package app.birthdayreminder.ai

/** 对话记录和 JSON 文本互转（存在本机，和旧版一样最多留 60 条）。纯逻辑，可测试。 */
object AiChatStore {
    const val MAX = 60

    fun trim(chat: List<ChatMsg>): List<ChatMsg> = if (chat.size > MAX) chat.takeLast(MAX) else chat

    /**
     * 截断后可能把某个工具调用的“发起”留在前面被砍掉、“结果”留在后面（或反过来），
     * 这样发给 Claude / OpenAI 会因为对不上被拒绝，所以把开头孤立的工具结果去掉，
     * 并保证记录从一条 user 消息开始。
     */
    fun sanitize(chat: List<ChatMsg>): List<ChatMsg> {
        val list = trim(chat).toMutableList()
        while (list.isNotEmpty() && list.first().role != "user") list.removeAt(0)
        return list
    }

    fun toJson(chat: List<ChatMsg>): String = MiniJson.write(chat.map { m ->
        mapOf(
            "role" to m.role, "text" to m.text, "callId" to m.callId, "name" to m.name, "result" to m.result, "display" to m.display,
            "calls" to m.calls.map { c ->
                val cm = linkedMapOf<String, Any?>("id" to c.id, "name" to c.name, "args" to c.args)
                c.signature?.let { cm["sig"] = it }
                cm
            }
        )
    })

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String): List<ChatMsg> = try {
        (MiniJson.parse(json) as? List<Any?>).orEmpty().mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            ChatMsg(
                role = m["role"] as? String ?: return@mapNotNull null,
                text = m["text"] as? String ?: "",
                calls = (m["calls"] as? List<Any?>).orEmpty().mapNotNull { c ->
                    val cm = c as? Map<String, Any?> ?: return@mapNotNull null
                    ToolCall(cm["id"] as? String ?: "", cm["name"] as? String ?: "", (cm["args"] as? Map<String, Any?>).orEmpty(), cm["sig"] as? String)
                },
                callId = m["callId"] as? String ?: "",
                name = m["name"] as? String ?: "",
                result = m["result"],
                display = m["display"] as? String
            )
        }
    } catch (e: Exception) {
        emptyList()
    }
}
