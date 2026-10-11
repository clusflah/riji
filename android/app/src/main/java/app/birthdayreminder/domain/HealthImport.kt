package app.birthdayreminder.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 从 Health Connect（Android 官方的开放健康数据接口）读到的原始数据 -> 每天的身体数据（纯逻辑，可测试）。
 * 手环、手表、体脂秤的 App 只要把数据写进 Health Connect，这里就能用。
 * 只填空着的项，不覆盖手动填的。
 */
object HealthImport {
    data class Sample(val time: Long, val value: Double)                    // 心率、体重、身高、体脂、血氧：时间 + 数值
    data class Span(val start: Long, val end: Long)                         // 睡眠：开始 - 结束
    data class Bp(val time: Long, val systolic: Double, val diastolic: Double)

    data class Raw(
        val heartRate: List<Sample> = emptyList(),       // 次/分
        val restingHr: List<Sample> = emptyList(),
        val sleep: List<Span> = emptyList(),
        val weightKg: List<Sample> = emptyList(),
        val heightM: List<Sample> = emptyList(),          // 米
        val bodyFat: List<Sample> = emptyList(),          // %
        val bloodPressure: List<Bp> = emptyList(),
        val spo2: List<Sample> = emptyList()              // %
    ) {
        val isEmpty: Boolean get() = heartRate.isEmpty() && restingHr.isEmpty() && sleep.isEmpty() && weightKg.isEmpty() &&
            heightM.isEmpty() && bodyFat.isEmpty() && bloodPressure.isEmpty() && spo2.isEmpty()
    }

    private fun day(ms: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    private fun r(v: Double, digits: Int): Double { val p = Math.pow(10.0, digits.toDouble()); return Math.round(v * p) / p }

    /**
     * 按天汇总：静息心率、平均心率取当天平均；睡眠按醒来那天算总时长；
     * 体重、身高、体脂、血压、血氧取当天最后一次。数值都按项目的小数位取整、夹到合理范围。
     */
    fun daily(raw: Raw, zone: ZoneId): Map<LocalDate, Map<String, Double>> {
        val out = HashMap<LocalDate, HashMap<String, Double>>()
        fun put(d: LocalDate, k: String, v: Double) {
            val f = FitnessFields.DEFAULTS.firstOrNull { it.key == k } ?: return
            if (v <= 0 || v.isNaN() || v.isInfinite()) return
            out.getOrPut(d) { HashMap() }[k] = r(v.coerceAtMost(f.max), f.decimals)
        }
        raw.restingHr.groupBy { day(it.time, zone) }.forEach { (d, l) -> put(d, "restingHr", l.map { it.value }.average()) }
        raw.heartRate.groupBy { day(it.time, zone) }.forEach { (d, l) -> put(d, "avgHr", l.map { it.value }.average()) }
        raw.sleep.filter { it.end > it.start }.groupBy { day(it.end, zone) }.forEach { (d, l) -> put(d, "sleep", l.sumOf { (it.end - it.start) / 3_600_000.0 }) }
        fun last(list: List<Sample>, key: String, conv: (Double) -> Double = { it }) =
            list.groupBy { day(it.time, zone) }.forEach { (d, l) -> put(d, key, conv(l.maxByOrNull { it.time }!!.value)) }
        last(raw.weightKg, "weight")
        last(raw.heightM, "height") { it * 100 }
        last(raw.bodyFat, "bodyFat")
        last(raw.spo2, "spo2")
        raw.bloodPressure.groupBy { day(it.time, zone) }.forEach { (d, l) ->
            val b = l.maxByOrNull { it.time }!!
            put(d, "bpSys", b.systolic); put(d, "bpDia", b.diastolic)
        }
        return out
    }

    /** 合并进某天已有的数据：只填空着的项，返回 (合并后的数据, 新填了几项) */
    fun merge(existing: Map<String, Double>, imported: Map<String, Double>): Pair<Map<String, Double>, Int> {
        val out = LinkedHashMap(existing)
        var n = 0
        for ((k, v) in imported) if (out[k] == null) { out[k] = v; n++ }
        return out to n
    }

    // ---------------- 自动同步 ----------------
    const val AUTO_MIN_MINUTES = 15L          // 打开 App 时自动同步：两次之间至少隔 15 分钟
    const val AUTO_DAYS = 7L                  // 每次同步最近 7 天

    /** 打开 App 时要不要自动同步一次 */
    fun shouldAutoSync(enabled: Boolean, lastMs: Long, nowMs: Long, minMinutes: Long = AUTO_MIN_MINUTES): Boolean =
        enabled && (lastMs <= 0 || nowMs - lastMs >= minMinutes * 60_000 || nowMs < lastMs)

    /** 上次同步的说明：今天 21:30 后台同步，填入 3 项 / 昨天 08:05，没有新数据 */
    fun statusText(lastMs: Long, filled: Int, background: Boolean, error: String?, nowMs: Long, zone: ZoneId): String {
        if (lastMs <= 0) return "还没有同步过"
        val t = Instant.ofEpochMilli(lastMs).atZone(zone)
        val today = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate()
        val day = when (t.toLocalDate()) {
            today -> "今天"
            today.minusDays(1) -> "昨天"
            else -> "${t.monthValue}月${t.dayOfMonth}日"
        }
        val head = "上次同步：$day %02d:%02d".format(t.hour, t.minute) + (if (background) "（后台）" else "")
        return head + "，" + when {
            error != null -> error
            filled > 0 -> "填入 $filled 项"
            else -> "没有新数据"
        }
    }
}
