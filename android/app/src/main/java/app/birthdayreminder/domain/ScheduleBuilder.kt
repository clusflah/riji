package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Reminder
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 计算“接下来要弹哪些通知、什么时候弹”，和 JS 版 buildSchedule 同一套规则：
 * 往后看 180 天；生日每人最多排 3 次、提醒每条最多排 8 次；
 * 全部按时间排序，只保留最近的 60 条。
 * 这里不碰任何 Android 类，方便单独测试。
 */
object ScheduleBuilder {
    const val MAX_NOTIF = 60
    const val SCHED_DAYS = 180

    /**
     * 一条要定时弹出的通知。
     * reminderId / occurrence：只有提醒事项才有（哪条提醒、哪一天那次），通知上的“完成”“稍后提醒”按钮靠它们知道操作的是谁。
     * source：来自哪条数据（生日 / 提醒 / 节假日），和 at 一起组成 key，用来固定通知编号、记“答应过要弹”的名单。
     */
    data class Item(
        val at: Long, val title: String, val body: String,
        val reminderId: String? = null, val occurrence: String? = null,
        val source: String = ""
    ) {
        val key: String get() = "$source|${occurrence ?: ""}|$at"
    }

    fun build(
        birthdays: List<Birthday>,
        reminders: List<Reminder>,
        dailyTime: String,
        now: Long = System.currentTimeMillis(),
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
        holidays: HolidayIndex = HolidayIndex.EMPTY,
        holidayAlert: Boolean = true
    ): List<Item> {
        val defaultTime = try { LocalTime.parse(dailyTime) } catch (e: Exception) { LocalTime.of(9, 0) }

        fun atTime(d: LocalDate, hhmm: String?): Long {
            val t = if (hhmm.isNullOrBlank()) defaultTime else try { LocalTime.parse(hhmm) } catch (e: Exception) { defaultTime }
            return d.atTime(t).atZone(zone).toInstant().toEpochMilli()
        }

        val items = mutableListOf<Item>()

        for (b in birthdays) {
            var count = 0
            var i = 0
            while (i <= SCHED_DAYS && count < 3) {
                val k = today.plusDays(i.toLong())
                i++
                if (!Occurrence.birthdayOn(b, k)) continue
                count++
                val age = Occurrence.ageAt(b, k)
                for (a in b.advance) {
                    val at = atTime(k.minusDays(a.toLong()), null)
                    if (at <= now) continue
                    val whenText = when (a) {
                        0 -> "今天"
                        1 -> "明天"
                        else -> "${a}天后（${k.monthValue}月${k.dayOfMonth}日）"
                    }
                    items += if (b.isPrivate) Item(at, "🎂 生日提醒", whenText, source = "b:${b.id}")      // 不写名字和年龄，也不出现“私密”字样
                    else Item(at, "🎂 ${b.name}的生日", whenText + (if (age != null) " · ${age}岁生日" else ""), source = "b:${b.id}")
                }
            }
        }

        for (r in reminders) {
            var count = 0
            var i = 0
            while (i <= SCHED_DAYS && count < 8) {
                val k = today.plusDays(i.toLong())
                i++
                if (!Occurrence.reminderOn(r, k) || r.done[k.toString()] == true) continue
                count++
                val adv = r.advance
                val at = atTime(k, r.time) - adv * 60_000L
                if (at <= now) continue
                val prefix = if (adv > 0) (Occurrence.advanceLabels[adv] ?: "提前${adv}分钟") + "：" else ""
                items += Item(at, if (r.isPrivate) "🔔 提醒" else "🔔 ${r.title}", "$prefix${k.monthValue}月${k.dayOfMonth}日 ${r.time.ifBlank { "全天" }}",
                    reminderId = r.id, occurrence = k.toString(), source = "r:${r.id}")
            }
        }

        // 节假日 / 调休补班：前一天在每日提醒时间提醒一次（规则和文案与旧版一致）
        if (holidayAlert) {
            val week = "日一二三四五六"
            for (i in 0..SCHED_DAYS) {
                val k = today.plusDays(i.toLong())
                val hat = atTime(k.minusDays(1), null)
                if (hat <= now) continue
                holidays.work[k]?.let { name ->
                    items += Item(hat, "💼 明天周${week[k.dayOfWeek.value % 7]}要上班", name + "调休补班", source = "hw:$k")
                }
                holidays.starts[k]?.let { h ->
                    items += Item(hat, "🏖️ 明天起放假：${h.name}", HolidayText.range(h) + " · 共${HolidayText.days(h)}天", source = "hs:$k")
                }
            }
        }

        items.sortBy { it.at }
        return items.take(MAX_NOTIF)
    }
}
