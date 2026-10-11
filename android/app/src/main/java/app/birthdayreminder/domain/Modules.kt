package app.birthdayreminder.domain

/**
 * 功能总开关（纯逻辑）：每项功能可以整个关掉。关掉后界面、提醒、闹钟、小组件、搜索、AI 里都不再出现，
 * 但数据不删，重新打开就全部回来。
 */
object Modules {
    const val DIARY = "diary"
    const val REMINDER = "reminder"
    const val FITNESS = "fitness"
    const val ALARM = "alarm"
    const val ALLERGY = "allergy"
    const val MED = "med"
    const val PHRASE = "phrase"
    const val BIRTHDAY = "birthday"
    const val AI = "ai"
    const val CYCLE = "cycle"

    data class Module(val key: String, val label: String, val desc: String)

    val ALL = listOf(
        Module(DIARY, "日记", "日记、图片、附件、位置"),
        Module(REMINDER, "提醒", "有日期的提醒和无时间待办，以及它们的通知"),
        Module(FITNESS, "健身数据", "每天的训练、体重记录和统计"),
        Module(ALARM, "闹钟", "工作日 / 休息日 / 自定义闹钟（关掉后所有闹钟都不响）"),
        Module(ALLERGY, "过敏记录", "眼睛过敏、鼻子过敏、咳嗽"),
        Module(MED, "用药就医", "药品、服药提醒、就医记录、复诊提醒"),
        Module(CYCLE, "生理期", "经期记录与预测、易孕期、经前期提醒、避孕药打卡"),
        Module(PHRASE, "常用语", "保存要经常复制的信息"),
        Module(BIRTHDAY, "生日", "生日和生日通知"),
        Module(AI, "AI 助手", "对话、语音、周报月报、日记 AI 润色")
    )

    /** 这个版本里有哪些功能（无过敏记录版没有过敏记录） */
    fun available(allergyBuilt: Boolean): List<Module> = ALL.filter { allergyBuilt || it.key != ALLERGY }

    fun isOn(disabled: Set<String>, key: String, allergyBuilt: Boolean = true): Boolean =
        key !in disabled && (allergyBuilt || key != ALLERGY)

    /** “全部”页标签编号 -> 对应的功能 */
    val TAB_MODULE = mapOf(
        OpenTarget.TAB_DIARY to DIARY, OpenTarget.TAB_REMINDER to REMINDER, OpenTarget.TAB_FITNESS to FITNESS,
        OpenTarget.TAB_ALARM to ALARM, OpenTarget.TAB_ALLERGY to ALLERGY, OpenTarget.TAB_MED to MED, OpenTarget.TAB_CYCLE to CYCLE,
        OpenTarget.TAB_PHRASE to PHRASE, OpenTarget.TAB_BIRTHDAY to BIRTHDAY
    )

    /** 现在能看到的标签（按原来的顺序） */
    fun tabs(disabled: Set<String>, allergyBuilt: Boolean): List<Int> =
        (0 until OpenTarget.TAB_COUNT).filter { t -> TAB_MODULE[t]?.let { isOn(disabled, it, allergyBuilt) } ?: true }

    /** AI 工具 -> 所属功能（关掉的功能，AI 也不能查、不能加） */
    private val TOOL_MODULE = mapOf(
        "add_birthday" to BIRTHDAY, "list_birthdays" to BIRTHDAY,
        "add_reminder" to REMINDER, "list_reminders" to REMINDER,
        "add_alarm" to ALARM, "list_alarms" to ALARM,
        "add_diary" to DIARY, "list_diary" to DIARY,
        "query_fitness" to FITNESS, "query_allergy" to ALLERGY
    )

    fun toolAllowed(disabled: Set<String>, tool: String): Boolean = TOOL_MODULE[tool]?.let { it !in disabled } ?: true

    // ---------------- 存储：关掉的功能存成逗号分隔 ----------------
    fun encode(disabled: Set<String>): String = disabled.filter { k -> ALL.any { it.key == k } }.sorted().joinToString(",")
    fun decode(s: String?): Set<String> = s.orEmpty().split(",").map { it.trim() }.filter { k -> ALL.any { it.key == k } }.toSet()
}
