package app.birthdayreminder.domain

import app.birthdayreminder.data.MoodLog
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 快捷心情（纯逻辑）：五档心情，以及统计页用到的三组数据——
 * 起伏（按天 / 周 / 月平均）、概览（每档几次）、每日分布（周一到周日的平均心情）。
 */
object MoodLogic {
    data class Level(val value: Int, val emoji: String, val label: String)

    /** 从好到差（统计页的纵轴、概览从上往下都是这个顺序） */
    val LEVELS = listOf(
        Level(5, "😊", "很好"), Level(4, "🙂", "不错"), Level(3, "😐", "一般"), Level(2, "😕", "不好"), Level(1, "😔", "很差")
    )
    fun level(v: Int): Level = LEVELS.firstOrNull { it.value == v } ?: LEVELS[2]

    /** 时间范围：最近 1 个月 / 6 个月 / 1 年 / 全部 */
    enum class Range(val label: String, val months: Long?) { M1("1 个月", 1), M6("6 个月", 6), Y1("1 年", 12), ALL("全部", null) }

    fun from(range: Range, today: LocalDate, logs: List<MoodLog>): LocalDate =
        range.months?.let { today.minusMonths(it).plusDays(1) }
            ?: logs.minOfOrNull { it.date }?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: today

    /** 起伏图的一个点：这一段的起始日、平均心情、记了几次 */
    data class Point(val start: LocalDate, val avg: Double, val count: Int)

    /**
     * 起伏：时间短按天平均，长一点按周（周一开头），再长按月——点数保持在一两百以内，曲线才看得清。
     * 没记录的段不出点（曲线直接连过去）。
     */
    fun series(logs: List<MoodLog>, from: LocalDate, to: LocalDate): List<Point> {
        val span = ChronoUnit.DAYS.between(from, to)
        val bucket: (LocalDate) -> LocalDate = when {
            span <= 92 -> { d -> d }
            span <= 400 -> { d -> d.minusDays((d.dayOfWeek.value - 1).toLong()) }
            else -> { d -> d.withDayOfMonth(1) }
        }
        return inRange(logs, from, to).groupBy { bucket(it.first) }.toSortedMap()
            .map { (k, v) -> Point(k, v.map { it.second.level }.average(), v.size) }
    }

    /** 概览：每档几次（从好到差） */
    fun counts(logs: List<MoodLog>, from: LocalDate, to: LocalDate): List<Pair<Level, Int>> {
        val c = inRange(logs, from, to).groupingBy { it.second.level }.eachCount()
        return LEVELS.map { it to (c[it.value] ?: 0) }
    }

    /** 每日分布：周一到周日各自的平均心情（没记录的那天是 null） */
    fun weekdayAvg(logs: List<MoodLog>, from: LocalDate, to: LocalDate): List<Double?> {
        val g = inRange(logs, from, to).groupBy { it.first.dayOfWeek.value }
        return (1..7).map { w -> g[w]?.map { it.second.level }?.average() }
    }

    private fun inRange(logs: List<MoodLog>, from: LocalDate, to: LocalDate): List<Pair<LocalDate, MoodLog>> =
        logs.mapNotNull { m -> runCatching { LocalDate.parse(m.date) }.getOrNull()?.let { it to m } }
            .filter { !it.first.isBefore(from) && !it.first.isAfter(to) }
}
