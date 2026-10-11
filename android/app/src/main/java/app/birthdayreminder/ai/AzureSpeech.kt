package app.birthdayreminder.ai

import java.io.ByteArrayOutputStream

/**
 * Azure 语音转文字（短音频 REST 接口，一次最长 60 秒）的纯逻辑部分：
 * 地址怎么拼、录音怎么包成 WAV、返回结果怎么读。真正录音和联网在 Android 侧（AzureSpeechRecorder）。
 */
object AzureSpeech {
    const val SAMPLE_RATE = 16000
    const val MAX_SECONDS = 55       // 接口上限 60 秒，留一点余量
    const val MIN_MS = 500           // 按得太短（不到半秒）就不发了

    /** 区域写法：小写字母和数字，比如 eastasia、southeastasia、chinaeast2；写错的返回 null */
    fun cleanRegion(region: String): String? =
        region.trim().lowercase().takeIf { it.matches(Regex("^[a-z0-9]{3,30}$")) }

    fun recognizeUrl(region: String, language: String = "zh-CN"): String =
        "https://$region.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1?language=$language&format=simple"

    /** 用来检查 Key 和区域对不对的地址（换一个临时令牌，成功说明 Key 可用） */
    fun tokenUrl(region: String): String = "https://$region.api.cognitive.microsoft.com/sts/v1.0/issueToken"

    /** 16 位单声道 PCM -> 完整的 WAV 文件（44 字节文件头 + 数据） */
    fun wav(pcm: ByteArray, sampleRate: Int = SAMPLE_RATE): ByteArray {
        val out = ByteArrayOutputStream(44 + pcm.size)
        fun le32(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff); out.write((v shr 16) and 0xff); out.write((v shr 24) and 0xff) }
        fun le16(v: Int) { out.write(v and 0xff); out.write((v shr 8) and 0xff) }
        out.write("RIFF".toByteArray()); le32(36 + pcm.size); out.write("WAVE".toByteArray())
        out.write("fmt ".toByteArray()); le32(16); le16(1); le16(1); le32(sampleRate); le32(sampleRate * 2); le16(2); le16(16)
        out.write("data".toByteArray()); le32(pcm.size); out.write(pcm)
        return out.toByteArray()
    }

    sealed class Result {
        data class Ok(val text: String) : Result()
        data class Fail(val message: String) : Result()
    }

    /** 读识别结果：{"RecognitionStatus":"Success","DisplayText":"…"} */
    @Suppress("UNCHECKED_CAST")
    fun parse(status: Int, body: String): Result {
        if (status == 401 || status == 403) return Result.Fail("Azure 语音的 Key 或区域不对，请检查 AI 设置里的“语音输入”")
        if (status == 429) return Result.Fail("Azure 语音请求太频繁或本月免费额度用完了，稍后再试")
        if (status !in 200..299) return Result.Fail("Azure 语音识别失败（$status）")
        val m = try { MiniJson.parse(body) as? Map<String, Any?> } catch (e: Exception) { null } ?: return Result.Fail("Azure 语音返回的内容看不懂")
        return when (m["RecognitionStatus"]) {
            "Success" -> {
                val t = (m["DisplayText"] as? String)?.trim().orEmpty()
                if (t.isEmpty()) Result.Fail("没有听清，再说一次试试") else Result.Ok(t)
            }
            "NoMatch", "InitialSilenceTimeout", "BabbleTimeout" -> Result.Fail("没有听清，再说一次试试")
            else -> Result.Fail("Azure 语音识别失败：" + (m["RecognitionStatus"] ?: "未知原因"))
        }
    }
}

/**
 * Azure 文字转语音（神经网络语音）的纯逻辑：朗读前清理文字、拼 SSML、地址。
 * 和语音转文字共用同一个“语音服务”资源的 Key 和区域。
 */
object AzureTts {
    /** 可选的声音：名字 -> 显示 */
    val VOICES = listOf(
        "zh-CN-XiaoxiaoNeural" to "晓晓（女声，温暖）",
        "zh-CN-YunxiNeural" to "云希（男声，阳光）",
        "zh-CN-XiaoyiNeural" to "晓伊（女声，活泼）",
        "zh-CN-YunjianNeural" to "云健（男声，沉稳）"
    )
    const val DEFAULT_VOICE = "zh-CN-XiaoxiaoNeural"

    /** 语速：显示 -> SSML 里的 rate */
    val RATES = listOf("slow" to "慢", "normal" to "正常", "fast" to "快")
    fun rateValue(rate: String): String = when (rate) { "slow" -> "-20%"; "fast" -> "+20%"; else -> "0%" }

    /** 自动朗读：关闭 / 语音提问时 / 总是 */
    val AUTO = listOf("off" to "关闭", "voice" to "语音提问时", "always" to "总是")
    const val DEFAULT_AUTO = "voice"

    const val MAX_CHARS = 1500     // 太长的回复只念前面一部分，免得等很久

    fun url(region: String): String = "https://$region.tts.speech.microsoft.com/cognitiveservices/v1"
    const val OUTPUT_FORMAT = "audio-24khz-48kbitrate-mono-mp3"

    /** 去掉 Markdown 符号和链接地址，多余空白压缩，太长截断；念出来自然一些 */
    fun clean(text: String): String {
        var t = text
        t = t.replace(Regex("```[\\s\\S]*?```"), " ")                       // 代码块整段不念
        t = t.replace(Regex("!?\\[([^\\]]*)]\\([^)]*\\)"), "$1")            // [文字](链接) -> 文字
        t = t.replace(Regex("https?://\\S+"), " ")                          // 裸链接不念
        t = t.replace(Regex("(?m)^\\s{0,3}#{1,6}\\s*"), "")                  // 标题井号
        t = t.replace(Regex("(?m)^\\s*[-*+•]\\s+"), "")                      // 列表符号
        t = t.replace(Regex("(?m)^\\s*>\\s?"), "")                           // 引用
        t = t.replace(Regex("[*_`~|]+"), "")                                 // 加粗、斜体、行内代码、表格竖线
        // 每行去掉首尾空白，空行合并（代码块、链接去掉后可能留下只有空格的行）
        t = t.lines().map { it.replace(Regex("[ \\t]+"), " ").trim() }.filter { it.isNotEmpty() }.joinToString("\n")
        return if (t.length > MAX_CHARS) t.take(MAX_CHARS) + "……" else t
    }

    fun escapeXml(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** 声音名里的语言：en-US-JennyNeural -> en-US */
    fun langOf(voice: String): String = voice.split("-").take(2).joinToString("-").ifBlank { "zh-CN" }

    fun ssml(text: String, voice: String, rate: String): String {
        // 内置的或从云端拉到的声音都能用；名字不合规就用默认
        val v = if (VoiceCatalog.safeId(voice) && voice.contains("-")) voice else DEFAULT_VOICE
        return "<speak version='1.0' xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='${langOf(v)}'>" +
            "<voice name='$v'><prosody rate='${rateValue(rate)}'>${escapeXml(clean(text))}</prosody></voice></speak>"
    }

    /** 这次该不该自动朗读 */
    fun shouldAutoSpeak(auto: String, askedByVoice: Boolean): Boolean = when (auto) {
        "always" -> true
        "voice" -> askedByVoice
        else -> false
    }

    fun errorText(status: Int): String = when (status) {
        401, 403 -> "Azure 语音的 Key 或区域不对，请检查 AI 设置里的“语音”"
        429 -> "Azure 语音请求太频繁或本月免费额度用完了"
        400 -> "朗读失败：这段文字没法朗读"
        else -> "朗读失败（$status）"
    }
}

/** 手机自带朗读（Android TextToSpeech）的小规则（纯逻辑） */
object SystemTtsLogic {
    /** 设置里的“慢 / 正常 / 快” -> 系统朗读的语速倍数 */
    fun rate(rate: String): Float = when (rate) { "slow" -> 0.8f; "fast" -> 1.25f; else -> 1.0f }

    /** 用什么语言念：几乎全是英文字母（≥90%，且没有汉字）就用英文，否则用中文 */
    fun isEnglish(text: String): Boolean {
        val letters = text.filter { it.isLetter() }
        if (letters.isEmpty()) return false
        if (letters.any { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }) return false
        return letters.count { it in 'a'..'z' || it in 'A'..'Z' } * 10 >= letters.length * 9
    }

    /** 朗读引擎：cloud = 所选语音服务（失败自动改用手机），system = 一直用手机自带 */
    val ENGINES = listOf("cloud" to "云端", "system" to "手机自带")

    /** 云端失败时给用户看的一句话（只在每次打开 App 后第一次回退时提示） */
    fun fallbackNotice(reason: String): String = "云端朗读暂时不可用（$reason），已改用手机自带朗读"
}
