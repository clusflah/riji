package app.birthdayreminder.domain

/**
 * 首页底部的一级导航：日历 / 记录 / 健康 / 更多。
 * 每个区域里放几个标签（“全部”页的标签编号），关掉的功能自动不出现；一个标签都没有的区域整个隐藏（日历永远在）。
 */
object HomeSections {
    const val CALENDAR = 0
    const val RECORD = 1
    const val HEALTH = 2
    const val MORE = 3

    val NAMES = listOf("日历", "记录", "健康", "更多")

    /** 每个区域里的标签（按这个顺序显示） */
    fun tabs(section: Int): List<Int> = when (section) {
        RECORD -> listOf(OpenTarget.TAB_DIARY, OpenTarget.TAB_REMINDER, OpenTarget.TAB_FITNESS, OpenTarget.TAB_STUDY)
        HEALTH -> listOf(OpenTarget.TAB_ALLERGY, OpenTarget.TAB_MED, OpenTarget.TAB_CYCLE)
        MORE -> listOf(OpenTarget.TAB_BIRTHDAY, OpenTarget.TAB_ALARM, OpenTarget.TAB_PHRASE)
        else -> emptyList()
    }

    /** 某个标签在哪个区域（比如通知、小组件要打开“生理期”，就切到“健康”） */
    fun sectionOf(tab: Int): Int = listOf(RECORD, HEALTH, MORE).firstOrNull { tab in tabs(it) } ?: CALENDAR

    /** 现在能看到的区域：日历永远在；其它区域至少有一个标签开着才显示 */
    fun visible(enabledTabs: List<Int>): List<Int> =
        listOf(CALENDAR) + listOf(RECORD, HEALTH, MORE).filter { s -> tabs(s).any { it in enabledTabs } }
}
