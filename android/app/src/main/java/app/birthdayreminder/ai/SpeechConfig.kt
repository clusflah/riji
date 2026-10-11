package app.birthdayreminder.ai

import android.content.Context
import android.util.Base64
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** 现在用哪家语音服务、填好了没有；以及 Google 语音的联网部分 */
object SpeechConfig {
    val PROVIDERS = listOf("gemini" to "Gemini", "azure" to "Azure", "google" to "Google Cloud")

    fun provider(ctx: Context): String = Prefs.speechProvider(ctx)

    /** 所选的语音服务 Key（Azure 还要区域）都填好了 */
    fun configured(ctx: Context): Boolean = when (provider(ctx)) {
        "gemini" -> Prefs.aiKey(ctx, "gemini").isNotBlank()
        "google" -> Prefs.googleSpeechKey(ctx).isNotBlank()
        else -> Prefs.azureSpeechKey(ctx).isNotBlank() && AzureSpeech.cleanRegion(Prefs.azureSpeechRegion(ctx)) != null
    }

    fun notConfiguredText(ctx: Context): String = when (provider(ctx)) {
        "gemini" -> "请先在 AI 设置里选 Gemini 服务商，填好 Gemini 的 API Key"
        "google" -> "请先在 AI 设置 → 语音 里填好 Google Cloud 的 API Key"
        else -> "请先在 AI 设置 → 语音 里填好 Azure 语音的 Key 和区域"
    }

    private fun geminiKey(ctx: Context) = Prefs.aiKey(ctx, "gemini")
    private fun geminiUrl(ctx: Context) = Prefs.aiUrl(ctx, "gemini", AiProviders.DEFAULT_URL.getValue("gemini"))
    private fun geminiModel(ctx: Context) = Prefs.aiModel(ctx, "gemini", AiProviders.DEFAULT_MODEL.getValue("gemini"))

    private suspend fun postGemini(url: String, key: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.setRequestProperty("x-goog-api-key", key)
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = c.responseCode
        status to (if (status in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
    }

    /** Gemini 说话转文字：录音包成 WAV，交给 AI 设置里选的 Gemini 模型逐字转写 */
    suspend fun geminiRecognize(ctx: Context, pcm: ByteArray): AzureSpeech.Result = try {
        val b64 = Base64.encodeToString(AzureSpeech.wav(pcm), Base64.NO_WRAP)
        val (status, body) = postGemini(GeminiSpeech.transcribeUrl(geminiUrl(ctx), geminiModel(ctx)), geminiKey(ctx), GeminiSpeech.transcribeBody(b64))
        GeminiSpeech.parseTranscribe(status, body)
    } catch (e: Exception) {
        AzureSpeech.Result.Fail("连不上 Gemini，请检查网络（国内一般需要代理）")
    }

    /** Gemini 朗读：返回可以直接播放的 WAV 字节，失败抛出带原因的异常 */
    suspend fun geminiSynthesize(ctx: Context, text: String, voice: String, rate: String): ByteArray {
        val (status, body) = try { postGemini(GeminiSpeech.interactionsUrl(geminiUrl(ctx)), geminiKey(ctx), GeminiSpeech.ttsBody(text, voice, rate)) }
        catch (e: Exception) { throw IllegalStateException("连不上 Gemini，请检查网络（国内一般需要代理）") }
        return when (val r = GeminiSpeech.parseTts(status, body)) {
            is GoogleSpeech.Audio.Ok -> GeminiSpeech.ensureWav(Base64.decode(r.base64, Base64.DEFAULT))
            is GoogleSpeech.Audio.Fail -> throw IllegalStateException(r.message)
        }
    }

    // ---------------- 声音：当前选的、可选列表（云端拉取的优先，没有就用内置的） ----------------
    fun currentVoice(ctx: Context, provider: String): String = when (provider) {
        "gemini" -> Prefs.ttsVoiceGemini(ctx)
        "google" -> Prefs.ttsVoiceGoogle(ctx)
        else -> Prefs.ttsVoice(ctx)
    }

    fun setVoice(ctx: Context, provider: String, id: String) = when (provider) {
        "gemini" -> Prefs.setTtsVoiceGemini(ctx, id)
        "google" -> Prefs.setTtsVoiceGoogle(ctx, id)
        else -> Prefs.setTtsVoice(ctx, id)
    }

    fun voiceOptions(ctx: Context, provider: String): List<VoiceOption> =
        VoiceCatalog.fromJson(Prefs.voicesCache(ctx, provider)).ifEmpty { VoiceCatalog.builtIn(provider) }

    fun hasCloudVoices(ctx: Context, provider: String) = VoiceCatalog.fromJson(Prefs.voicesCache(ctx, provider)).isNotEmpty()

    private suspend fun get(url: String, headers: Map<String, String>): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        for ((k, v) in headers) c.setRequestProperty(k, v)
        val status = c.responseCode
        status to (if (status in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
    }

    private var geminiZhCount = 0

    /** 从云端拉取这家的声音（只留中英文），成功就存到手机。返回 (列表, 出错原因或提示) */
    @Suppress("UNCHECKED_CAST")
    suspend fun fetchVoices(ctx: Context, provider: String): Pair<List<VoiceOption>, String?> {
        val list: List<VoiceOption> = try {
            when (provider) {
                "gemini" -> {
                    val key = geminiKey(ctx)
                    if (key.isBlank()) return emptyList<VoiceOption>() to notConfiguredText(ctx)
                    // 分两次查：先让服务器只返回中文声音，再查英文；声音库很大，一次拉全部中文会被英文淹没
                    suspend fun query(langs: List<String>, maxPages: Int): List<VoiceOption> {
                        val out = ArrayList<VoiceOption>()
                        var token: String? = null
                        var page = 0
                        do {
                            val (status, body) = get(VoiceCatalog.geminiUrl(geminiUrl(ctx), token, langs), mapOf("x-goog-api-key" to key))
                            if (status !in 200..299) {
                                val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
                                throw IllegalStateException(GeminiSpeech.errorText(status, m, "获取声音"))
                            }
                            val (items, next) = VoiceCatalog.parseGemini(MiniJson.parse(body))
                            out.addAll(items)
                            token = next
                            page++
                        } while (token != null && page < maxPages)
                        return out
                    }
                    val zh = try { query(VoiceCatalog.GEMINI_ZH_CODES, 3) } catch (e: IllegalStateException) { return emptyList<VoiceOption>() to (e.message ?: "获取失败") }
                    val en = try { query(VoiceCatalog.GEMINI_EN_CODES, 2) } catch (e: IllegalStateException) { emptyList() }
                    // 官方工作室声音一定要有（能念中文），英文查询里没带回来的话补上内置的
                    val studio = VoiceCatalog.builtIn("gemini").map { it.copy(lang = "mul", label = it.id + "（多语言，能念中文）") }
                    geminiZhCount = zh.count { VoiceCatalog.isZh(it.lang) }
                    VoiceCatalog.sort(zh + en + studio)
                }
                "google" -> {
                    val key = Prefs.googleSpeechKey(ctx)
                    if (key.isBlank()) return emptyList<VoiceOption>() to notConfiguredText(ctx)
                    val (status, body) = get(VoiceCatalog.googleUrl(key), emptyMap())
                    if (status !in 200..299) {
                        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }
                        return emptyList<VoiceOption>() to GoogleSpeech.errorText(status, m, "获取声音")
                    }
                    VoiceCatalog.parseGoogle(MiniJson.parse(body))
                }
                else -> {
                    val key = Prefs.azureSpeechKey(ctx)
                    val region = AzureSpeech.cleanRegion(Prefs.azureSpeechRegion(ctx))
                    if (key.isBlank() || region == null) return emptyList<VoiceOption>() to notConfiguredText(ctx)
                    val (status, body) = get(VoiceCatalog.azureUrl(region), mapOf("Ocp-Apim-Subscription-Key" to key))
                    if (status !in 200..299) return emptyList<VoiceOption>() to AzureTts.errorText(status)
                    VoiceCatalog.parseAzure(MiniJson.parse(body))
                }
            }
        } catch (e: Exception) {
            return emptyList<VoiceOption>() to "连不上服务器，请检查网络" + (if (provider != "azure") "（国内一般需要代理）" else "")
        }
        if (list.isEmpty()) return list to "没有拉到中文或英文的声音"
        Prefs.setVoicesCache(ctx, provider, VoiceCatalog.toJson(list))
        if (provider == "gemini" && geminiZhCount == 0) {
            return list to "已获取 ${list.size} 个声音。Gemini 声音库里目前没有标为中文的声音；列表最上面标着“多语言，能念中文”的官方声音都能念中文"
        }
        return list to null
    }

    /** 检查 Gemini Key：能列出模型就说明可用 */
    suspend fun geminiCheckKey(ctx: Context): String? {
        val (list, err) = AiClient.listModels("gemini", geminiKey(ctx), geminiUrl(ctx))
        return if (list.isNotEmpty()) null else (err ?: "检查失败")
    }

    private suspend fun post(url: String, body: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val c = URL(url).openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.connectTimeout = 15_000
        c.readTimeout = 30_000
        c.doOutput = true
        c.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val status = c.responseCode
        status to (if (status in 200..299) c.inputStream else (c.errorStream ?: c.inputStream)).bufferedReader().use { it.readText() }
    }

    /** Google 说话转文字 */
    suspend fun googleRecognize(key: String, pcm: ByteArray): AzureSpeech.Result = try {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        val (status, body) = post(GoogleSpeech.recognizeUrl(key), GoogleSpeech.recognizeBody(b64))
        GoogleSpeech.parseRecognize(status, body)
    } catch (e: Exception) {
        AzureSpeech.Result.Fail("连不上 Google 语音服务，请检查网络（国内一般需要代理）")
    }

    /** Google 朗读：返回 mp3 的字节，失败抛出带原因的异常 */
    suspend fun googleSynthesize(key: String, text: String, voice: String, rate: String): ByteArray {
        val (status, body) = try { post(GoogleSpeech.ttsUrl(key), GoogleSpeech.ttsBody(text, voice, rate)) }
        catch (e: Exception) { throw IllegalStateException("连不上 Google 语音服务，请检查网络（国内一般需要代理）") }
        return when (val r = GoogleSpeech.parseTts(status, body)) {
            is GoogleSpeech.Audio.Ok -> Base64.decode(r.base64, Base64.DEFAULT)
            is GoogleSpeech.Audio.Fail -> throw IllegalStateException(r.message)
        }
    }

    /** 检查 Google Key：能列出中文语音就说明可用 */
    suspend fun googleCheckKey(key: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(GoogleSpeech.voicesUrl(key)).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 15_000
            val status = c.responseCode
            if (status in 200..299) null else {
                val body = (c.errorStream ?: c.inputStream).bufferedReader().use { it.readText() }
                @Suppress("UNCHECKED_CAST")
                GoogleSpeech.errorText(status, try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null }, "朗读")
            }
        } catch (e: Exception) {
            "连不上 Google：请检查网络（国内一般需要代理）"
        }
    }
}
