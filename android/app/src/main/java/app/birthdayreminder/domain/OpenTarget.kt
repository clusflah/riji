package app.birthdayreminder.domain

/**
 * 桌面小组件（和其它入口）点开 App 时带的“想看哪一页”，映射成 App 里的页面。
 * 单数复数都认。返回 null 表示留在首页日历。
 */
object OpenTarget {
    /** 启动 App 的 Intent 里放“想看哪一页”的字段名（和 LiWidgetProvider.EXTRA_OPEN 是同一个） */
    const val EXTRA = "li_open"

    /**
     * “全部”页各标签的顺序，也是“+”菜单的顺序：
     * 日记、提醒、健身数据、闹钟、过敏记录、用药就医、生理期、常用语、生日
     */
    const val TAB_DIARY = 0
    const val TAB_REMINDER = 1
    const val TAB_FITNESS = 2
    const val TAB_ALARM = 3
    const val TAB_ALLERGY = 4
    const val TAB_MED = 5
    const val TAB_CYCLE = 6
    const val TAB_PHRASE = 7
    const val TAB_BIRTHDAY = 8
    /** 学习：后加的，编号排在最后（显示顺序由各处的列表决定，放在健身后面） */
    const val TAB_STUDY = 9
    const val TAB_COUNT = 10

    /** 当前版本实际有哪些标签（按显示顺序）。无过敏记录版没有 TAB_ALLERGY */
    fun tabs(allergy: Boolean = true): List<Int> = (0 until TAB_COUNT).filter { allergy || it != TAB_ALLERGY }

    fun route(open: String?, allergy: Boolean = true): String? = when (open?.trim()?.lowercase()) {
        "diary", "notes" -> "list?tab=$TAB_DIARY"
        "reminder", "reminders", "todo" -> "list?tab=$TAB_REMINDER"
        "fitness" -> "list?tab=$TAB_FITNESS"
        "alarm", "alarms" -> "list?tab=$TAB_ALARM"
        "allergy" -> if (allergy) "list?tab=$TAB_ALLERGY" else null
        "med", "medicine", "medication" -> "list?tab=$TAB_MED"
        "cycle", "period" -> "list?tab=$TAB_CYCLE"
        "phrase", "phrases" -> "list?tab=$TAB_PHRASE"
        "birthday", "birthdays" -> "list?tab=$TAB_BIRTHDAY"
        "study" -> "list?tab=$TAB_STUDY"
        "settings" -> "settings"
        "assistant" -> "assistant"
        else -> null        // calendar / holiday / 空 / 不认识 = 留在首页
    }

    /** 路由里带的标签编号 -> 标签；当前版本没有这个标签（或者乱写的）就回到第一个 */
    fun tabOf(arg: String?, allergy: Boolean = true): Int = arg?.toIntOrNull()?.takeIf { it in tabs(allergy) } ?: TAB_DIARY
}
