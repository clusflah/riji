package app.birthdayreminder.domain

import java.time.LocalDate
import java.time.YearMonth

/**
 * “日程”视图（参照 Google 日历的日程列表）：把未来的事按 月份 → 周 → 天 分组。
 * 没有安排的日子不出现。纯逻辑，方便测试。
 */
object Agenda {
    data class Day(val date: LocalDate, val items: List<UpcomingItem>)
    data class Week(val start: LocalDate, val end: LocalDate, val days: List<Day>)
    data class Month(val month: YearMonth, val weeks: List<Week>)

    /** 这天所在周的第一天（weekStart：0 = 周日开头，1 = 周一开头） */
    fun weekStartOf(d: LocalDate, weekStart: Int): LocalDate {
        val dow = d.dayOfWeek.value % 7          // 周日 = 0 … 周六 = 6
        val back = (dow - weekStart + 7) % 7
        return d.minusDays(back.toLong())
    }

    /** 周标题：9月28日至10月4日；同一个月里写成 10月5日至11日 */
    fun weekLabel(start: LocalDate, end: LocalDate): String =
        if (start.monthValue == end.monthValue) "${start.monthValue}月${start.dayOfMonth}日至${end.dayOfMonth}日"
        else "${start.monthValue}月${start.dayOfMonth}日至${end.monthValue}月${end.dayOfMonth}日"

    /** 分组：按天合并，再按周、按月（月份以这周里第一个有事的日子为准，跨月的周不会重复出现） */
    fun group(items: List<UpcomingItem>, weekStart: Int, ensureDay: LocalDate? = null): List<Month> {
        val map = items.groupBy { it.date }.toSortedMap()
        // 今天即使没有安排也留一行，当作定位的标记
        if (ensureDay != null && ensureDay !in map) map[ensureDay] = emptyList()
        val days = map.map { (d, l) -> Day(d, l) }
        val weeks = days.groupBy { weekStartOf(it.date, weekStart) }.toSortedMap()
            .map { (s, l) -> Week(s, s.plusDays(6), l) }
        return weeks.groupBy { YearMonth.from(it.days.first().date) }.toSortedMap().map { (m, w) -> Month(m, w) }
    }
}
