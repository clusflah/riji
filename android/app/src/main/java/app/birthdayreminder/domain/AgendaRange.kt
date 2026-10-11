package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 日程视图的数据（可以往前看过去、往后看将来）：日期范围 [from, to] 里每一次发生的事。
 * 和“未来 30 天”不同，这里重复的提醒每一次都列出来，生日每年都列，假期每一天都列。
 * 过去的日子还会列出日记和就医记录（记下来的事）。纯逻辑，可测试。
 */
object AgendaRange {
    data class Note(val date: LocalDate, val title: String, val id: String)      // 日记、就医记录

    fun build(
        birthdays: List<Birthday>, reminders: List<Reminder>, holidays: HolidayIndex,
        from: LocalDate, to: LocalDate,
        followUps: List<Pair<LocalDate, String>> = emptyList(),
        diaries: List<Note> = emptyList(),
        visits: List<Note> = emptyList()
    ): List<UpcomingItem> {
        if (to.isBefore(from)) return emptyList()
        val out = ArrayList<UpcomingItem>()
        fun inRange(d: LocalDate) = !d.isBefore(from) && !d.isAfter(to)
        for (b in birthdays) {
            if (b.isPrivate) continue
            var cur = from; var n = 0
            while (n++ < 30) {
                val (d, _) = Occurrence.nextBirthday(b, cur) ?: break
                if (d.isAfter(to)) break
                val age = Occurrence.ageAt(b, d)
                out += UpcomingItem(d, "birthday", "🎂 ${b.name}" + (if (age != null) " ${age}岁生日" else "的生日"), b.id)
                cur = d.plusDays(1)
            }
        }
        for (r in reminders) {
            if (r.isPrivate || Occurrence.isTodo(r)) continue
            var cur = from; var n = 0
            while (n++ < 800) {
                val left = ChronoUnit.DAYS.between(cur, to).toInt() + 1
                if (left <= 0) break
                val d = Occurrence.nextReminder(r, cur, left) ?: break
                if (d.isAfter(to)) break
                out += UpcomingItem(d, "reminder", "🔔 ${r.title}" + (if (r.time.isNotBlank()) " ${r.time}" else ""), r.id)
                cur = d.plusDays(1)
            }
        }
        // 假期每一天：第一天写共几天，之后写第几天
        for ((d, info) in holidays.off) {
            if (!inRange(d)) continue
            out += UpcomingItem(d, "holiday", if (info.index == 1) "🏖️ ${info.name}放假 · 共${info.total}天" else "🏖️ ${info.name}放假 · 第${info.index}天，共${info.total}天")
        }
        for ((d, name) in holidays.work) if (inRange(d)) out += UpcomingItem(d, "work", "💼 调休上班（$name）")
        for ((d, text) in followUps) if (inRange(d)) out += UpcomingItem(d, "followup", "🏥 $text")
        for (v in visits) if (inRange(v.date)) out += UpcomingItem(v.date, "visit", "🏥 就医：${v.title}", v.id)
        for (n in diaries) if (inRange(n.date)) out += UpcomingItem(n.date, "diary", "📝 ${n.title}", n.id)
        val order = mapOf("holiday" to 0, "work" to 1, "followup" to 2, "visit" to 3, "birthday" to 4, "reminder" to 5, "diary" to 6)
        return out.sortedWith(compareBy({ it.date }, { order[it.kind] ?: 9 }, { it.title }))
    }
}
