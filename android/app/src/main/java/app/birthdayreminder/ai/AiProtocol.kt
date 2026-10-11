package app.birthdayreminder.ai

/** 工具参数的一个字段 */
data class ToolParam(
    val name: String,
    val type: String,
    val desc: String,
    val required: Boolean = false,
    val enum: List<String>? = null,
    val itemsType: String? = null
)

data class ToolSpec(val name: String, val desc: String, val params: List<ToolParam> = emptyList())

/**
 * 模型发起的一次工具调用。
 * signature：Gemini 较新模型返回的“思考签名”(thoughtSignature)，回传工具结果时必须原样带回去，
 * 不带的话 Gemini 会直接返回 400 拒绝请求。其它服务商没有这个字段，为 null。
 */
data class ToolCall(val id: String, val name: String, val args: Map<String, Any?>, val signature: String? = null)

/** 对话记录里的一条。role: user / assistant / tool */
data class ChatMsg(
    val role: String,
    val text: String = "",
    val calls: List<ToolCall> = emptyList(),   // role=assistant 时：这一轮模型发起的工具调用
    val callId: String = "",                   // role=tool 时：对应哪个调用
    val name: String = "",
    val result: Any? = null,                   // role=tool 时：工具执行结果（Map / List）
    val display: String? = null                // role=user 时：界面上显示的文字（为空就显示 text）；发给 AI 的始终是 text
)

data class AiReply(val text: String, val toolCalls: List<ToolCall>)

/** 请求失败：code 为 KEY(Key 无效) / NET(网络) / HTTP(其他) */
class AiException(val code: String, message: String) : Exception(message)

object AiProviders {
    val IDS = listOf("openai", "claude", "gemini", "grok", "deepseek", "kimi", "glm")
    val LABEL = mapOf("openai" to "OpenAI", "claude" to "Claude", "gemini" to "Gemini", "grok" to "Grok", "deepseek" to "DeepSeek",
        "kimi" to "Kimi", "glm" to "GLM 智谱")
    val KEY_HINT = mapOf(
        "openai" to "platform.openai.com", "claude" to "console.anthropic.com", "gemini" to "aistudio.google.com",
        "grok" to "console.x.ai", "deepseek" to "platform.deepseek.com",
        "kimi" to "platform.moonshot.cn", "glm" to "open.bigmodel.cn"
    )
    // 默认值和旧版 DEF_AI_MODELS / DEF_AI_URLS 一致
    val DEFAULT_MODEL = mapOf(
        "openai" to "gpt-4.1-mini", "claude" to "claude-sonnet-4-5", "gemini" to "gemini-3.8-flash",
        "grok" to "grok-4-fast", "deepseek" to "deepseek-chat",
        "kimi" to "kimi-k2-0905-preview", "glm" to "glm-4.5"
    )
    val DEFAULT_URL = mapOf(
        "openai" to "https://api.openai.com/v1/chat/completions",
        "claude" to "https://api.anthropic.com/v1/messages",
        "gemini" to "https://generativelanguage.googleapis.com/v1beta/models/",
        "grok" to "https://api.x.ai/v1/chat/completions",
        "deepseek" to "https://api.deepseek.com/chat/completions",
        // Kimi（月之暗面）、GLM（智谱）：和 OpenAI 同样的接口格式
        "kimi" to "https://api.moonshot.cn/v1/chat/completions",
        "glm" to "https://open.bigmodel.cn/api/paas/v4/chat/completions"
    )
}

/** 一次 HTTP 请求：地址 + 请求头 + 请求体（JSON 文本） */
data class AiRequest(val url: String, val headers: Map<String, String>, val body: String)

/**
 * 五家服务商的请求怎么拼、返回怎么读、对话记录怎么转成各家要的格式，
 * 逐条移植自旧版 convertHistoryFor / callOpenAiCompatible / callClaude / callGemini。
 * 纯逻辑，不联网、不碰 Android，方便单独测试。
 */
object AiProtocol {

    fun toJsonSchema(params: List<ToolParam>): Map<String, Any?> {
        val props = LinkedHashMap<String, Any?>()
        val req = ArrayList<String>()
        for (p in params) {
            val m = LinkedHashMap<String, Any?>()
            m["type"] = p.type
            m["description"] = p.desc
            p.enum?.let { m["enum"] = it }
            p.itemsType?.let { m["items"] = mapOf("type" to it) }
            props[p.name] = m
            if (p.required) req.add(p.name)
        }
        return mapOf("type" to "object", "properties" to props, "required" to req)
    }

    private fun resultAsObject(r: Any?): Map<String, Any?> =
        if (r is Map<*, *>) @Suppress("UNCHECKED_CAST") (r as Map<String, Any?>) else mapOf("result" to r)

    /** 转成 Claude 的 messages：工具结果要合并成同一条 user 消息里的多个 tool_result */
    fun claudeMessages(chat: List<ChatMsg>): List<Map<String, Any?>> {
        class M(val role: String, val content: Any, val toolBatch: Boolean = false)
        val out = ArrayList<M>()
        for (m in chat) {
            when (m.role) {
                "user" -> out.add(M("user", m.text))
                "assistant" -> {
                    val content = ArrayList<Map<String, Any?>>()
                    if (m.text.isNotEmpty()) content.add(mapOf("type" to "text", "text" to m.text))
                    for (c in m.calls) content.add(mapOf("type" to "tool_use", "id" to c.id, "name" to c.name, "input" to c.args))
                    out.add(M("assistant", content))
                }
                "tool" -> {
                    val block = mapOf("type" to "tool_result", "tool_use_id" to m.callId, "content" to MiniJson.write(m.result))
                    val last = out.lastOrNull()
                    if (last != null && last.role == "user" && last.toolBatch) {
                        @Suppress("UNCHECKED_CAST") (last.content as MutableList<Map<String, Any?>>).add(block)
                    } else {
                        out.add(M("user", mutableListOf(block), true))
                    }
                }
            }
        }
        return out.map { mapOf("role" to it.role, "content" to it.content) }
    }

    /** 转成 Gemini 的 contents（角色是 user / model；工具结果是 functionResponse） */
    fun geminiContents(chat: List<ChatMsg>): List<Map<String, Any?>> {
        class C(val role: String, val parts: MutableList<Map<String, Any?>>, val toolBatch: Boolean = false)
        val out = ArrayList<C>()
        for (m in chat) {
            when (m.role) {
                "user" -> out.add(C("user", mutableListOf(mapOf("text" to m.text))))
                "assistant" -> {
                    val parts = ArrayList<Map<String, Any?>>()
                    if (m.text.isNotEmpty()) parts.add(mapOf("text" to m.text))
                    for (c in m.calls) {
                        val part = LinkedHashMap<String, Any?>()
                        part["functionCall"] = mapOf("name" to c.name, "args" to c.args)
                        c.signature?.let { part["thoughtSignature"] = it }   // 原样带回，缺了会被拒绝
                        parts.add(part)
                    }
                    out.add(C("model", parts))
                }
                "tool" -> {
                    val part = mapOf("functionResponse" to mapOf("name" to m.name, "response" to resultAsObject(m.result)))
                    val last = out.lastOrNull()
                    if (last != null && last.role == "user" && last.toolBatch) last.parts.add(part)
                    else out.add(C("user", mutableListOf(part), true))
                }
            }
        }
        return out.map { mapOf("role" to it.role, "parts" to it.parts) }
    }

    /** 转成 OpenAI 兼容格式（OpenAI / Grok / DeepSeek 通用） */
    fun openAiMessages(chat: List<ChatMsg>, systemPrompt: String): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        out.add(mapOf("role" to "system", "content" to systemPrompt))
        for (m in chat) {
            when (m.role) {
                "user" -> out.add(mapOf("role" to "user", "content" to m.text))
                "assistant" -> {
                    val msg = LinkedHashMap<String, Any?>()
                    msg["role"] = "assistant"
                    msg["content"] = if (m.text.isEmpty()) null else m.text
                    if (m.calls.isNotEmpty()) {
                        msg["tool_calls"] = m.calls.map {
                            mapOf("id" to it.id, "type" to "function",
                                "function" to mapOf("name" to it.name, "arguments" to MiniJson.write(it.args)))
                        }
                    }
                    out.add(msg)
                }
                "tool" -> out.add(mapOf("role" to "tool", "tool_call_id" to m.callId, "content" to MiniJson.write(m.result)))
            }
        }
        return out
    }

    fun buildRequest(
        provider: String, key: String, model: String, url: String,
        chat: List<ChatMsg>, tools: List<ToolSpec>, systemPrompt: String
    ): AiRequest = when (provider) {
        "claude" -> {
            val body = LinkedHashMap<String, Any?>()
            body["model"] = model
            body["max_tokens"] = 1024
            body["system"] = systemPrompt
            body["messages"] = claudeMessages(chat)
            if (tools.isNotEmpty()) {
                body["tools"] = tools.map { mapOf("name" to it.name, "description" to it.desc, "input_schema" to toJsonSchema(it.params)) }
            }
            AiRequest(url, mapOf("Content-Type" to "application/json", "x-api-key" to key, "anthropic-version" to "2023-06-01"), MiniJson.write(body))
        }
        "gemini" -> {
            val m = model.ifBlank { AiProviders.DEFAULT_MODEL.getValue("gemini") }
            val body = LinkedHashMap<String, Any?>()
            body["systemInstruction"] = mapOf("parts" to listOf(mapOf("text" to systemPrompt)))
            body["contents"] = geminiContents(chat)
            if (tools.isNotEmpty()) {
                body["tools"] = listOf(mapOf("functionDeclarations" to tools.map {
                    mapOf("name" to it.name, "description" to it.desc, "parameters" to toJsonSchema(it.params))
                }))
            }
            AiRequest(url + java.net.URLEncoder.encode(m, "UTF-8") + ":generateContent",
                mapOf("Content-Type" to "application/json", "x-goog-api-key" to key), MiniJson.write(body))
        }
        else -> {
            val body = LinkedHashMap<String, Any?>()
            body["model"] = model
            body["messages"] = openAiMessages(chat, systemPrompt)
            if (tools.isNotEmpty()) {
                body["tools"] = tools.map {
                    mapOf("type" to "function",
                        "function" to mapOf("name" to it.name, "description" to it.desc, "parameters" to toJsonSchema(it.params)))
                }
                body["tool_choice"] = "auto"
            }
            AiRequest(url, mapOf("Content-Type" to "application/json", "Authorization" to "Bearer $key"), MiniJson.write(body))
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun asMap(x: Any?): Map<String, Any?> = (x as? Map<String, Any?>) ?: emptyMap()
    @Suppress("UNCHECKED_CAST")
    private fun asList(x: Any?): List<Any?> = (x as? List<Any?>) ?: emptyList()

    /** 服务商返回的错误信息（每家放的位置差不多：error.message） */
    fun errorMessage(json: Any?, status: Int): String {
        val msg = asMap(asMap(json)["error"])["message"] as? String
        return msg ?: "http $status"
    }

    /**
     * 状态码 -> 错误类别。KEY = Key 无效，HTTP = 其它（界面上会显示服务商给的原因）。
     * Gemini 很多种请求错误都返回 400，只有错误信息里明确说是 API Key 的问题，才当作 Key 无效。
     */
    fun errorCode(provider: String, status: Int, message: String = ""): String {
        val keyHint = message.contains("API key", true) || message.contains("API_KEY", true) || message.contains("api_key", true)
        return if (provider == "gemini") {
            if (status == 403 || (status == 400 && keyHint) || status == 401) "KEY" else "HTTP"
        } else {
            if (status == 401 || status == 403) "KEY" else "HTTP"
        }
    }

    fun parseReply(provider: String, json: Any?): AiReply {
        val root = asMap(json)
        when (provider) {
            "claude" -> {
                val sb = StringBuilder()
                val calls = ArrayList<ToolCall>()
                for (b in asList(root["content"])) {
                    val bm = asMap(b)
                    when (bm["type"]) {
                        "text" -> sb.append(bm["text"] as? String ?: "")
                        "tool_use" -> calls.add(ToolCall(bm["id"] as? String ?: "", bm["name"] as? String ?: "", asMap(bm["input"])))
                    }
                }
                return AiReply(sb.toString(), calls)
            }
            "gemini" -> {
                val cand = asMap(asList(root["candidates"]).firstOrNull())
                val parts = asList(asMap(cand["content"])["parts"])
                val sb = StringBuilder()
                val calls = ArrayList<ToolCall>()
                parts.forEachIndexed { i, p ->
                    val pm = asMap(p)
                    val t = pm["text"] as? String
                    if (!t.isNullOrEmpty()) sb.append(t)
                    else if (pm["functionCall"] != null) {
                        val fc = asMap(pm["functionCall"])
                        calls.add(ToolCall("g$i", fc["name"] as? String ?: "", asMap(fc["args"]), pm["thoughtSignature"] as? String))
                    }
                }
                return AiReply(sb.toString(), calls)
            }
            else -> {
                val msg = asMap(asMap(asList(root["choices"]).firstOrNull())["message"])
                val calls = asList(msg["tool_calls"]).map { tc ->
                    val t = asMap(tc)
                    val f = asMap(t["function"])
                    val args = try {
                        asMap(MiniJson.parse((f["arguments"] as? String).orEmpty().ifBlank { "{}" }))
                    } catch (e: Exception) { emptyMap() }
                    ToolCall(t["id"] as? String ?: "", f["name"] as? String ?: "", args)
                }
                return AiReply(msg["content"] as? String ?: "", calls)
            }
        }
    }
}
