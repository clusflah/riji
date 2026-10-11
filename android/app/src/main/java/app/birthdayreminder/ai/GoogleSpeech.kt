package app.birthdayreminder.ai

/**
 * Google Cloud 语音（纯逻辑）：说话转文字（Speech-to-Text）和朗读（Text-to-Speech）的地址、请求内容、返回解析。
 * 用一个 API Key，需要在 Google Cloud 控制台里把这两个 API 都启用。
 * 音频的 base64 编解码在 Android 侧做（这里只收发 base64 文字）。
 */
object GoogleSpeech {
    fun recognizeUrl(key: String) = "https://speech.googleapis.com/v1/speech:recognize?key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8")
    fun ttsUrl(key: String) = "https://texttospeech.googleapis.com/v1/text:synthesize?key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8")
    /** 检查 Key：列出中文语音，能列出来说明 Key 可用、朗读的 API 已启用 */
    fun voicesUrl(key: String) = "https://texttospeech.googleapis.com/v1/voices?languageCode=cmn-CN&key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8")

    /** 可选的中文声音 */
    val VOICES = listOf(
        "cmn-CN-Wavenet-A" to "女声 A（温和）",
        "cmn-CN-Wavenet-B" to "男声 B（沉稳）",
        "cmn-CN-Wavenet-C" to "男声 C（年轻）",
        "cmn-CN-Wavenet-D" to "女声 D（明亮）"
    )
    const val DEFAULT_VOICE = "cmn-CN-Wavenet-A"

    /** 声音名里的语言：en-US-Neural2-C -> en-US，cmn-CN-Wavenet-A -> cmn-CN */
    fun langOf(voice: String): String = voice.split("-").take(2).joinToString("-").ifBlank { "cmn-CN" }

    fun speakingRate(rate: String): Double = when (rate) { "slow" -> 0.8; "fast" -> 1.2; else -> 1.0 }

    /** 说话转文字的请求：16kHz 16 位单声道 PCM（LINEAR16），中文，自动加标点 */
    fun recognizeBody(audioBase64: String): String = MiniJson.write(mapOf(
        "config" to mapOf("encoding" to "LINEAR16", "sampleRateHertz" to AzureSpeech.SAMPLE_RATE,
            "languageCode" to "zh-CN", "enableAutomaticPunctuation" to true),
        "audio" to mapOf("content" to audioBase64)
    ))

    /** 读识别结果：把各段 transcript 连起来 */
    @Suppress("UNCHECKED_CAST")
    fun parseRecognize(status: Int, body: String): AzureSpeech.Result {
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
        if (status !in 200..299) return AzureSpeech.Result.Fail(errorText(status, m, "说话转文字"))
        val text = (m?.get("results") as? List<Any?>).orEmpty().joinToString("") { r ->
            (((r as? Map<String, Any?>)?.get("alternatives") as? List<Any?>)?.firstOrNull() as? Map<String, Any?>)?.get("transcript") as? String ?: ""
        }.trim()
        return if (text.isEmpty()) AzureSpeech.Result.Fail("没有听清，再说一次试试") else AzureSpeech.Result.Ok(text)
    }

    /** 朗读的请求：纯文字（先去掉 Markdown 符号），中文声音，mp3 */
    fun ttsBody(text: String, voice: String, rate: String): String {
        val v = if (VoiceCatalog.safeId(voice) && voice.count { it == '-' } >= 2) voice else DEFAULT_VOICE
        return MiniJson.write(mapOf(
            "input" to mapOf("text" to AzureTts.clean(text)),
            "voice" to mapOf("languageCode" to langOf(v), "name" to v),
            "audioConfig" to mapOf("audioEncoding" to "MP3", "speakingRate" to speakingRate(rate))
        ))
    }

    sealed class Audio {
        data class Ok(val base64: String) : Audio()
        data class Fail(val message: String) : Audio()
    }

    @Suppress("UNCHECKED_CAST")
    fun parseTts(status: Int, body: String): Audio {
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
        if (status !in 200..299) return Audio.Fail(errorText(status, m, "朗读"))
        val a = m?.get("audioContent") as? String
        return if (a.isNullOrBlank()) Audio.Fail("Google 没有返回声音") else Audio.Ok(a)
    }

    /** Google 的错误：{"error":{"code":403,"message":"…","status":"PERMISSION_DENIED"}} -> 中文提示 */
    @Suppress("UNCHECKED_CAST")
    fun errorText(status: Int, json: Map<String, Any?>?, what: String): String {
        val err = json?.get("error") as? Map<String, Any?>
        val msg = (err?.get("message") as? String).orEmpty()
        return when {
            msg.contains("API key not valid", true) || status == 400 && msg.contains("key", true) -> "Google 的 API Key 不对，请检查"
            msg.contains("has not been used", true) || msg.contains("disabled", true) || msg.contains("SERVICE_DISABLED") ->
                "Google Cloud 里还没有启用$what 的 API，请在控制台启用后再试"
            status == 403 -> "Google 拒绝了请求：Key 没有权限，或者没有启用$what 的 API"
            status == 429 -> "Google 请求太频繁或免费额度用完了"
            else -> "Google ${what}失败（$status）" + (if (msg.isNotBlank()) "：" + msg.take(80) else "")
        }
    }
}
