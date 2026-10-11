package app.birthdayreminder.notify

import android.content.Context

/** 原生版自己的小设置（阶段7做完整设置页时再迁移）。 */
object Prefs {
    private const val FILE = "li_native_prefs"

    /** 每天统一提醒时间，生日提醒、全天提醒都用它，默认 09:00（和 JS 版默认值一致） */
    fun dailyTime(ctx: Context): String = SettingsStore.get(ctx).dailyTime

    fun setDailyTime(ctx: Context, value: String) {
        SettingsStore.update(ctx) { it.copy(dailyTime = value) }
    }

    /** 高德地图 Web端(JS API) Key，日记加位置时用。和旧版共用同一个 Key/安全密钥即可，不用重新申请。 */
    fun amapKey(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("amapKey", "") ?: ""

    fun setAmapKey(ctx: Context, value: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("amapKey", value).apply()
    }

    fun amapSecCode(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("amapSecCode", "") ?: ""

    fun setAmapSecCode(ctx: Context, value: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("amapSecCode", value).apply()
    }

    // ---------------- AI 助手设置（Key、模型、接口地址都只保存在本机） ----------------

    fun aiProvider(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiProvider", "gemini")
            ?.takeIf { it in app.birthdayreminder.ai.AiProviders.IDS } ?: "gemini"     // 已经去掉的服务商（如 Azure OpenAI）退回 Gemini

    fun setAiProvider(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("aiProvider", v).apply()
    }

    fun aiKey(ctx: Context, provider: String): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiKey_$provider", "") ?: ""

    fun setAiKey(ctx: Context, provider: String, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("aiKey_$provider", v.trim()).apply()
    }

    fun aiModel(ctx: Context, provider: String, default: String): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiModel_$provider", null)?.takeIf { it.isNotBlank() } ?: default

    fun setAiModel(ctx: Context, provider: String, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("aiModel_$provider", v.trim()).apply()
    }

    fun aiUrl(ctx: Context, provider: String, default: String): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiUrl_$provider", null)?.takeIf { it.isNotBlank() } ?: default

    fun setAiUrl(ctx: Context, provider: String, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("aiUrl_$provider", v.trim()).apply()
    }

    /** 对话记录（JSON 文本），最多留 60 条 */
    fun aiChatJson(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("aiChat", "[]") ?: "[]"

    fun setAiChatJson(ctx: Context, v: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("aiChat", v).apply()
    }

    /** 常用语里被折叠起来的分组 */
    fun phraseCollapsed(ctx: Context): Set<String> =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getStringSet("phraseCollapsed", emptySet())?.toSet() ?: emptySet()

    fun setPhraseCollapsed(ctx: Context, v: Set<String>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putStringSet("phraseCollapsed", HashSet(v)).apply()
    }

    /** AI 添加数据前先让用户确认（默认开） */
    fun aiConfirm(ctx: Context): Boolean = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("aiConfirm", true)
    fun setAiConfirm(ctx: Context, v: Boolean) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("aiConfirm", v).apply() }

    /** 健身：常用训练组合（JSON） */
    fun fitnessTemplatesJson(ctx: Context): String? = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("fitnessTemplates", null)
    fun setFitnessTemplatesJson(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("fitnessTemplates", v).apply() }

    // ---------------- 语音输入：手机自带 / Azure 语音 ----------------
    /**
     * 说话转文字用什么：service = 用下面选的语音服务（Azure / Google），system = 手机自带。
     * 没专门选过时：填了语音服务的 Key 就用服务，否则用手机自带。以前存的 "azure" 也当作 service。
     */
    fun voiceEngine(ctx: Context): String =
        when (ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("voiceEngine", null)) {
            "system" -> "system"
            "service", "azure" -> "service"
            else -> if (app.birthdayreminder.ai.SpeechConfig.configured(ctx)) "service" else "system"
        }
    fun setVoiceEngine(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("voiceEngine", v).apply() }
    fun azureSpeechKey(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("azureSpeechKey", "") ?: ""
    fun setAzureSpeechKey(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("azureSpeechKey", v.trim()).apply() }
    fun azureSpeechRegion(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("azureSpeechRegion", "") ?: ""
    fun setAzureSpeechRegion(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("azureSpeechRegion", v.trim()).apply() }

    // ---------------- AI 回复朗读（Azure 文字转语音） ----------------
    fun ttsAuto(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsAuto", "voice") ?: "voice"
    fun setTtsAuto(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsAuto", v).apply() }
    fun ttsVoice(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsVoice", "zh-CN-XiaoxiaoNeural") ?: "zh-CN-XiaoxiaoNeural"
    fun setTtsVoice(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsVoice", v).apply() }
    fun ttsRate(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsRate", "normal") ?: "normal"
    fun setTtsRate(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsRate", v).apply() }

    // ---------------- 语音服务提供商：Azure / Google（说话转文字、朗读共用） ----------------
    fun speechProvider(ctx: Context): String =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("speechProvider", null)
            ?: when {
                azureSpeechKey(ctx).isNotBlank() -> "azure"
                googleSpeechKey(ctx).isNotBlank() -> "google"
                aiKey(ctx, "gemini").isNotBlank() -> "gemini"      // 填过 Gemini Key 就默认用 Gemini，不用另外申请
                else -> "azure"
            }
    fun setSpeechProvider(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("speechProvider", v).apply() }
    fun googleSpeechKey(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("googleSpeechKey", "") ?: ""
    fun setGoogleSpeechKey(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("googleSpeechKey", v.trim()).apply() }
    fun ttsVoiceGemini(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsVoiceGemini", "Kore") ?: "Kore"
    fun setTtsVoiceGemini(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsVoiceGemini", v).apply() }
    fun ttsVoiceGoogle(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsVoiceGoogle", "cmn-CN-Wavenet-A") ?: "cmn-CN-Wavenet-A"
    fun setTtsVoiceGoogle(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsVoiceGoogle", v).apply() }

    /** 从云端拉到的声音列表（每家服务商各存一份 JSON） */
    fun voicesCache(ctx: Context, provider: String): String? = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("voices_$provider", null)
    fun setVoicesCache(ctx: Context, provider: String, json: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("voices_$provider", json).apply() }

    /** 朗读引擎：cloud = 所选语音服务（失败自动改用手机自带），system = 一直用手机自带 */
    fun ttsEngine(ctx: Context): String = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("ttsEngine", "cloud") ?: "cloud"
    fun setTtsEngine(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("ttsEngine", v).apply() }

    /** 健身项目设置（顺序、显示/隐藏、自定义项目），JSON */
    fun fitnessFieldsJson(ctx: Context): String? = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("fitnessFields", null)
    fun setFitnessFieldsJson(ctx: Context, v: String) { ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("fitnessFields", v).apply() }

    /** 关掉的功能（功能总开关） */
    fun disabledModules(ctx: Context): Set<String> =
        app.birthdayreminder.domain.Modules.decode(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("disabledModules", ""))
    fun setDisabledModules(ctx: Context, v: Set<String>) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("disabledModules", app.birthdayreminder.domain.Modules.encode(v)).apply()
    }

    /** 某项功能有没有开（后台的提醒、闹钟也用它判断） */
    fun moduleOn(ctx: Context, key: String): Boolean =
        app.birthdayreminder.domain.Modules.isOn(disabledModules(ctx), key, app.birthdayreminder.Features.allergy)

    // ---------------- 生理期 ----------------
    private fun sp(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
    /** 避孕模式：不显示、不提醒易孕期和排卵日 */
    fun cycleBirthControl(ctx: Context) = sp(ctx).getBoolean("cycleBC", false)
    fun setCycleBirthControl(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("cycleBC", v).apply()
    /** 没有足够记录时用的默认周期 / 经期天数 */
    fun cycleDefaultLength(ctx: Context) = sp(ctx).getInt("cycleLen", 28)
    fun setCycleDefaultLength(ctx: Context, v: Int) = sp(ctx).edit().putInt("cycleLen", v.coerceIn(15, 60)).apply()
    fun cycleDefaultPeriod(ctx: Context) = sp(ctx).getInt("cyclePeriod", 5)
    fun setCycleDefaultPeriod(ctx: Context, v: Int) = sp(ctx).edit().putInt("cyclePeriod", v.coerceIn(1, 12)).apply()
    /** 经期前几天提醒（0 = 不提醒） */
    fun cycleRemindBefore(ctx: Context) = sp(ctx).getInt("cycleBefore", 2)
    fun setCycleRemindBefore(ctx: Context, v: Int) = sp(ctx).edit().putInt("cycleBefore", v.coerceIn(0, 7)).apply()
    fun cycleRemindPms(ctx: Context) = sp(ctx).getBoolean("cyclePms", true)
    fun setCycleRemindPms(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("cyclePms", v).apply()
    fun cycleRemindOvulation(ctx: Context) = sp(ctx).getBoolean("cycleOv", false)
    fun setCycleRemindOvulation(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("cycleOv", v).apply()
    /** 避孕药：用的是“用药就医”里的哪种药 */
    fun pillMedId(ctx: Context) = sp(ctx).getString("pillMed", "") ?: ""
    fun setPillMedId(ctx: Context, v: String) = sp(ctx).edit().putString("pillMed", v).apply()

    /** 高刷新率（默认开） */
    fun highRefresh(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("highRefresh", true)
    fun setHighRefresh(ctx: Context, v: Boolean) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("highRefresh", v).apply()

    /** 首页“上滑查看日程”提示：只显示一次 */
    fun swipeHintShown(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean("swipeHintShown", false)
    fun setSwipeHintShown(ctx: Context) = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean("swipeHintShown", true).apply()
}
