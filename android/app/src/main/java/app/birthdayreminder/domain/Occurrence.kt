package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.lunar.LunarCalendar
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 农历换算有点费时（要从1900年往后数），按日期缓存一下，日历翻月/通知调度都会反复用到。 */
object LunarCache {
    private val map = HashMap<LocalDate, LunarCalendar.LunarDate>()

    @Synchronized
    fun of(d: LocalDate): LunarCalendar.LunarDate =
        map.getOrPut(d) { LunarCalendar.solarToLunar(d.year, d.monthValue, d.dayOfMonth) }
}

/**
 * 逐日判断“某天有没有生日/提醒”，规则逐条对应 JS 版 www/index.html 里的
 * lunarMatch / solarMatch / occursOn，行为保持一致：
 *  - 农历生日：月份、是否闰月都要对上；生日在三十、当月只有二十九天时，落在二十九
 *  - 阳历生日：2月29日出生的，非闰年在2月28日过
 *  - 每月/每年重复：日期超出当月天数时取当月最后一天
 */
object Occurrence {

    fun birthdayOn(b: Birthday, d: LocalDate): Boolean =
        if (b.type == "lunar") lunarMatch(b, d) else solarMatch(b, d)

    private fun lunarMatch(b: Birthday, d: LocalDate): Boolean {
        val l = LunarCache.of(d)
        if (l.month != b.month || l.isLeap != b.leap) return false
        if (l.day == b.day) return true
        if (b.day == 30 && l.day == 29) return LunarCache.of(d.plusDays(1)).day == 1
        return false
    }

    private fun solarMatch(b: Birthday, d: LocalDate): Boolean {
        if (b.month == d.monthValue && b.day == d.dayOfMonth) return true
        return b.month == 2 && b.day == 29 && d.monthValue == 2 && d.dayOfMonth == 28 && !d.isLeapYear
    }

    /** 在 d 这天过的是几岁生日，没填出生年份返回 null */
    fun ageAt(b: Birthday, d: LocalDate): Int? {
        val y = b.year ?: return null
        val n = if (b.type == "lunar") LunarCache.of(d).year - y else d.year - y
        return if (n > 0) n else null
    }

    fun birthdayDesc(b: Birthday): String {
        var s = if (b.type == "lunar") {
            "农历 " + LunarCalendar.monthLabel(b.month, b.leap) + LunarCalendar.dayLabel(b.day)
        } else {
            "公历 ${b.month}月${b.day}日"
        }
        b.year?.let { s += " · ${it}年" }
        return s
    }

    /** 从 from 起（含当天）往后找下一个生日，返回 日期 to 还有几天；闰月生日可能隔好几年，所以上限放宽 */
    fun nextBirthday(b: Birthday, from: LocalDate, limit: Int = if (b.leap) 4000 else 400): Pair<LocalDate, Int>? {
        for (i in 0..limit) {
            val k = from.plusDays(i.toLong())
            if (birthdayOn(b, k)) return k to i
        }
        return null
    }

    /** 没有日期的提醒 = “无时间待办”：不排通知、不出现在日历某一天，做完打个勾 */
    fun isTodo(r: Reminder): Boolean = r.date.isBlank()

    const val TODO_DONE_KEY = "todo"

    fun todoDone(r: Reminder): Boolean = r.done[TODO_DONE_KEY] == true

    fun reminderOn(r: Reminder, k: LocalDate): Boolean {
        if (isTodo(r)) return false
        val base = try { LocalDate.parse(r.date) } catch (e: Exception) { return false }
        if (k.isBefore(base)) return false
        val lastDay = k.lengthOfMonth()
        return when (r.repeat) {
            "daily" -> true
            "weekly" -> ChronoUnit.DAYS.between(base, k) % 7 == 0L
            "monthly" -> k.dayOfMonth == minOf(base.dayOfMonth, lastDay)
            "yearly" -> base.monthValue == k.monthValue && k.dayOfMonth == minOf(base.dayOfMonth, lastDay)
            "interval" -> {
                val n = maxOf(1, r.intervalN ?: 1)
                when (r.intervalUnit) {
                    "month" -> {
                        val months = (k.year - base.year) * 12 + (k.monthValue - base.monthValue)
                        months >= 0 && months % n == 0 && k.dayOfMonth == minOf(base.dayOfMonth, lastDay)
                    }
                    "year" -> {
                        val years = k.year - base.year
                        years >= 0 && years % n == 0 && base.monthValue == k.monthValue &&
                            k.dayOfMonth == minOf(base.dayOfMonth, lastDay)
                    }
                    else -> ChronoUnit.DAYS.between(base, k) % n == 0L
                }
            }
            else -> k == base
        }
    }

    /** 下一次还没打勾完成的提醒日期 */
    fun nextReminder(r: Reminder, from: LocalDate, limit: Int = 400): LocalDate? {
        for (i in 0..limit) {
            val k = from.plusDays(i.toLong())
            if (reminderOn(r, k) && r.done[k.toString()] != true) return k
        }
        return null
    }

    fun repeatLabel(r: Reminder): String = when (r.repeat) {
        "none" -> "不重复"
        "daily" -> "每天"
        "weekly" -> "每周"
        "monthly" -> "每月"
        "yearly" -> "每年"
        "interval" -> {
            val unit = when (r.intervalUnit) { "month" -> "个月"; "year" -> "年"; else -> "天" }
            "每 ${maxOf(1, r.intervalN ?: 1)}$unit"
        }
        else -> r.repeat
    }

    val advanceLabels = mapOf(0 to "准时", 10 to "提前10分钟", 60 to "提前1小时", 1440 to "提前1天")
}
