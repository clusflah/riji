package app.birthdayreminder.domain

import app.birthdayreminder.data.CycleDay
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 生理期的规则（纯逻辑）：找出每次经期、算平均周期、预测下次经期 / 排卵日 / 易孕期 / 经前期、
 * 日历上每天的标记、提醒时间、趋势和导出。功能设计参考 Mooneva Cycle。
 * 这些都是按平均值估算，只供参考，不能用来避孕或诊断。
 */
object CycleLogic {
    val FLOWS = listOf("无", "点滴", "少", "中", "多")
    const val PERIOD_FLOW = 2          // 少量及以上算经期（点滴不算）
    /** 只标了“是经期”、没选流量（存在 flow 里，比“多”大，所以也算经期） */
    const val PERIOD_ONLY = 5

    fun isPeriod(flow: Int) = flow >= PERIOD_FLOW
    /** 流量的文字：只标了经期的显示“经期” */
    fun flowLabel(flow: Int) = if (flow == PERIOD_ONLY) "经期" else FLOWS[flow.coerceIn(0, 4)]

    /**
     * 把 start 标成经期时，后面要自动标成经期的日子：只在这是新一次经期的第一天（前两天都不是经期）时才补，
     * 补到一共 periodLength 天（含 start）。已经记过的日子由调用方决定怎么处理。
     */
    fun autoFillDates(days: List<CycleDay>, start: LocalDate, periodLength: Int): List<LocalDate> {
        val period = days.filter { isPeriod(it.flow) }.map { it.date }.toSet()
        if (start.minusDays(1).toString() in period || start.minusDays(2).toString() in period) return emptyList()
        return (1 until periodLength.coerceIn(1, 12)).map { start.plusDays(it.toLong()) }
    }

    /** “经期到这天结束”：这天之后紧接着的经期日子（连续的），要取消掉 */
    fun datesAfterEnd(days: List<CycleDay>, end: LocalDate): List<LocalDate> {
        val period = days.filter { isPeriod(it.flow) }.map { it.date }.toSet()
        val out = ArrayList<LocalDate>()
        var x = end.plusDays(1)
        while (x.toString() in period && out.size < 30) { out += x; x = x.plusDays(1) }
        return out
    }
    val SYMPTOMS = listOf(
        "cramps" to "痛经", "headache" to "头痛", "backache" to "腰酸", "breast" to "胸胀", "bloating" to "腹胀",
        "acne" to "长痘", "fatigue" to "疲劳", "insomnia" to "失眠", "nausea" to "恶心", "moody" to "情绪波动", "appetite" to "食欲变化"
    )
    val MOODS = listOf("happy" to "开心", "calm" to "平静", "irritable" to "烦躁", "low" to "低落", "anxious" to "焦虑", "sensitive" to "敏感")
    val DISCHARGES = listOf("none" to "无", "little" to "少量", "sticky" to "黏稠", "eggwhite" to "蛋清状", "watery" to "水样")
    fun label(list: List<Pair<String, String>>, key: String) = list.firstOrNull { it.first == key }?.second ?: ""

    const val DEFAULT_CYCLE = 28
    const val DEFAULT_PERIOD = 5

    private fun d(s: String) = try { LocalDate.parse(s) } catch (e: Exception) { null }

    // ---------------- 每次经期 ----------------
    data class Period(val start: LocalDate, val end: LocalDate) {
        val length: Int get() = ChronoUnit.DAYS.between(start, end).toInt() + 1
    }

    /** 流量“少”及以上的连续日子算一次经期；中间漏记一天也算连着 */
    fun periods(days: List<CycleDay>): List<Period> {
        val dates = days.filter { isPeriod(it.flow) }.mapNotNull { d(it.date) }.sorted()
        val out = ArrayList<Period>()
        for (x in dates) {
            val last = out.lastOrNull()
            if (last != null && ChronoUnit.DAYS.between(last.end, x) <= 2) out[out.size - 1] = last.copy(end = x)
            else out.add(Period(x, x))
        }
        return out
    }

    data class Stats(val cycleLength: Int, val periodLength: Int, val cycles: Int, val learned: Boolean)

    /** 最近 6 个周期的平均周期长度、平均经期天数；记录不够时用默认值 */
    fun stats(periods: List<Period>, defCycle: Int = DEFAULT_CYCLE, defPeriod: Int = DEFAULT_PERIOD): Stats {
        val gaps = periods.zipWithNext { a, b -> ChronoUnit.DAYS.between(a.start, b.start).toInt() }.filter { it in 15..60 }.takeLast(6)
        val lens = periods.map { it.length }.filter { it in 1..12 }.takeLast(6)
        return Stats(
            if (gaps.isEmpty()) defCycle else Math.round(gaps.average()).toInt(),
            if (lens.isEmpty()) defPeriod else Math.round(lens.average()).toInt(),
            gaps.size, gaps.isNotEmpty()
        )
    }

    // ---------------- 预测 ----------------
    data class Prediction(
        val lastStart: LocalDate,
        val nextStart: LocalDate,
        val lateDays: Int,                 // 已经过了预计日期还没来：推迟几天
        val periodDay: Int?,               // 今天是经期第几天（不在经期为 null）
        val ovulation: LocalDate,
        val fertileStart: LocalDate,
        val fertileEnd: LocalDate,
        val pmsStart: LocalDate
    )

    fun predict(periods: List<Period>, stats: Stats, today: LocalDate): Prediction? {
        val last = periods.lastOrNull() ?: return null
        val inPeriod = !today.isBefore(last.start) && (!today.isAfter(last.end) ||
            (ChronoUnit.DAYS.between(last.end, today) <= 1 && ChronoUnit.DAYS.between(last.start, today) < stats.periodLength))
        val next = last.start.plusDays(stats.cycleLength.toLong())
        val late = if (today.isAfter(next)) ChronoUnit.DAYS.between(next, today).toInt() else 0
        val ov = next.minusDays(14)
        return Prediction(last.start, next, late, if (inPeriod) ChronoUnit.DAYS.between(last.start, today).toInt() + 1 else null,
            ov, ov.minusDays(5), ov.plusDays(1), next.minusDays(5))
    }

    // ---------------- 日历标记 ----------------
    enum class Mark { PERIOD, PREDICTED, OVULATION, FERTILE, NONE }

    /**
     * 某段日期里每天的标记：记录的经期 > 预测经期 > 排卵日 > 易孕期。
     * 预测往后算 3 个周期；避孕模式不显示排卵日、易孕期。
     */
    fun marks(days: List<CycleDay>, from: LocalDate, to: LocalDate, today: LocalDate, birthControl: Boolean,
              defCycle: Int = DEFAULT_CYCLE, defPeriod: Int = DEFAULT_PERIOD): Map<LocalDate, Mark> {
        val ps = periods(days)
        val st = stats(ps, defCycle, defPeriod)
        val out = HashMap<LocalDate, Mark>()
        fun put(x: LocalDate, m: Mark) {
            if (x.isBefore(from) || x.isAfter(to)) return
            val old = out[x]
            if (old == null || m.ordinal < old.ordinal) out[x] = m
        }
        // 只标真正记成经期的日子。periods() 会把中间漏记的一天连起来（算周期用），但日历上不能跟着标：
        // 不然没记的那天是不是“经期”取决于后一天，改后一天，前一天也跟着变
        for (x in days) if (isPeriod(x.flow)) d(x.date)?.let { put(it, Mark.PERIOD) }
        val pr = predict(ps, st, today) ?: return out
        // 预测的起点：没推迟就是预计日期；推迟了就从今天起往后排
        var start = if (pr.lateDays > 0) today else pr.nextStart
        for (k in 0 until 3) {
            for (i in 0 until st.periodLength) {
                val x = start.plusDays(i.toLong())
                if (x.isAfter(today) || (x == today && pr.periodDay == null)) put(x, Mark.PREDICTED)
            }
            if (!birthControl) {
                val ov = start.minusDays(14)
                var f = ov.minusDays(5)
                while (!f.isAfter(ov.plusDays(1))) { if (f.isAfter(pr.lastStart.plusDays(st.periodLength.toLong() - 1))) put(f, if (f == ov) Mark.OVULATION else Mark.FERTILE); f = f.plusDays(1) }
            }
            start = start.plusDays(st.cycleLength.toLong())
        }
        return out
    }

    /** 今天的一句话状态 */
    fun todayText(pr: Prediction?, today: LocalDate, birthControl: Boolean): String {
        if (pr == null) return "还没有经期记录。来的那天点“今天来了”，记两三次之后就能预测"
        pr.periodDay?.let { return "经期第 $it 天" }
        if (pr.lateDays > 0) return "比预计晚了 ${pr.lateDays} 天（预计 ${pr.nextStart.monthValue}月${pr.nextStart.dayOfMonth}日）"
        val left = ChronoUnit.DAYS.between(today, pr.nextStart).toInt()
        val base = if (left == 0) "预计今天来" else "预计 ${pr.nextStart.monthValue}月${pr.nextStart.dayOfMonth}日来，还有 $left 天"
        val extra = when {
            !birthControl && today == pr.ovulation -> " · 今天可能是排卵日"
            !birthControl && !today.isBefore(pr.fertileStart) && !today.isAfter(pr.fertileEnd) -> " · 易孕期"
            !today.isBefore(pr.pmsStart) -> " · 经前期"
            else -> ""
        }
        return base + extra
    }

    // ---------------- 提醒 ----------------
    data class Alert(val date: LocalDate, val title: String, val body: String)

    /** 接下来要提醒的：经期前 N 天、经前期开始、排卵日（避孕模式不提醒排卵）；只要今天及以后的 */
    fun alerts(pr: Prediction?, today: LocalDate, daysBefore: Int, pms: Boolean, ovulation: Boolean, birthControl: Boolean): List<Alert> {
        if (pr == null || pr.lateDays > 0) return emptyList()
        val out = ArrayList<Alert>()
        val md = "${pr.nextStart.monthValue}月${pr.nextStart.dayOfMonth}日"
        if (daysBefore > 0) out += Alert(pr.nextStart.minusDays(daysBefore.toLong()), "🩸 经期预计 $daysBefore 天后来", "预计 $md，记得准备")
        out += Alert(pr.nextStart, "🩸 经期预计今天来", "来了的话记得在日迹里记一下")
        if (pms) out += Alert(pr.pmsStart, "🌙 经前期开始了", "接下来几天可能会情绪波动、胸胀，注意休息")
        if (ovulation && !birthControl) out += Alert(pr.ovulation, "🌱 今天可能是排卵日", "按平均周期估算，仅供参考")
        return out.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    }

    // ---------------- 趋势 ----------------
    data class HistoryRow(val start: LocalDate, val periodLength: Int, val cycleLength: Int?)

    fun history(periods: List<Period>): List<HistoryRow> =
        periods.mapIndexed { i, p -> HistoryRow(p.start, p.length, periods.getOrNull(i + 1)?.let { ChronoUnit.DAYS.between(p.start, it.start).toInt() }) }.reversed()

    /** 某段时间里各症状出现的天数（从多到少） */
    fun symptomCounts(days: List<CycleDay>, from: LocalDate, to: LocalDate): List<Pair<String, Int>> {
        val f = from.toString(); val t = to.toString()
        val counts = HashMap<String, Int>()
        for (x in days) if (x.date in f..t) for (k in MedicalLogic.listFromJson(x.symptoms)) counts[k] = (counts[k] ?: 0) + 1
        return SYMPTOMS.mapNotNull { (k, l) -> counts[k]?.let { l to it } }.sortedByDescending { it.second }
    }

    /** 一天记录的摘要：流量 中 · 痛经、腰酸 · 心情 烦躁 */
    fun daySummary(x: CycleDay): String = listOfNotNull(
        if (x.flow == PERIOD_ONLY) "经期" else if (x.flow > 0) "流量 ${flowLabel(x.flow)}" else null,
        MedicalLogic.listFromJson(x.symptoms).mapNotNull { k -> label(SYMPTOMS, k).ifEmpty { null } }.joinToString("、").ifEmpty { null },
        label(MOODS, x.mood).ifEmpty { null }?.let { "心情 $it" },
        label(DISCHARGES, x.discharge).takeIf { it.isNotEmpty() && x.discharge != "none" }?.let { "分泌物 $it" },
        x.note.ifBlank { null }
    ).joinToString(" · ")

    fun csv(days: List<CycleDay>, periods: List<Period>): String {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder("\uFEFF经期开始,经期天数,周期长度\n")
        for (h in history(periods)) sb.append("${h.start},${h.periodLength},${h.cycleLength ?: ""}\n")
        sb.append("\n日期,流量,症状,心情,分泌物,备注\n")
        for (x in days.sortedByDescending { it.date }) {
            sb.append(listOf(x.date, flowLabel(x.flow),
                q(MedicalLogic.listFromJson(x.symptoms).map { label(SYMPTOMS, it) }.joinToString("、")),
                label(MOODS, x.mood), label(DISCHARGES, x.discharge), q(x.note)).joinToString(",")).append('\n')
        }
        return sb.toString()
    }
}

/**
 * 避孕药（参照在 Mooneva 上做过的改进）：药板进度、某天的服药状态、连续天数、最近 7 天、月历。
 * 用“用药就医”里的药品和服药记录，不另外存数据。
 */
object PillLogic {
    enum class DayStatus { TAKEN, MISSED, SKIPPED, BREAK, DUE, UPCOMING, NONE }

    /** 药板进度：第 5/21 粒，或停药期 3/7；不是“吃几天停几天”的计划返回 null */
    fun packText(plan: MedLogic.MedPlan, date: LocalDate): String? {
        if (plan.mode != "cycle") return null
        val start = try { LocalDate.parse(plan.start) } catch (e: Exception) { return null }
        if (date.isBefore(start)) return null
        val k = (ChronoUnit.DAYS.between(start, date) % (plan.on + plan.off)).toInt()
        return if (k < plan.on) "第 ${k + 1}/${plan.on} 粒" else "停药期 ${k - plan.on + 1}/${plan.off}"
    }

    /** 某一天：停药日 / 已服 / 跳过（标记为没吃） / 漏服 / 待服 / 未到 */
    fun dayStatus(med: app.birthdayreminder.data.Medication, logs: List<app.birthdayreminder.data.MedLog>, date: LocalDate, now: java.time.LocalDateTime): DayStatus {
        val plan = MedLogic.planFromJson(med.plan)
        if (!MedLogic.scheduledOn(plan, date)) return if (plan.mode == "cycle") DayStatus.BREAK else DayStatus.NONE
        val states = MedLogic.day(listOf(med), logs, date, now).map { it.status }
        return when {
            states.isEmpty() -> DayStatus.NONE
            states.all { it == MedLogic.Status.TAKEN } -> DayStatus.TAKEN
            states.any { it == MedLogic.Status.SKIPPED } -> DayStatus.SKIPPED
            states.any { it == MedLogic.Status.MISSED } -> DayStatus.MISSED
            states.any { it == MedLogic.Status.DUE } -> DayStatus.DUE
            else -> DayStatus.UPCOMING
        }
    }

    /** 连续服药天数：停药日跳过不算也不打断；今天还没到时间或还在待服，就从昨天往回数 */
    fun streak(med: app.birthdayreminder.data.Medication, logs: List<app.birthdayreminder.data.MedLog>, today: LocalDate, now: java.time.LocalDateTime): Int {
        var n = 0
        var x = today
        for (i in 0 until 400) {
            when (dayStatus(med, logs, x, now)) {
                DayStatus.TAKEN -> n++
                DayStatus.BREAK, DayStatus.NONE -> {}
                DayStatus.DUE, DayStatus.UPCOMING -> if (x != today) return n
                else -> return n
            }
            x = x.minusDays(1)
        }
        return n
    }

    /** 最近 7 天（从 6 天前到今天） */
    fun last7(med: app.birthdayreminder.data.Medication, logs: List<app.birthdayreminder.data.MedLog>, today: LocalDate, now: java.time.LocalDateTime): List<Pair<LocalDate, DayStatus>> =
        (6 downTo 0).map { today.minusDays(it.toLong()) }.map { it to dayStatus(med, logs, it, now) }

    /** 昨天漏服了没有（需要按说明书处理） */
    fun missedYesterday(med: app.birthdayreminder.data.Medication, logs: List<app.birthdayreminder.data.MedLog>, today: LocalDate, now: java.time.LocalDateTime): Boolean =
        dayStatus(med, logs, today.minusDays(1), now).let { it == DayStatus.MISSED || it == DayStatus.SKIPPED }
}
