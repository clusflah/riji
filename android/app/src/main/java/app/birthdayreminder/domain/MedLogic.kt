package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.data.MedLog
import app.birthdayreminder.data.Medication
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * 药物追踪与提醒的规则（纯逻辑，可测试）。功能设计参考 MedTimer（MIT）。
 */
object MedLogic {
    /** 一天里的一个服药时间和这次的用量 */
    data class MedTime(val time: String, val amount: Double = 1.0)

    /**
     * 服药计划。mode：
     *  daily 每天 / weekly 每周几（weekdays：1=周一 … 7=周日）/ every 每隔 every 天 / cycle 吃 on 天停 off 天
     */
    data class MedPlan(
        val mode: String = "daily",
        val times: List<MedTime> = listOf(MedTime("08:00")),
        val weekdays: Set<Int> = emptySet(),
        val every: Int = 2,
        val on: Int = 21,
        val off: Int = 7,
        val start: String = "",
        val end: String? = null
    )

    val MODES = listOf("daily" to "每天", "weekly" to "每周几", "every" to "每隔几天", "cycle" to "吃几天停几天")
    private val HM = Regex("^([01]\\d|2[0-3]):[0-5]\\d$")
    private const val WEEK = "一二三四五六日"

    // ---------------- 计划的保存 / 读取 ----------------
    fun planToJson(p: MedPlan): String = MiniJson.write(mapOf(
        "mode" to p.mode, "times" to p.times.map { mapOf("t" to it.time, "a" to it.amount) },
        "weekdays" to p.weekdays.sorted(), "every" to p.every, "on" to p.on, "off" to p.off, "start" to p.start, "end" to p.end
    ))

    @Suppress("UNCHECKED_CAST")
    fun planFromJson(json: String?): MedPlan {
        val m = try { MiniJson.parse(json ?: "") as? Map<String, Any?> } catch (e: Exception) { null } ?: return MedPlan()
        val times = (m["times"] as? List<Any?>).orEmpty().mapNotNull { x ->
            val t = (x as? Map<String, Any?>) ?: return@mapNotNull null
            val time = t["t"] as? String ?: return@mapNotNull null
            if (!HM.matches(time)) null else MedTime(time, ((t["a"] as? Number)?.toDouble() ?: 1.0).coerceIn(0.0, 1000.0))
        }.distinctBy { it.time }.sortedBy { it.time }
        fun int(k: String, d: Int) = (m[k] as? Number)?.toInt() ?: d
        return MedPlan(
            mode = (m["mode"] as? String)?.takeIf { md -> MODES.any { it.first == md } } ?: "daily",
            times = times.ifEmpty { listOf(MedTime("08:00")) },
            weekdays = (m["weekdays"] as? List<Any?>).orEmpty().mapNotNull { (it as? Number)?.toInt()?.takeIf { d -> d in 1..7 } }.toSet(),
            every = int("every", 2).coerceIn(1, 365), on = int("on", 21).coerceIn(1, 365), off = int("off", 7).coerceIn(0, 365),
            start = (m["start"] as? String).orEmpty(), end = m["end"] as? String
        )
    }

    /** 检查计划，有问题返回提示 */
    fun validate(p: MedPlan): String? = when {
        p.times.isEmpty() -> "至少要有一个服药时间"
        p.times.any { !HM.matches(it.time) } -> "服药时间格式不对"
        p.times.map { it.time }.distinct().size != p.times.size -> "有重复的服药时间"
        p.times.any { it.amount <= 0 } -> "每次用量要大于 0"
        p.mode == "weekly" && p.weekdays.isEmpty() -> "请选择每周哪几天吃"
        p.mode == "every" && p.every < 2 -> "间隔至少 2 天（每天吃请选“每天”）"
        p.mode == "cycle" && p.on < 1 -> "吃药天数至少 1 天"
        p.end != null && p.start.isNotEmpty() && p.end < p.start -> "结束日期不能早于开始日期"
        else -> null
    }

    /** 计划的一句话说明：每天 08:00、20:00 / 每周一三五 08:00 / 每隔 3 天 / 吃 21 天停 7 天 */
    fun describe(p: MedPlan, unit: String = ""): String {
        val head = when (p.mode) {
            "weekly" -> "每周" + p.weekdays.sorted().joinToString("") { WEEK[it - 1].toString() }
            "every" -> "每隔 ${p.every} 天"
            "cycle" -> "吃 ${p.on} 天停 ${p.off} 天"
            else -> "每天"
        }
        val times = p.times.sortedBy { it.time }.joinToString("、") { t -> t.time + (if (t.amount != 1.0) " ${fmt(t.amount)}$unit" else "") }
        return "$head $times"
    }

    fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.2f".format(v).trimEnd('0').trimEnd('.')

    // ---------------- 哪天要吃 ----------------
    fun scheduledOn(p: MedPlan, date: LocalDate): Boolean {
        val start = try { LocalDate.parse(p.start) } catch (e: Exception) { null }
        val end = p.end?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } }
        if (start != null && date.isBefore(start)) return false
        if (end != null && date.isAfter(end)) return false
        val days = if (start != null) ChronoUnit.DAYS.between(start, date) else 0L
        return when (p.mode) {
            "weekly" -> date.dayOfWeek.value in p.weekdays
            "every" -> start == null || days % p.every == 0L
            "cycle" -> start == null || days % (p.on + p.off) < p.on
            else -> true
        }
    }

    /** 某一次服药 */
    data class Dose(val medId: String, val medName: String, val date: LocalDate, val time: String, val amount: Double) {
        val key: String get() = "$medId|$date|$time"
    }

    fun dosesOn(med: Medication, date: LocalDate): List<Dose> {
        if (!med.active) return emptyList()
        val p = planFromJson(med.plan)
        if (!scheduledOn(p, date)) return emptyList()
        return p.times.map { Dose(med.id, med.name, date, it.time, it.amount) }
    }

    fun doseKey(medId: String, date: String, time: String) = "$medId|$date|$time"

    /** 药名按拼音排（中文用户看着顺），同名再按原文 */
    private val nameOrder: Comparator<String> = run {
        val c = java.text.Collator.getInstance(java.util.Locale.CHINA)
        Comparator { a, b -> c.compare(a, b).let { if (it != 0) it else a.compareTo(b) } }
    }

    // ---------------- 今天 ----------------
    enum class Status { TAKEN, SKIPPED, DUE, MISSED, UPCOMING }
    const val DUE_WINDOW_MIN = 120L       // 过了时间 2 小时内算“待服”，再久算“漏服”

    data class DoseState(val dose: Dose, val status: Status, val log: MedLog?)

    fun statusOf(dose: Dose, log: MedLog?, now: LocalDateTime): Status {
        if (log != null) return if (log.status == "skipped") Status.SKIPPED else Status.TAKEN
        val at = LocalDateTime.of(dose.date, java.time.LocalTime.parse(dose.time))
        return when {
            at.isAfter(now) -> Status.UPCOMING
            ChronoUnit.MINUTES.between(at, now) <= DUE_WINDOW_MIN -> Status.DUE
            else -> Status.MISSED
        }
    }

    /** 某天所有要吃的药（按时间、药名排），带状态 */
    fun day(meds: List<Medication>, logs: List<MedLog>, date: LocalDate, now: LocalDateTime): List<DoseState> {
        val byId = logs.filter { it.scheduled }.associateBy { it.id }
        return meds.flatMap { dosesOn(it, date) }.sortedWith(compareBy<Dose> { it.time }.thenBy(nameOrder) { it.medName })
            .map { d -> val log = byId[d.key]; DoseState(d, statusOf(d, log, now), log) }
    }

    // ---------------- 服药率 / 历史 ----------------
    data class Adherence(val due: Int, val taken: Int, val skipped: Int, val missed: Int) {
        /** 服药率（已服用 / 应服用），没有应服用的返回 null */
        val rate: Int? get() = if (due == 0) null else Math.round(taken * 100.0 / due).toInt()
    }

    /** from..to（含）里已经到时间的每一次：多少应吃、吃了、跳过、漏服 */
    fun adherence(meds: List<Medication>, logs: List<MedLog>, from: LocalDate, to: LocalDate, now: LocalDateTime): Adherence {
        var due = 0; var taken = 0; var skipped = 0; var missed = 0
        var d = from
        while (!d.isAfter(to)) {
            for (s in day(meds, logs, d, now)) {
                when (s.status) {
                    Status.UPCOMING, Status.DUE -> {}
                    Status.TAKEN -> { due++; taken++ }
                    Status.SKIPPED -> { due++; skipped++ }
                    Status.MISSED -> { due++; missed++ }
                }
            }
            d = d.plusDays(1)
        }
        return Adherence(due, taken, skipped, missed)
    }

    data class HistoryRow(val date: String, val time: String, val medName: String, val amount: Double, val unit: String,
                          val status: Status, val actionAt: Long?, val extra: Boolean)

    /** 历史记录：每一次（含漏服的、临时加的），新的在前；还没到时间的不算 */
    fun history(meds: List<Medication>, logs: List<MedLog>, from: LocalDate, to: LocalDate, now: LocalDateTime): List<HistoryRow> {
        val unit = meds.associate { it.id to it.unit }
        val out = ArrayList<HistoryRow>()
        var d = from
        while (!d.isAfter(to)) {
            for (s in day(meds, logs, d, now)) {
                if (s.status == Status.UPCOMING || s.status == Status.DUE) continue
                out += HistoryRow(d.toString(), s.dose.time, s.dose.medName, s.dose.amount, unit[s.dose.medId].orEmpty(), s.status, s.log?.actionAt, false)
            }
            d = d.plusDays(1)
        }
        // 计划里已经没有了的记录（比如改了计划、删了药）和临时加的，也要列出来
        val listed = out.map { "${it.date}|${it.time}|${it.medName}" }.toSet()
        for (l in logs) {
            if (l.date < from.toString() || l.date > to.toString()) continue
            if (l.scheduled && "${l.date}|${l.time}|${l.medName}" in listed) continue
            out += HistoryRow(l.date, l.time, l.medName, l.amount, unit[l.medId].orEmpty(),
                if (l.status == "skipped") Status.SKIPPED else Status.TAKEN, l.actionAt, !l.scheduled)
        }
        return out.sortedWith(compareByDescending<HistoryRow> { it.date }.thenByDescending { it.time }.thenBy(nameOrder) { it.medName })
    }

    fun statusText(s: Status) = when (s) {
        Status.TAKEN -> "已服用"; Status.SKIPPED -> "跳过"; Status.DUE -> "待服"; Status.MISSED -> "漏服"; Status.UPCOMING -> "未到时间"
    }

    /** 导出 CSV（Excel 能直接打开：开头加 BOM，字段里有逗号、引号的加引号） */
    fun csv(rows: List<HistoryRow>, zone: ZoneId): String {
        fun q(s: String) = if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
        val sb = StringBuilder("\uFEFF日期,计划时间,药品,用量,状态,记录时间,备注\n")
        for (r in rows) {
            val at = r.actionAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDateTime().toString().replace('T', ' ').take(16) } ?: ""
            sb.append(listOf(r.date, r.time, q(r.medName), fmt(r.amount) + r.unit, statusText(r.status), at, if (r.extra) "临时加的" else "").joinToString(","))
            sb.append('\n')
        }
        return sb.toString()
    }

    // ---------------- 库存 / 有效期 ----------------
    /** 吃了 amount 之后的库存（不记库存的返回 null；不会小于 0） */
    fun afterTake(stock: Double?, amount: Double): Double? = stock?.let { Math.max(0.0, Math.round((it - amount) * 100) / 100.0) }

    fun isLow(med: Medication): Boolean = med.stock != null && med.lowStock != null && med.stock <= med.lowStock

    /** 有效期状态：null 没填 / 过期了几天（负数 = 还有几天） */
    fun daysPastExpiry(med: Medication, today: LocalDate): Long? =
        med.expiry?.let { try { ChronoUnit.DAYS.between(LocalDate.parse(it), today) } catch (e: Exception) { null } }

    /** 按现在的计划，库存还够吃几天（不记库存或计划里没用量返回 null） */
    fun daysLeft(med: Medication, today: LocalDate): Int? {
        val stock = med.stock ?: return null
        var left = stock
        var d = today
        for (i in 0 until 366) {
            val need = dosesOn(med, d).sumOf { it.amount }
            if (need > 0) { if (left < need) return i; left -= need }
            d = d.plusDays(1)
        }
        return 366
    }

    // ---------------- 通知排程 ----------------
    data class Upcoming(val at: Long, val dose: Dose)

    /** 接下来 hours 小时内、还没记录的每一次服药（排通知用），最多 max 条 */
    fun upcoming(meds: List<Medication>, logs: List<MedLog>, now: LocalDateTime, zone: ZoneId, hours: Long = 48, max: Int = 60): List<Upcoming> {
        val ids = logs.map { it.id }.toSet()
        val end = now.plusHours(hours)
        val out = ArrayList<Upcoming>()
        var d = now.toLocalDate()
        while (!d.isAfter(end.toLocalDate())) {
            for (m in meds) for (dose in dosesOn(m, d)) {
                val at = LocalDateTime.of(d, java.time.LocalTime.parse(dose.time))
                if (at.isAfter(now) && !at.isAfter(end) && dose.key !in ids) out += Upcoming(at.atZone(zone).toInstant().toEpochMilli(), dose)
            }
            d = d.plusDays(1)
        }
        return out.sortedBy { it.at }.take(max)
    }
}
