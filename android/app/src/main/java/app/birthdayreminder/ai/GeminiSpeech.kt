package app.birthdayreminder.ai

/**
 * 用 Gemini API（同一个 Gemini Key）做语音：
 *  - 说话转文字：把录音（WAV）附在请求里，交给 AI 设置里选的 Gemini 模型逐字转写（generateContent，和日记润色同一个接口）
 *  - 朗读：Gemini TTS 模型（Interactions 接口），返回 WAV
 * 纯逻辑：地址、请求内容、返回解析。base64 编解码和联网在 Android 侧。
 */
object GeminiSpeech {
    /** 朗读用的模型：官方推荐用于朗读功能、快且省 */
    const val TTS_MODEL = "gemini-3.8-flash-lite-tts"

    /** 预置声音（官方 30 个里挑几个适合朗读中文的） */
    val VOICES = listOf(
        "Kore" to "Kore（沉稳）",
        "Sulafat" to "Sulafat（温暖）",
        "Achernar" to "Achernar（轻柔）",
        "Puck" to "Puck（活泼）",
        "Charon" to "Charon（播报）",
        "Leda" to "Leda（年轻）"
    )
    const val DEFAULT_VOICE = "Kore"

    /** AI 设置里 Gemini 的地址（…/v1beta/models/）-> 同一个版本下的 Interactions 地址 */
    fun interactionsUrl(geminiUrl: String): String {
        val u = geminiUrl.trim()
        val base = if (u.contains("/models")) u.substringBefore("/models") else u.trimEnd('/')
        return base.trimEnd('/') + "/interactions"
    }

    /** 转写用 generateContent：和对话、润色的地址拼法一样 */
    fun transcribeUrl(geminiUrl: String, model: String): String =
        geminiUrl.trim().let { if (it.endsWith("/")) it else "$it/" } + java.net.URLEncoder.encode(model.trim(), "UTF-8") + ":generateContent"

    const val TRANSCRIBE_PROMPT = "请把这段录音逐字转写成文字。说的主要是普通话，可能夹杂英文。" +
        "只输出转写出来的文字，加上合适的标点，不要加任何解释、引号或前后缀。如果没有听到说话，就什么都不输出。"

    fun transcribeBody(wavBase64: String): String = MiniJson.write(mapOf(
        "contents" to listOf(mapOf("role" to "user", "parts" to listOf(
            mapOf("text" to TRANSCRIBE_PROMPT),
            mapOf("inlineData" to mapOf("mimeType" to "audio/wav", "data" to wavBase64))
        ))),
        "generationConfig" to mapOf("temperature" to 0)
    ))

    /** 读转写结果：candidates[0].content.parts 里的文字连起来（跳过思考内容） */
    @Suppress("UNCHECKED_CAST")
    fun parseTranscribe(status: Int, body: String): AzureSpeech.Result {
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
        if (status !in 200..299) return AzureSpeech.Result.Fail(errorText(status, m, "说话转文字"))
        val parts = ((((m?.get("candidates") as? List<Any?>)?.firstOrNull() as? Map<String, Any?>)?.get("content") as? Map<String, Any?>)
            ?.get("parts") as? List<Any?>).orEmpty()
        val text = parts.mapNotNull { p ->
            val pm = p as? Map<String, Any?> ?: return@mapNotNull null
            if (pm["thought"] == true) null else pm["text"] as? String
        }.joinToString("").trim().trim('"', '“', '”', '「', '」')
        return if (text.isEmpty()) AzureSpeech.Result.Fail("没有听清，再说一次试试") else AzureSpeech.Result.Ok(text)
    }

    /** 朗读请求：文字（去掉 Markdown 符号）原样念；语速快慢用 speech_metadata.style 表达 */
    fun ttsBody(text: String, voice: String, rate: String): String {
        val v = if (VoiceCatalog.safeId(voice)) voice else DEFAULT_VOICE      // 内置的或从声音库拉到的都能用
        val item = linkedMapOf<String, Any?>("type" to "text", "text" to AzureTts.clean(text))
        when (rate) {
            "slow" -> item["annotations"] = listOf(mapOf("type" to "speech_metadata", "style" to "speaking slowly and clearly"))
            "fast" -> item["annotations"] = listOf(mapOf("type" to "speech_metadata", "style" to "speaking rapidly"))
        }
        return MiniJson.write(mapOf(
            "model" to TTS_MODEL,
            "input" to listOf(mapOf("type" to "user_input", "content" to listOf(item))),
            "response_format" to mapOf("type" to "audio"),
            "generation_config" to mapOf("speech_config" to listOf(mapOf("voice" to v)))
        ))
    }

    /** 读朗读结果：steps 里 model_output 的最后一个 audio 块（base64） */
    @Suppress("UNCHECKED_CAST")
    fun parseTts(status: Int, body: String): GoogleSpeech.Audio {
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
        if (status !in 200..299) return GoogleSpeech.Audio.Fail(errorText(status, m, "朗读"))
        var data: String? = null
        for (step in (m?.get("steps") as? List<Any?>).orEmpty()) {
            val sm = step as? Map<String, Any?> ?: continue
            if (sm["type"] != null && sm["type"] != "model_output") continue
            for (c in (sm["content"] as? List<Any?>).orEmpty()) {
                val cm = c as? Map<String, Any?> ?: continue
                if (cm["type"] == "audio") (cm["data"] as? String)?.let { data = it }
            }
        }
        // 兼容另一种写法：output_audio.data
        if (data == null) data = ((m?.get("output_audio") as? Map<String, Any?>)?.get("data")) as? String
        return if (data.isNullOrBlank()) GoogleSpeech.Audio.Fail("Gemini 没有返回声音") else GoogleSpeech.Audio.Ok(data!!)
    }

    /** 返回的声音字节：3.8 TTS 默认带 WAV 文件头；老模型是不带头的 24kHz PCM，补上文件头才能播放 */
    fun ensureWav(bytes: ByteArray): ByteArray =
        if (bytes.size >= 4 && bytes[0] == 'R'.code.toByte() && bytes[1] == 'I'.code.toByte() && bytes[2] == 'F'.code.toByte() && bytes[3] == 'F'.code.toByte()) bytes
        else AzureSpeech.wav(bytes, 24000)

    @Suppress("UNCHECKED_CAST")
    fun errorText(status: Int, json: Map<String, Any?>?, what: String): String {
        val msg = ((json?.get("error") as? Map<String, Any?>)?.get("message") as? String).orEmpty()
        return when {
            msg.contains("API key not valid", true) || msg.contains("API_KEY_INVALID", true) -> "Gemini 的 API Key 不对，请到 AI 设置里检查"
            status == 429 -> "Gemini 请求太频繁或额度用完了，稍后再试"
            status == 404 || msg.contains("not found", true) -> "Gemini ${what}用的模型不可用：" + msg.take(80)
            status == 403 -> "Gemini 拒绝了请求（没有权限，或所在地区不支持）"
            else -> "Gemini ${what}失败（$status）" + (if (msg.isNotBlank()) "：" + msg.take(80) else "")
        }
    }
}
