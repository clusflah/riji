package app.birthdayreminder.domain

import app.birthdayreminder.data.Alarm
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 算出“接下来哪些时刻要响铃”，规则逐条对应旧版 alarmRingsOn / alarmTriggers / buildAlarmPayload：
 * 往后看 60 天，每个闹钟最多 40 次，所有闹钟合起来只保留最近的 200 次。
 * 纯逻辑，不碰 Android 类。
 */
object AlarmTriggers {
    const val HORIZON_DAYS = 60
    const val PER_ALARM = 40
    const val MAX_TOTAL = 200

    data class Trigger(
        val aid: String,
        val at: Long,
        val label: String,
        val sound: String,
        val snooze: Int,
        val vibrate: Boolean
    )

    val DOW_NAMES = listOf("日", "一", "二", "三", "四", "五", "六")

    fun rings(a: Alarm, d: LocalDate, isWorkday: (LocalDate) -> Boolean): Boolean = when (a.repeat) {
        "daily" -> true
        "workday" -> isWorkday(d)
        "restday" -> !isWorkday(d)
        "custom" -> (d.dayOfWeek.value % 7) in a.days   // 周日 value=7 -> 0
        else -> false
    }

    private fun parseTime(hm: String): LocalTime = try { LocalTime.parse(hm) } catch (e: Exception) { LocalTime.of(7, 0) }

    fun atTime(d: LocalDate, hm: String, zone: ZoneId): Long =
        d.atTime(parseTime(hm)).atZone(zone).toInstant().toEpochMilli()

    /** 单次闹钟：今天这个时间还没到就今天，否则明天 */
    fun nextOnceAt(hm: String, now: Long, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): Long {
        val t = atTime(today, hm, zone)
        return if (t > now + 1000) t else atTime(today.plusDays(1), hm, zone)
    }

    fun triggersFor(
        a: Alarm, now: Long, zone: ZoneId, today: LocalDate, isWorkday: (LocalDate) -> Boolean
    ): List<Long> {
        if (a.repeat == "once") {
            val t = a.onceAt
            return if (t != null && t > now) listOf(t) else emptyList()
        }
        val out = mutableListOf<Long>()
        var i = 0
        while (i <= HORIZON_DAYS && out.size < PER_ALARM) {
            val k = today.plusDays(i.toLong())
            i++
            if (!rings(a, k, isWorkday)) continue
            val t = atTime(k, a.time, zone)
            if (t > now) out.add(t)
        }
        return out
    }

    fun build(
        alarms: List<Alarm>, now: Long, zone: ZoneId, today: LocalDate, isWorkday: (LocalDate) -> Boolean
    ): List<Trigger> {
        val out = mutableListOf<Trigger>()
        for (a in alarms) {
            if (!a.enabled) continue
            for (t in triggersFor(a, now, zone, today, isWorkday)) {
                out += Trigger(a.id, t, a.label, a.soundPath ?: "", a.snooze, a.vibrate)
            }
        }
        out.sortBy { it.at }
        return out.take(MAX_TOTAL)
    }

    /** 已经过期的“仅一次”闹钟要自动关掉（响过 1 分钟以上，或者根本没有下次时间） */
    fun expiredOnceIds(alarms: List<Alarm>, now: Long): List<String> =
        alarms.filter { it.repeat == "once" && it.enabled && (it.onceAt == null || it.onceAt < now - 60_000) }
            .map { it.id }

    /** 最近一个要响的闹钟，用于列表显示“下一次 …” */
    fun next(
        alarms: List<Alarm>, now: Long, zone: ZoneId, today: LocalDate, isWorkday: (LocalDate) -> Boolean
    ): Pair<Alarm, Long>? {
        var best: Pair<Alarm, Long>? = null
        for (a in alarms) {
            if (!a.enabled) continue
            val t = triggersFor(a, now, zone, today, isWorkday).firstOrNull() ?: continue
            if (best == null || t < best.second) best = a to t
        }
        return best
    }

    fun repeatText(a: Alarm): String = when (a.repeat) {
        "custom" -> {
            val s = listOf(1, 2, 3, 4, 5, 6, 0).filter { it in a.days }.joinToString(" ") { DOW_NAMES[it] }
            if (s.isEmpty()) "未选择日期" else "每周 $s"
        }
        "workday" -> "工作日 · 跳过法定节假日"
        "restday" -> "休息日 · 周末与法定节假日"
        "daily" -> "每天"
        "once" -> "仅一次"
        else -> ""
    }

    fun whenText(ms: Long, zone: ZoneId, today: LocalDate = LocalDate.now(zone)): String {
        val dt = java.time.Instant.ofEpochMilli(ms).atZone(zone)
        val n = java.time.temporal.ChronoUnit.DAYS.between(today, dt.toLocalDate())
        val day = when (n) {
            0L -> "今天"
            1L -> "明天"
            2L -> "后天"
            else -> "${dt.monthValue}月${dt.dayOfMonth}日 周${DOW_NAMES[dt.dayOfWeek.value % 7]}"
        }
        return "%s %02d:%02d".format(day, dt.hour, dt.minute)
    }
}
