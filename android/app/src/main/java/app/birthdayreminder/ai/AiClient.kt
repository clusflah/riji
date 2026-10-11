package app.birthdayreminder.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * 真正联网的那一步。原生请求不受浏览器的跨域限制，所以旧版说明里
 * “OpenAI 官方接口不允许网页直接访问、需要自建代理”的问题，在原生版里不存在，五家都能直接连。
 */
object AiClient {
    private const val TIMEOUT_MS = 60_000

    suspend fun call(provider: String, key: String, model: String, url: String,
                     chat: List<ChatMsg>, tools: List<ToolSpec>, systemPrompt: String): AiReply =
        withContext(Dispatchers.IO) {
            val req = AiProtocol.buildRequest(provider, key, model, url, chat, tools, systemPrompt)
            val conn: HttpURLConnection
            val status: Int
            val text: String
            try {
                conn = URL(req.url).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.connectTimeout = 15_000
                conn.readTimeout = TIMEOUT_MS
                conn.doOutput = true
                for ((k, v) in req.headers) conn.setRequestProperty(k, v)
                conn.outputStream.use { it.write(req.body.toByteArray(Charsets.UTF_8)) }
                status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                text = stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } catch (e: Exception) {
                throw AiException("NET", "network")
            }
            val json = try { MiniJson.parse(text) } catch (e: Exception) { null }
            if (status !in 200..299) {
                val msg = AiProtocol.errorMessage(json, status)
                throw AiException(AiProtocol.errorCode(provider, status, msg), msg)
            }
            AiProtocol.parseReply(provider, json)
        }

    /** 用 Key 去服务商那里拉取账号能用的模型。返回 (模型列表, 出错原因)；不支持拉取的服务商返回空列表和说明 */
    suspend fun listModels(provider: String, key: String, url: String): Pair<List<String>, String?> = withContext(Dispatchers.IO) {
        if (key.isBlank()) return@withContext emptyList<String>() to "请先填写 API Key"
        val req = AiModels.listRequest(provider, key.trim(), url) ?: return@withContext emptyList<String>() to "这家服务商不提供模型列表，请从内置列表里选，或手动输入"
        try {
            val c = URL(req.url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            for ((k, v) in req.headers) c.setRequestProperty(k, v)
            val status = c.responseCode
            val text = (if (status in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
            val json = try { MiniJson.parse(text) } catch (e: Exception) { null }
            if (status !in 200..299) {
                return@withContext emptyList<String>() to (if (status == 401 || status == 403) "Key 无效或没有权限" else "获取失败：" + AiProtocol.errorMessage(json, status))
            }
            val list = AiModels.parse(provider, json)
            list to (if (list.isEmpty()) "没有获取到可用的对话模型" else null)
        } catch (e: Exception) {
            emptyList<String>() to "网络请求失败，请检查网络"
        }
    }

    /** 连接测试第一步：发一句简单的话（不带工具），返回 耗时毫秒 和 模型回复 */
    suspend fun testBasic(provider: String, key: String, model: String, url: String): Pair<Long, String> {
        val t0 = System.currentTimeMillis()
        val r = call(provider, key.trim(), model.trim(), url.trim(), listOf(ChatMsg("user", AiConnTest.BASIC_PROMPT)), emptyList(), "你是连通性测试助手。")
        return (System.currentTimeMillis() - t0) to r.text
    }

    /**
     * 连接测试第二步：让模型调用测试工具，再把结果回传，确认整圈都通。
     * 返回 耗时毫秒；模型没有调用工具时返回 null。
     */
    suspend fun testToolRoundTrip(provider: String, key: String, model: String, url: String): Long? {
        val t0 = System.currentTimeMillis()
        val tools = listOf(AiConnTest.PING_TOOL)
        val sys = "你是连通性测试助手。"
        val first = call(provider, key.trim(), model.trim(), url.trim(), listOf(ChatMsg("user", AiConnTest.TOOL_PROMPT)), tools, sys)
        val chat = AiConnTest.followUpChat(first) ?: return null
        call(provider, key.trim(), model.trim(), url.trim(), chat, tools, sys)     // 这一步出错会直接抛出，由界面显示原因
        return System.currentTimeMillis() - t0
    }

    /** 日记润色：单轮请求 Gemini，不带工具，返回润色后的正文（提示词和旧版一致） */
    suspend fun polishDiary(key: String, model: String, baseUrl: String, text: String): String {
        val prompt = "请帮我润色下面这段中文日记，让语句更通顺自然，保持第一人称和原意，不要编造没有的内容，不要使用列表或标题，只输出润色后的正文：\n\n$text"
        val reply = call("gemini", key, model, baseUrl, listOf(ChatMsg("user", prompt)), emptyList(), "你是一个中文写作助手。")
        val out = reply.text.trim()
        if (out.isEmpty()) throw AiException("EMPTY", "empty")
        return out
    }

    fun errorText(e: Throwable): String = when {
        e is AiException && e.code == "KEY" -> "API Key 无效，请检查设置里填的 Key、模型名和接口地址"
        e is AiException && e.code == "NET" -> "网络请求失败，请检查网络"
        e is AiException && e.code == "EMPTY" -> "AI 没有返回内容，请稍后再试"
        e is AiException && e.code == "HTTP" -> "请求被 AI 服务拒绝：" + (e.message ?: "未知原因")
        else -> "请求失败：" + (e.message ?: "未知错误")
    }
}
