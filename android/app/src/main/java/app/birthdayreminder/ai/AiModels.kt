package app.birthdayreminder.ai

/**
 * 模型下拉框：每家服务商内置的常用模型 + 用你的 Key 从服务商拉取到的可用模型。
 * 纯逻辑：拉取地址怎么拼、返回怎么读、怎么筛掉不能对话的模型（语音、画图、向量等）。
 */
object AiModels {
    /** 内置的常用模型（第一个是默认）。服务商出了新模型可以点“获取可用模型”，或者直接手动输入 */
    val PRESETS = mapOf(
        "openai" to listOf("gpt-4.1-mini", "gpt-4.1", "gpt-4o", "gpt-4o-mini", "o4-mini"),
        "claude" to listOf("claude-sonnet-4-5", "claude-opus-4-1", "claude-haiku-4-5", "claude-3-5-haiku-latest"),
        // 2026 年起 2.5 系列只对以前用过的老用户开放，新项目用 3.x
        "gemini" to listOf("gemini-3.8-flash", "gemini-3.7-flash", "gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite",
            "gemini-3.1-flash-lite", "gemini-3.1-pro-preview", "gemini-2.5-flash"),
        "grok" to listOf("grok-4-fast", "grok-4", "grok-3-mini"),
        "deepseek" to listOf("deepseek-chat", "deepseek-reasoner"),
        "kimi" to listOf("kimi-k2-0905-preview", "kimi-k2-turbo-preview", "kimi-latest", "moonshot-v1-32k", "moonshot-v1-8k"),
        "glm" to listOf("glm-4.5", "glm-4.5-air", "glm-4.6", "glm-4-plus", "glm-4-flash")
    )

    data class ListRequest(val url: String, val headers: Map<String, String>)

    /** 拉取模型列表的请求；不支持拉取的服务商返回 null（只用内置列表） */
    fun listRequest(provider: String, key: String, chatUrl: String): ListRequest? {
        val u = chatUrl.trim()
        return when (provider) {
            "claude" -> ListRequest(u.substringBeforeLast("/messages") + "/models?limit=100",
                mapOf("x-api-key" to key, "anthropic-version" to "2023-06-01"))
            "gemini" -> ListRequest(u.trimEnd('/') + "?pageSize=200", mapOf("x-goog-api-key" to key))
            "glm" -> null      // 智谱没有公开的模型列表接口，用内置列表
            else -> if (u.endsWith("/chat/completions")) ListRequest(u.removeSuffix("/chat/completions") + "/models", mapOf("Authorization" to "Bearer $key")) else null
        }
    }

    /** 名字里带这些的不是对话模型（语音、画图、向量、审核等），不放进下拉框 */
    private val NOT_CHAT = listOf("embedding", "whisper", "tts", "dall-e", "image", "moderation", "audio", "realtime",
        "transcribe", "search", "computer-use", "codex", "vision-preview", "babbage", "davinci", "aqa", "imagen", "veo", "learnlm",
        "live", "omni", "lyria", "robotics", "deep-research", "antigravity", "translate", "native-audio")

    fun isChatModel(id: String): Boolean = NOT_CHAT.none { id.lowercase().contains(it) }

    /** 读服务商返回的模型列表 */
    @Suppress("UNCHECKED_CAST")
    fun parse(provider: String, json: Any?): List<String> {
        val root = json as? Map<String, Any?> ?: return emptyList()
        val ids = if (provider == "gemini") {
            (root["models"] as? List<Any?>).orEmpty().mapNotNull { m ->
                val mm = m as? Map<String, Any?> ?: return@mapNotNull null
                val methods = (mm["supportedGenerationMethods"] as? List<Any?>).orEmpty()
                if (methods.isNotEmpty() && "generateContent" !in methods) return@mapNotNull null
                (mm["name"] as? String)?.removePrefix("models/")
            }
        } else {
            (root["data"] as? List<Any?>).orEmpty().mapNotNull { (it as? Map<String, Any?>)?.get("id") as? String }
        }
        return ids.filter { it.isNotBlank() && isChatModel(it) }.distinct().sorted()
    }

    /** 下拉框里的完整列表：内置常用的在前（保持顺序），拉取到的其它模型在后，去重 */
    fun merge(provider: String, fetched: List<String>, current: String): List<String> {
        val out = LinkedHashSet<String>()
        if (current.isNotBlank()) out.add(current.trim())
        PRESETS[provider].orEmpty().forEach { out.add(it) }
        fetched.forEach { out.add(it) }
        return out.toList()
    }
}
