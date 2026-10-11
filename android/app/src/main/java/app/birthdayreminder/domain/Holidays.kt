package app.birthdayreminder.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** 一个法定假期：放假起止 + 需要补班的调休日 */
data class Holiday(val name: String, val start: LocalDate, val end: LocalDate, val workdays: List<LocalDate>)

data class OffInfo(val name: String, val index: Int, val total: Int)

/** holiday-cn 在线数据里的一天：isOffDay=true 放假，false 是调休补班 */
data class HolidayDayEntry(val name: String, val date: LocalDate, val isOffDay: Boolean)

/** 把若干假期展开成“哪天放假 / 哪天补班”的索引，日历角标和工作日闹钟都查它。 */
class HolidayIndex(holidays: Collection<Holiday>) {
    val off: Map<LocalDate, OffInfo>
    val work: Map<LocalDate, String>
    /** 每段假期的第一天 -> 这段假期（“明天起放假”提醒用） */
    val starts: Map<LocalDate, Holiday>

    init {
        val o = HashMap<LocalDate, OffInfo>()
        val w = HashMap<LocalDate, String>()
        val st = HashMap<LocalDate, Holiday>()
        for (h in holidays) {
            st[h.start] = h
            val n = (ChronoUnit.DAYS.between(h.start, h.end) + 1).toInt()
            for (i in 0 until n) o[h.start.plusDays(i.toLong())] = OffInfo(h.name, i + 1, n)
            for (d in h.workdays) w[d] = h.name
        }
        off = o
        work = w
        starts = st
    }

    /** 补班日算工作日；法定假期不算；其余按周一到周五（和旧版规则一致） */
    fun isWorkday(d: LocalDate): Boolean {
        if (work.containsKey(d)) return true
        if (off.containsKey(d)) return false
        return d.dayOfWeek.value in 1..5
    }

    companion object {
        val EMPTY = HolidayIndex(emptyList())
    }
}

object HolidayText {
    private const val WEEK = "日一二三四五六"
    private fun mdw(d: LocalDate) = "${d.monthValue}月${d.dayOfMonth}日（周${WEEK[d.dayOfWeek.value % 7]}）"

    /** 假期日期范围文案，和旧版 holRange 一致：单日只写一天，多日写“起 – 止” */
    fun range(h: Holiday): String = if (h.start == h.end) mdw(h.start) else mdw(h.start) + " – " + mdw(h.end)

    fun days(h: Holiday): Int = (ChronoUnit.DAYS.between(h.start, h.end) + 1).toInt()
}

object Holidays {
    private fun h(n: String, s: String, e: String, vararg w: String) =
        Holiday(n, LocalDate.parse(s), LocalDate.parse(e), w.map { LocalDate.parse(it) })

    /** 内置离线数据（国务院办公厅公告），和旧版 BUILTIN_HOL 一致 */
    val BUILTIN: Map<Int, List<Holiday>> = mapOf(
        2025 to listOf(
            h("元旦", "2025-01-01", "2025-01-01"),
            h("春节", "2025-01-28", "2025-02-04", "2025-01-26", "2025-02-08"),
            h("清明节", "2025-04-04", "2025-04-06"),
            h("劳动节", "2025-05-01", "2025-05-05", "2025-04-27"),
            h("端午节", "2025-05-31", "2025-06-02"),
            h("国庆节、中秋节", "2025-10-01", "2025-10-08", "2025-09-28", "2025-10-11")
        ),
        2026 to listOf(
            h("元旦", "2026-01-01", "2026-01-03", "2026-01-04"),
            h("春节", "2026-02-15", "2026-02-23", "2026-02-14", "2026-02-28"),
            h("清明节", "2026-04-04", "2026-04-06"),
            h("劳动节", "2026-05-01", "2026-05-05", "2026-05-09"),
            h("端午节", "2026-06-19", "2026-06-21"),
            h("中秋节", "2026-09-25", "2026-09-27"),
            h("国庆节", "2026-10-01", "2026-10-07", "2026-09-20", "2026-10-10")
        )
    )

    /** 联网数据（cached，按年份）覆盖同年的内置数据 */
    fun merge(cached: Map<Int, List<Holiday>>): HolidayIndex {
        val all = HashMap<Int, List<Holiday>>(BUILTIN)
        for ((y, list) in cached) all[y] = list
        return HolidayIndex(all.values.flatten())
    }

    /**
     * 把 holiday-cn 的逐日数据合并成“一段假期”，逻辑和旧版 holidayGroups 一致：
     * 连续的放假日合成一段；补班日优先按名字归到对应假期，
     * 一个都对不上时，把开始日前后 10 天内的补班日都算给它。
     */
    fun groupDays(days: List<HolidayDayEntry>): List<Holiday> {
        val off = days.filter { it.isOffDay }.sortedBy { it.date }
        class Run(val start: LocalDate, var end: LocalDate, val names: MutableList<String>)
        val runs = mutableListOf<Run>()
        var cur: Run? = null
        for (d in off) {
            val c = cur
            if (c != null && ChronoUnit.DAYS.between(c.end, d.date) == 1L) {
                c.end = d.date
                if (d.name !in c.names) c.names.add(d.name)
            } else {
                val r = Run(d.date, d.date, mutableListOf(d.name))
                runs.add(r)
                cur = r
            }
        }
        val work = days.filter { !it.isOffDay }
        return runs.map { r ->
            var w = work.filter { it.name in r.names }.map { it.date }
            if (w.isEmpty()) {
                w = work.filter { Math.abs(ChronoUnit.DAYS.between(it.date, r.start)) <= 10 }.map { it.date }
            }
            Holiday(r.names.joinToString("、"), r.start, r.end, w)
        }
    }
}
