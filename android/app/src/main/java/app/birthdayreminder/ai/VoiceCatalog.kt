package app.birthdayreminder.ai

/**
 * 语音服务的声音列表（纯逻辑）：从云端拉取后只留中文（含方言）和英文，排好序，存到手机上。
 * 三家返回的格式不同，各写一个解析。
 */
data class VoiceOption(val id: String, val label: String, val lang: String)

object VoiceCatalog {
    /** 中文（普通话、粤语、吴语、闽南语等）或英文 */
    fun isZh(lang: String): Boolean = lang.lowercase().let { it.startsWith("zh") || it.startsWith("cmn") || it.startsWith("yue") || it.startsWith("wuu") || it.startsWith("nan") }
    fun isEn(lang: String): Boolean = lang.lowercase().startsWith("en")
    fun isZhOrEn(lang: String): Boolean = isZh(lang) || isEn(lang)

    /** 声音名字只允许这些字符（防止把奇怪内容塞进请求） */
    fun safeId(id: String): Boolean = id.isNotBlank() && id.length <= 100 && id.all { it.isLetterOrDigit() || it in "-_:." }

    private fun langLabel(lang: String): String {
        val l = lang.lowercase()
        return when {
            l.startsWith("yue") || l == "zh-hk" -> "粤语"
            l.startsWith("wuu") -> "吴语"
            l.startsWith("nan") -> "闽南语"
            l == "zh-tw" || l.contains("hant") -> "中文（台湾）"
            isZh(l) -> "中文"
            l == "en-us" -> "英文（美国）"
            l == "en-gb" -> "英文（英国）"
            isEn(l) -> "英文（${lang.substringAfter('-', "")}）"
            l.isBlank() || l == "mul" || l.contains("multi") -> "多语言"
            else -> lang
        }
    }

    private fun genderLabel(g: String?): String = when (g?.lowercase()) {
        "female" -> "女"; "male" -> "男"; "neutral" -> "中性"; else -> ""
    }

    /** 排序用的分组：普通话 0、其它中文（台湾、粤语、吴语…）1、多语言 2、美式英文 3、其它英文 4 */
    private fun rank(lang: String): Int {
        val l = lang.lowercase()
        return when {
            l == "zh-cn" || l.startsWith("cmn-cn") || l == "cmn-hans-cn" -> 0
            isZh(l) -> 1
            !isEn(l) -> 2          // 多语言（Gemini 官方声音）
            l == "en-us" -> 3
            else -> 4
        }
    }

    /** 排序：普通话在前，然后其它中文、多语言，最后英文；同组按语言、名字 */
    fun sort(list: List<VoiceOption>): List<VoiceOption> = list.distinctBy { it.id }.sortedWith(
        compareBy<VoiceOption>({ rank(it.lang) }, { it.lang }, { it.label })
    )

    // ---------------- Azure：[{ShortName, LocalName, Gender, Locale, LocaleName}] ----------------
    @Suppress("UNCHECKED_CAST")
    fun parseAzure(json: Any?): List<VoiceOption> = sort((json as? List<Any?>).orEmpty().mapNotNull { x ->
        val m = x as? Map<String, Any?> ?: return@mapNotNull null
        val id = m["ShortName"] as? String ?: return@mapNotNull null
        val lang = m["Locale"] as? String ?: id.split("-").take(2).joinToString("-")
        if (!isZhOrEn(lang) || !safeId(id)) return@mapNotNull null
        val name = (m["LocalName"] as? String) ?: (m["DisplayName"] as? String) ?: id
        val g = genderLabel(m["Gender"] as? String)
        VoiceOption(id, "$name（${langLabel(lang)}${if (g.isNotEmpty()) "·$g" else ""}）", lang)
    })

    // ---------------- Google Cloud：{"voices":[{name, languageCodes, ssmlGender}]} ----------------
    @Suppress("UNCHECKED_CAST")
    fun parseGoogle(json: Any?): List<VoiceOption> = sort(((json as? Map<String, Any?>)?.get("voices") as? List<Any?>).orEmpty().mapNotNull { x ->
        val m = x as? Map<String, Any?> ?: return@mapNotNull null
        val id = m["name"] as? String ?: return@mapNotNull null
        val lang = ((m["languageCodes"] as? List<Any?>)?.firstOrNull() as? String) ?: GoogleSpeech.langOf(id)
        if (!isZhOrEn(lang) || !safeId(id)) return@mapNotNull null
        val g = genderLabel(m["ssmlGender"] as? String)
        VoiceOption(id, "$id（${langLabel(lang)}${if (g.isNotEmpty()) "·$g" else ""}）", lang)
    })

    // ---------------- Gemini ----------------
    /** 官方 30 个工作室声音：接口里标的是英文，但 Gemini 朗读会自动识别语言，这些声音都能念中文 */
    val GEMINI_STUDIO = setOf(
        "Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe",
        "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar",
        "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"
    )
    /** 让服务器只返回中文声音：普通话、台湾、粤语的各种写法都带上（多个值是“或”的关系） */
    val GEMINI_ZH_CODES = listOf("cmn-CN", "zh-CN", "cmn-Hans-CN", "zh-Hans-CN", "cmn-TW", "zh-TW", "cmn-Hant-TW", "yue-HK", "zh-HK", "yue-Hant-HK", "yue-CN")
    val GEMINI_EN_CODES = listOf("en-US", "en-GB")

    /** 描述只取第一个短语（"Soft, calm, and…" -> "Soft"），太长就截到 12 个字 */
    fun shortDesc(d: String?): String {
        val t = d.orEmpty().trim().split(',', '，', '.', '。', ';', '；').firstOrNull().orEmpty().trim()
        return if (t.length > 12) t.take(12) + "…" else t
    }

    // {"voices":[{id, display_name, language_code, gender, description}], next_page_token}
    @Suppress("UNCHECKED_CAST")
    fun parseGemini(json: Any?): Pair<List<VoiceOption>, String?> {
        val root = json as? Map<String, Any?> ?: return emptyList<VoiceOption>() to null
        val list = (root["voices"] as? List<Any?>).orEmpty().mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            val id = (m["id"] as? String)?.removePrefix("voices/") ?: (m["name"] as? String)?.removePrefix("voices/") ?: return@mapNotNull null
            val bareId = id.substringAfterLast('/')
            // 官方工作室声音能念任何语言，标成多语言
            val lang = if (bareId in GEMINI_STUDIO) "mul" else (m["language_code"] as? String) ?: (m["languageCode"] as? String) ?: ""
            // 多语言声音（没标语言）也能念中文，保留；标了别的语言的去掉
            if (lang.isNotBlank() && lang.lowercase() != "mul" && !lang.lowercase().contains("multi") && !isZhOrEn(lang)) return@mapNotNull null
            if (!safeId(id)) return@mapNotNull null
            val name = (m["display_name"] as? String) ?: (m["displayName"] as? String) ?: id
            val g = genderLabel(m["gender"] as? String)
            val desc = shortDesc((m["description"] as? String) ?: (m["persona"] as? String))
            val lab = if (lang == "mul") "多语言，能念中文" else langLabel(lang)
            VoiceOption(id, "$name（$lab${if (g.isNotEmpty()) "·$g" else ""}${if (desc.isNotEmpty()) "·$desc" else ""}）", lang)
        }
        return list to ((root["next_page_token"] as? String) ?: (root["nextPageToken"] as? String))?.takeIf { it.isNotBlank() }
    }

    /** 拉取地址 */
    fun azureUrl(region: String) = "https://$region.tts.speech.microsoft.com/cognitiveservices/voices/list"
    fun googleUrl(key: String) = "https://texttospeech.googleapis.com/v1/voices?key=" + java.net.URLEncoder.encode(key.trim(), "UTF-8")
    /** langs 不为空时让服务器按语言筛选（language_code 可以写多个） */
    fun geminiUrl(geminiChatUrl: String, pageToken: String?, langs: List<String> = emptyList()): String {
        val base = GeminiSpeech.interactionsUrl(geminiChatUrl).removeSuffix("/interactions")
        val sb = StringBuilder("$base/voices?page_size=1000")
        for (l in langs) sb.append("&language_code=").append(java.net.URLEncoder.encode(l, "UTF-8"))
        pageToken?.let { sb.append("&page_token=").append(java.net.URLEncoder.encode(it, "UTF-8")) }
        return sb.toString()
    }

    // ---------------- 内置的默认声音（没从云端拉取过时用） ----------------
    fun builtIn(provider: String): List<VoiceOption> = when (provider) {
        "gemini" -> GeminiSpeech.VOICES.map { VoiceOption(it.first, it.second, "") }
        "google" -> GoogleSpeech.VOICES.map { VoiceOption(it.first, it.second, "cmn-CN") }
        else -> AzureTts.VOICES.map { VoiceOption(it.first, it.second, "zh-CN") }
    }

    // ---------------- 存到手机 ----------------
    fun toJson(list: List<VoiceOption>): String = MiniJson.write(list.map { mapOf("id" to it.id, "label" to it.label, "lang" to it.lang) })

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String?): List<VoiceOption> = try {
        (MiniJson.parse(json ?: "[]") as? List<Any?>).orEmpty().mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            if (!safeId(id)) null else VoiceOption(id, (m["label"] as? String) ?: id, (m["lang"] as? String).orEmpty())
        }
    } catch (e: Exception) { emptyList() }
}
