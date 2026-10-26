package app.birthdayreminder.ui.common

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.birthdayreminder.Features
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.domain.OpenTarget
import app.birthdayreminder.notify.Prefs

/**
 * 首页日程区域的标签：顺序、显示哪些可以在“设置 → 首页标签”里改，只影响首页，不像“功能开关”那样把整个功能关掉。
 * “全部”永远在第一个、不能隐藏。改了之后 version 加一，首页跟着刷新。
 */
object HomeTabs {
    const val ALL = -1

    /** 默认顺序：全部、日记、提醒，其它在后 */
    val DEFAULT = listOf(ALL to "全部", OpenTarget.TAB_DIARY to "日记", OpenTarget.TAB_REMINDER to "提醒",
        OpenTarget.TAB_FITNESS to "健身", OpenTarget.TAB_STUDY to "学习", OpenTarget.TAB_ALARM to "闹钟", OpenTarget.TAB_MED to "用药就医", OpenTarget.TAB_CYCLE to "生理期",
        OpenTarget.TAB_ALLERGY to "过敏", OpenTarget.TAB_PHRASE to "常用语", OpenTarget.TAB_BIRTHDAY to "生日")

    /** 跟着选中的日子变的标签（其它标签显示的是那一项的全部数据，和选中哪天无关） */
    val DATED = setOf(ALL, OpenTarget.TAB_CYCLE)

    val version = mutableIntStateOf(0)

    /** 功能开着的标签，按设置的顺序（没排过的按默认顺序放在后面） */
    fun ordered(ctx: Context): List<Pair<Int, String>> {
        val on = Modules.tabs(ModuleStore.disabled(ctx), Features.allergy)
        val avail = DEFAULT.filter { (t, _) -> t == ALL || t in on }
        val order = Prefs.homeTabOrder(ctx)
        val rest = avail.drop(1).sortedBy { (t, _) -> order.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it } }
        return listOf(avail.first()) + rest
    }

    /** 首页上显示的：去掉隐藏的 */
    fun visible(ctx: Context): List<Pair<Int, String>> {
        val hidden = Prefs.homeTabHidden(ctx)
        return ordered(ctx).filter { (t, _) -> t == ALL || t !in hidden }
    }

    fun save(ctx: Context, order: List<Int>, hidden: Set<Int>) {
        Prefs.setHomeTabs(ctx, order.filter { it != ALL }, hidden - ALL)
        version.intValue++
    }
}
