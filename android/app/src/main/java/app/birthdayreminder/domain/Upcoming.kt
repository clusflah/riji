package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 主界面“未来 30 天”里的一条 */
data class UpcomingItem(
    val date: LocalDate,
    val kind: String,          // birthday / reminder / holiday / work
    val title: String,
    val refId: String? = null  // 生日 / 提醒的 id（点了进编辑）；假期没有
)

/**
 * 主界面下方的“未来 30 天”摘要：生日、有日期的提醒、法定假期开始、调休补班，按日期排。
 * - 每条提醒只取下一次（每天重复的提醒不会刷满整个列表）；已经打勾的那次会自动跳到下一次；
 * - 私密内容一律不出现；
 * - 今天也算在内（今天 + 之后 30 天）。
 * 纯逻辑，可测试。
 */
object Upcoming {
    const val DAYS = 30
    private const val WEEK = "日一二三四五六"

    fun build(
        birthdays: List<Birthday>, reminders: List<Reminder>, holidays: HolidayIndex,
        today: LocalDate, days: Int = DAYS,
        followUps: List<Pair<LocalDate, String>> = emptyList()      // 复诊：日期 + 一句话
    ): List<UpcomingItem> {
        val end = today.plusDays(days.toLong())
        val out = ArrayList<UpcomingItem>()
        for (b in birthdays) {
            if (b.isPrivate) continue
            val (d, _) = Occurrence.nextBirthday(b, today, if (b.leap) 4000 else 400) ?: continue
            if (d.isAfter(end)) continue
            val age = Occurrence.ageAt(b, d)
            out += UpcomingItem(d, "birthday", "🎂 ${b.name}" + (if (age != null) " ${age}岁生日" else "的生日"), b.id)
        }
        for (r in reminders) {
            if (r.isPrivate || Occurrence.isTodo(r)) continue
            val d = Occurrence.nextReminder(r, today, days + 1) ?: continue
            if (d.isAfter(end)) continue
            out += UpcomingItem(d, "reminder", "🔔 ${r.title}" + (if (r.time.isNotBlank()) " ${r.time}" else ""), r.id)
        }
        for ((d, h) in holidays.starts) {
            if (d.isBefore(today) || d.isAfter(end)) continue
            out += UpcomingItem(d, "holiday", "🏖️ ${h.name}放假 · 共${HolidayText.days(h)}天")
        }
        // 今天正在放假（假期开始日在今天之前）：也提一句，免得列表里看不出来
        holidays.off[today]?.let { info ->
            if (info.index > 1) out += UpcomingItem(today, "holiday", "🏖️ ${info.name}放假中 · 第${info.index}天，共${info.total}天")
        }
        for ((d, name) in holidays.work) {
            if (d.isBefore(today) || d.isAfter(end)) continue
            out += UpcomingItem(d, "work", "💼 调休上班（${name}）")
        }
        for ((d, text) in followUps) {
            if (d.isBefore(today) || d.isAfter(end)) continue
            out += UpcomingItem(d, "followup", "🏥 $text")
        }
        // 同一天里：假期/补班在前，然后复诊、生日，最后提醒（提醒再按时间）
        val order = mapOf("holiday" to 0, "work" to 1, "followup" to 2, "birthday" to 3, "reminder" to 4)
        return out.sortedWith(compareBy({ it.date }, { order[it.kind] ?: 9 }, { it.title }))
    }

    /** 日期标题：今天 / 明天 / 后天 / 10月5日 周一 · 还有4天 */
    fun dateLabel(d: LocalDate, today: LocalDate): String {
        val n = ChronoUnit.DAYS.between(today, d)
        return when (n) {
            0L -> "今天"
            1L -> "明天"
            2L -> "后天"
            else -> "${d.monthValue}月${d.dayOfMonth}日 周${WEEK[d.dayOfWeek.value % 7]} · 还有${n}天"
        }
    }
}
