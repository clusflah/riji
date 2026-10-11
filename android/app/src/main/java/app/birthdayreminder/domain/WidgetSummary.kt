package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** 日记在小组件里只需要这几样：日期、更新时间、摘要文字 */
data class DiaryBrief(val date: String, val updatedAt: Long, val snippet: String)

/** 下一次要响的闹钟：标签 + 响铃时间(毫秒) */
data class AlarmBrief(val label: String, val at: Long)

/**
 * 生成桌面小组件用的数据汇总。
 *
 * 字段名、条数、排序规则，逐条对应旧版 www/index.html 里的 widgetSummary()，
 * 因为渲染小组件的 Java 代码（LiWidgetProvider）就是按这些字段名读的，一个字对不上，小组件就是空白。
 * 返回值是嵌套的 Map / List，纯逻辑、不碰 Android 类；转成 JSON 由 Android 侧的 WidgetUpdater 做。
 */
object WidgetSummary {
    private val WEEK = "日一二三四五六"

    private fun weekChar(d: LocalDate): Char = WEEK[d.dayOfWeek.value % 7]

    fun dateLabelFuture(k: LocalDate, today: LocalDate): String {
        val n = java.time.temporal.ChronoUnit.DAYS.between(today, k)
        if (n <= 0) return "今天"
        if (n == 1L) return "明天"
        if (n == 2L) return "后天"
        return "${k.monthValue}月${k.dayOfMonth}日 星期${weekChar(k)}"
    }

    fun dateLabelPast(k: LocalDate, today: LocalDate): String {
        val n = java.time.temporal.ChronoUnit.DAYS.between(k, today)
        if (n <= 0) return "今天"
        if (n == 1L) return "昨天"
        if (n == 2L) return "前天"
        return "${k.monthValue}月${k.dayOfMonth}日"
    }

    private fun birthdayText(b: Birthday, k: LocalDate): String {
        val age = Occurrence.ageAt(b, k)
        return b.name + "的生日" + (if (age != null) "（将满${age}岁）" else "")
    }

    private fun reminderText(r: Reminder): String = r.title + (if (r.time.isNotBlank()) " · " + r.time else "")

    private fun ms(d: LocalDate, hm: String?, zone: ZoneId): Long {
        val t = if (hm.isNullOrBlank()) LocalTime.MIDNIGHT else try { LocalTime.parse(hm) } catch (e: Exception) { LocalTime.MIDNIGHT }
        return d.atTime(t).atZone(zone).toInstant().toEpochMilli()
    }

    /** 迷你月历：周一开头，只画当月需要的行，其余补空到 42 格。event = 当天有生日，或有还没打勾的提醒 */
    fun miniGrid(
        today: LocalDate, birthdays: List<Birthday>, reminders: List<Reminder>
    ): Map<String, Any?> {
        val first = today.withDayOfMonth(1)
        val lead = first.dayOfWeek.value - 1
        val rows = Math.ceil((lead + today.lengthOfMonth()) / 7.0).toInt()
        val start = first.minusDays(lead.toLong())
        val cells = ArrayList<Map<String, Any?>>()
        for (i in 0 until rows * 7) {
            val k = start.plusDays(i.toLong())
            val event = birthdays.any { Occurrence.birthdayOn(it, k) } ||
                reminders.any { Occurrence.reminderOn(it, k) && it.done[k.toString()] != true }
            cells += mapOf(
                "d" to k.dayOfMonth,
                "inMonth" to (k.monthValue == today.monthValue && k.year == today.year),
                "today" to (k == today),
                "event" to event
            )
        }
        while (cells.size < 42) cells += mapOf("d" to 0, "inMonth" to false, "today" to false, "event" to false)
        return mapOf("y" to today.year, "m" to today.monthValue, "cells" to cells)
    }

    fun todayItem(
        today: LocalDate, birthdays: List<Birthday>, reminders: List<Reminder>, holidays: HolidayIndex
    ): Map<String, Any?>? {
        holidays.off[today]?.let { return mapOf("kind" to "holiday", "text" to it.name + " 假期") }
        birthdays.firstOrNull { Occurrence.birthdayOn(it, today) }?.let {
            return mapOf("kind" to "birthday", "text" to birthdayText(it, today))
        }
        reminders.firstOrNull { Occurrence.reminderOn(it, today) && it.done[today.toString()] != true }?.let {
            return mapOf("kind" to "reminder", "text" to reminderText(it))
        }
        return null
    }

    /** 接下来最近的两件事（从明天起 60 天内；每天先假期、再生日、再提醒） */
    fun agenda(
        today: LocalDate, zone: ZoneId, birthdays: List<Birthday>, reminders: List<Reminder>, holidays: HolidayIndex
    ): List<Map<String, Any?>> {
        val out = ArrayList<Map<String, Any?>>()
        var i = 1
        while (i <= 60 && out.size < 2) {
            val k = today.plusDays(i.toLong())
            val label = dateLabelFuture(k, today)
            holidays.off[k]?.let {
                out += mapOf("dateMs" to ms(k, null, zone), "label" to label, "kind" to "holiday", "text" to it.name + " 假期")
            }
            for (b in birthdays) {
                if (out.size < 2 && Occurrence.birthdayOn(b, k)) {
                    out += mapOf("dateMs" to ms(k, null, zone), "label" to label, "kind" to "birthday", "text" to birthdayText(b, k))
                }
            }
            for (r in reminders) {
                if (out.size < 2 && Occurrence.reminderOn(r, k) && r.done[k.toString()] != true) {
                    out += mapOf("dateMs" to ms(k, r.time, zone), "label" to label, "kind" to "reminder", "text" to reminderText(r))
                }
            }
            i++
        }
        return out.take(2)
    }

    fun build(
        today: LocalDate,
        zone: ZoneId,
        now: Long,
        birthdays: List<Birthday>,
        reminders: List<Reminder>,
        alarms: List<AlarmBrief>,
        diaries: List<DiaryBrief>,
        holidays: HolidayIndex
    ): Map<String, Any?> {
        // 私密内容不上桌面：从这里开始就当它们不存在
        @Suppress("NAME_SHADOWING") val birthdays = birthdays.filter { !it.isPrivate }
        @Suppress("NAME_SHADOWING") val reminders = reminders.filter { !it.isPrivate }
        val bAll = birthdays.mapNotNull { b ->
            Occurrence.nextBirthday(b, today, if (b.leap) 4000 else 400)?.let { (d, _) -> Triple(d, b, Occurrence.ageAt(b, d)) }
        }.sortedBy { it.first }

        val rAll = reminders.mapNotNull { r -> Occurrence.nextReminder(r, today, 400)?.let { it to r } }
            .sortedWith(compareBy({ it.first }, { it.second.time }))

        val aAll = alarms.sortedBy { it.at }

        val dAll = diaries.sortedWith(compareByDescending<DiaryBrief> { it.date }.thenByDescending { it.updatedAt })

        val lunar = LunarCache.of(today)
        val lunarPlain = app.birthdayreminder.lunar.LunarCalendar.monthLabel(lunar.month, lunar.isLeap) +
            app.birthdayreminder.lunar.LunarCalendar.dayLabel(lunar.day)
        val lunarDayOnly = app.birthdayreminder.lunar.LunarCalendar.dayLabel(lunar.day)

        return mapOf(
            "updatedAt" to now,
            "today" to mapOf(
                "label" to "${today.year}年${today.monthValue}月${today.dayOfMonth}日 周${weekChar(today)}",
                "lunar" to lunarPlain,
                "lunarPlain" to lunarPlain,
                "lunarDayOnly" to lunarDayOnly,
                "dayNum" to today.dayOfMonth,
                "weekLabel" to "周${weekChar(today)}"
            ),
            "grid" to miniGrid(today, birthdays, reminders),
            "todayItem" to todayItem(today, birthdays, reminders, holidays),
            "agendaList" to agenda(today, zone, birthdays, reminders, holidays),
            "birthdayList" to bAll.take(2).map { (d, b, age) ->
                val m = mutableMapOf<String, Any?>(
                    "dateMs" to ms(d, null, zone), "label" to dateLabelFuture(d, today), "name" to b.name
                )
                if (age != null) m["age"] = age
                m
            },
            "reminderList" to rAll.take(2).map { (d, r) ->
                mapOf("dateMs" to ms(d, r.time, zone), "label" to dateLabelFuture(d, today), "title" to r.title, "time" to r.time)
            },
            // 提醒小组件用的完整列表：先是没做完的无时间待办，再是接下来有日期的提醒，最多 12 条（能显示几条由小组件大小决定）
            "todoList" to (
                reminders.filter { Occurrence.isTodo(it) && !Occurrence.todoDone(it) }.sortedBy { it.title }
                    .map { mapOf("title" to it.title, "todo" to true, "id" to it.id) } +
                    rAll.map { (d, r) ->
                        // id + occ：小组件上点这一行时，知道要给哪条提醒的哪一天打勾
                        mapOf("title" to r.title, "todo" to false, "dateMs" to ms(d, r.time, zone), "time" to r.time,
                            "id" to r.id, "occ" to d.toString())
                    }
                ).take(12),
            "alarmList" to aAll.take(2).map { mapOf("atMs" to it.at, "label" to it.label) },
            "diaryList" to dAll.take(2).map { e ->
                val d = try { LocalDate.parse(e.date) } catch (ex: Exception) { today }
                mapOf("dateMs" to ms(d, null, zone), "label" to dateLabelPast(d, today), "text" to e.snippet)
            }
        )
    }
}
