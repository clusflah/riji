package app.birthdayreminder.domain

import app.birthdayreminder.data.AllergyLog
import app.birthdayreminder.data.FitnessLog
import java.time.LocalDate

/**
 * 健身数据的各个项目。项目列表可以在“项目设置”里调整顺序、隐藏、添加自定义项目，
 * App 启动和改设置后调用 configure() 更新；没配置过就用内置的默认列表。
 */
object FitnessFields {
    /** 项目类型：体重（kg，一位小数）、次数（整数，可再记组数）、时间（分钟）、距离（公里，两位小数）、其它数字（自己填单位） */
    enum class Kind { WEIGHT, COUNT, TIME, DISTANCE, NUMBER, BODY }

    data class Field(
        val key: String,
        val label: String,
        val unit: String,
        val kind: Kind,
        val custom: Boolean = false,
        val visible: Boolean = true,
        val digits: Int? = null,          // 身体数据：小数位数（不填按类型）
        val maxValue: Double? = null      // 身体数据：上限（不填按类型）
    ) {
        /** 身体数据（体重、身高、三围、心率、睡眠…）：测量值，不累加，不算进训练统计 */
        val isBody: Boolean get() = kind == Kind.WEIGHT || kind == Kind.BODY
        /** 小数位数 */
        val decimals: Int get() = digits ?: when (kind) { Kind.WEIGHT, Kind.BODY -> 1; Kind.DISTANCE, Kind.NUMBER -> 2; else -> 0 }
        /** 能不能带小数 */
        val decimal: Boolean get() = decimals > 0
        /** 能不能记组数（只有次数类） */
        val hasSets: Boolean get() = kind == Kind.COUNT
        val max: Double get() = maxValue ?: when (kind) {
            Kind.WEIGHT -> 300.0; Kind.COUNT -> 10000.0; Kind.TIME -> 1440.0; Kind.DISTANCE -> 500.0; Kind.NUMBER -> 1_000_000.0; Kind.BODY -> 1000.0
        }
    }

    /** 内置项目（默认顺序）。散步记分钟，跑步记公里 */
    val DEFAULTS = listOf(
        Field("weight", "体重", "kg", Kind.WEIGHT),
        Field("walk", "散步", "分钟", Kind.TIME),
        Field("run", "跑步", "公里", Kind.DISTANCE),
        Field("pullup", "引体向上", "次", Kind.COUNT),
        Field("pushup", "俯卧撑", "次", Kind.COUNT),
        Field("dip", "臂屈伸", "次", Kind.COUNT),
        Field("jumpSquat", "深蹲跳", "次", Kind.COUNT),
        Field("jumpLunge", "弓箭步跳", "次", Kind.COUNT),
        Field("hangingCrunch", "杠上卷腹", "次", Kind.COUNT),
        Field("abWheel", "健腹轮卷腹", "次", Kind.COUNT),
        Field("muscleUp", "双力臂", "次", Kind.COUNT),
        // 身体数据（外面只显示体重，这些收在“身体数据”里）；心率、睡眠等可以从 Health Connect 同步
        Field("height", "身高", "cm", Kind.BODY, digits = 1, maxValue = 250.0),
        Field("chest", "胸围", "cm", Kind.BODY, digits = 1, maxValue = 200.0),
        Field("waist", "腰围", "cm", Kind.BODY, digits = 1, maxValue = 200.0),
        Field("hip", "臀围", "cm", Kind.BODY, digits = 1, maxValue = 200.0),
        Field("underbust", "胸下围", "cm", Kind.BODY, digits = 1, maxValue = 200.0),
        Field("shoulder", "肩宽", "cm", Kind.BODY, digits = 1, maxValue = 80.0),
        Field("thigh", "大腿围", "cm", Kind.BODY, digits = 1, maxValue = 120.0),
        Field("calf", "小腿围", "cm", Kind.BODY, digits = 1, maxValue = 80.0),
        Field("upperArm", "上臂围", "cm", Kind.BODY, digits = 1, maxValue = 80.0),
        Field("neck", "颈围", "cm", Kind.BODY, digits = 1, maxValue = 80.0),
        Field("bodyFat", "体脂率", "%", Kind.BODY, digits = 1, maxValue = 70.0),
        Field("restingHr", "静息心率", "次/分", Kind.BODY, digits = 0, maxValue = 220.0),
        Field("avgHr", "平均心率", "次/分", Kind.BODY, digits = 0, maxValue = 220.0),
        Field("sleep", "睡眠", "小时", Kind.BODY, digits = 1, maxValue = 24.0),
        Field("bpSys", "高压", "mmHg", Kind.BODY, digits = 0, maxValue = 260.0),
        Field("bpDia", "低压", "mmHg", Kind.BODY, digits = 0, maxValue = 200.0),
        Field("spo2", "血氧", "%", Kind.BODY, digits = 0, maxValue = 100.0)
    )

    /** 当前的项目列表（含隐藏的），按用户排的顺序 */
    @Volatile var ALL: List<Field> = DEFAULTS
        private set

    fun configure(list: List<Field>) { ALL = list.ifEmpty { DEFAULTS } }

    val BY_KEY: Map<String, Field> get() = ALL.associateBy { it.key }
    /** 编辑页显示的项目 */
    val VISIBLE: List<Field> get() = ALL.filter { it.visible }
    /** 可以记组数的项目（次数类） */
    val COUNT_FIELDS: List<Field> get() = ALL.filter { it.hasSets }
    /** 可以累加统计的项目（身体数据以外都算） */
    val SUMMABLE: List<Field> get() = ALL.filter { !it.isBody }
    /** 身体数据里除体重以外的（折叠起来的那些） */
    val BODY_EXTRA: List<Field> get() = ALL.filter { it.kind == Kind.BODY }

    /** BMI = 体重(kg) / 身高(m)²，一位小数；缺一样就没有 */
    fun bmi(weight: Double?, heightCm: Double?): Double? {
        if (weight == null || heightCm == null || heightCm < 50) return null
        val m = heightCm / 100
        return Math.round(weight / (m * m) * 10) / 10.0
    }

    /** 最近一次记的身高（算 BMI 用，身高不是每天都量） */
    fun latestHeight(logs: List<FitnessLog>): Double? =
        logs.filter { it.metrics["height"] != null }.maxByOrNull { it.date }?.metrics?.get("height")

    /** 组数存在同一个 metrics 里，键是“项目键 + Sets”，比如 pullupSets。只有次数类项目有组数。 */
    const val MAX_SETS = 100.0
    fun setsKey(f: Field): String = f.key + "Sets"

    /** 某个键如果是组数键，返回它对应的项目；否则 null */
    fun fieldOfSetsKey(key: String, fields: Map<String, Field> = BY_KEY): Field? =
        if (key.endsWith("Sets")) fields[key.removeSuffix("Sets")]?.takeIf { it.hasSets } else null

    // ---------------- 分组记录 / 极限组（只有次数类项目） ----------------
    // 每组的次数存在 metrics 里：键是“项目键#第几组”（从 1 开始），极限组是“项目键#max”。
    // 有分组时，总次数（项目键）= 各组之和 + 极限组，组数（项目键Sets）= 分组的组数（极限组不算组数）。
    const val MAX_SET_ROWS = 20
    fun groupKey(f: Field, i: Int): String = "${f.key}#$i"
    fun maxKey(f: Field): String = "${f.key}#max"

    /** 某个键如果是分组 / 极限组的键，返回 (项目, 第几组；极限组为 0)；否则 null */
    fun partOfKey(key: String, fields: Map<String, Field> = BY_KEY): Pair<Field, Int>? {
        val i = key.lastIndexOf('#')
        if (i <= 0) return null
        val f = fields[key.substring(0, i)]?.takeIf { it.hasSets } ?: return null
        val part = key.substring(i + 1)
        return if (part == "max") f to 0 else part.toIntOrNull()?.takeIf { it in 1..MAX_SET_ROWS }?.let { f to it }
    }

    /** 这项的每组次数（按组的顺序）；没分组记录返回空 */
    fun setsOf(f: Field, metrics: Map<String, Double>): List<Double> =
        (1..MAX_SET_ROWS).mapNotNull { metrics[groupKey(f, it)] }

    fun maxSetOf(f: Field, metrics: Map<String, Double>): Double? = metrics[maxKey(f)]

    /** 有没有用分组 / 极限组记录 */
    fun hasParts(f: Field, metrics: Map<String, Double>): Boolean = setsOf(f, metrics).isNotEmpty() || maxSetOf(f, metrics) != null

    /**
     * 分组记录 -> 要存的数据：每组的键（重新从 1 连续编号）、极限组、算好的总次数和组数。
     * sets 里的 0 和空会被去掉；什么都没填返回空（这项不记）。
     */
    fun compose(f: Field, sets: List<Double?>, max: Double?): Map<String, Double> {
        val clean = sets.filterNotNull().filter { it > 0 }.take(MAX_SET_ROWS)
        val mx = max?.takeIf { it > 0 }
        if (clean.isEmpty() && mx == null) return emptyMap()
        val out = LinkedHashMap<String, Double>()
        out[f.key] = clean.sum() + (mx ?: 0.0)
        if (clean.isNotEmpty()) out[setsKey(f)] = clean.size.toDouble()
        clean.forEachIndexed { i, v -> out[groupKey(f, i + 1)] = v }
        if (mx != null) out[maxKey(f)] = mx
        return out
    }

    /** 这一项在 metrics 里所有相关的键（总数、组数、各组、极限组） */
    fun keysOf(f: Field): List<String> = listOf(f.key, setsKey(f), maxKey(f)) + (1..MAX_SET_ROWS).map { groupKey(f, it) }

    /** 组数输入：空 = 没填；必须是 1–100 的整数 */
    fun parseSets(field: Field, text: String): Parsed {
        val t = text.trim()
        if (t.isEmpty()) return Parsed.Ok(null)
        val v = t.toDoubleOrNull() ?: return Parsed.Bad("${field.label}的组数要填数字")
        if (v != Math.floor(v) || v < 1) return Parsed.Bad("${field.label}的组数要填 1 以上的整数")
        if (v > MAX_SETS) return Parsed.Bad("${field.label}的组数太大了")
        return Parsed.Ok(v)
    }

    sealed class Parsed {
        data class Ok(val value: Double?) : Parsed()        // null = 没填
        data class Bad(val message: String) : Parsed()
    }

    /** 保留几位小数（四舍五入） */
    fun round(field: Field, v: Double): Double {
        if (field.decimals == 0) return Math.floor(v)
        val p = Math.pow(10.0, field.decimals.toDouble())
        return Math.round(v * p) / p
    }

    /**
     * 输入框文字 -> 数值：空 = 没填。次数、时间要整数；体重一位小数；距离、其它数字两位小数。
     * 体重、距离、身体数据填 0 不算（多半是填错）；次数、时间、其它数字可以填 0。
     */
    fun parse(field: Field, text: String): Parsed {
        val t = text.trim().replace('，', '.').replace('。', '.')
        if (t.isEmpty()) return Parsed.Ok(null)
        val v = t.toDoubleOrNull() ?: return Parsed.Bad("${field.label}要填数字")
        if (v.isNaN() || v.isInfinite() || v < 0) return Parsed.Bad("${field.label}不能是负数")
        if (!field.decimal && v != Math.floor(v)) return Parsed.Bad("${field.label}要填整数")
        if ((field.isBody || field.kind == Kind.DISTANCE) && v == 0.0) return Parsed.Bad("${field.label}不能是 0")
        if (v > field.max) return Parsed.Bad("${field.label}数值太大了")
        return Parsed.Ok(round(field, v))
    }

    /** 数字本身（去掉末尾多余的 0）：70.50 -> 70.5，5.0 -> 5 */
    private fun num(field: Field, v: Double): String {
        if (field.decimals == 0 || v == Math.floor(v)) return v.toLong().toString()
        return ("%." + field.decimals + "f").format(v).trimEnd('0').trimEnd('.')
    }

    /** 数值 -> 显示文字：体重 70.5kg、次数 12、散步 30分钟、跑步 5.25公里、自定义 1200步 */
    fun format(field: Field, v: Double): String = when (field.kind) {
        Kind.COUNT -> num(field, v)
        else -> num(field, v) + field.unit
    }

    /** 输入框里回填的文字（不带单位） */
    fun inputText(field: Field, v: Double?): String = if (v == null) "" else num(field, v)

    /** 一个项目的显示文字：体重 70.5kg / 引体向上 30（3组）/ 只填了组数时 引体向上 3组；都没填返回 null */
    fun itemText(f: Field, metrics: Map<String, Double>): String? {
        val v = metrics[f.key]
        val sets = if (f.hasSets) metrics[setsKey(f)] else null
        // 分组记录：引体向上 45（12/10/8 · 极限 15）
        if (f.hasSets && hasParts(f, metrics)) {
            val parts = setsOf(f, metrics).joinToString("/") { it.toLong().toString() }
            val mx = maxSetOf(f, metrics)?.let { "极限 ${it.toLong()}" }
            return f.label + " " + format(f, v ?: 0.0) + "（" + listOf(parts.ifEmpty { null }, mx).filterNotNull().joinToString(" · ") + "）"
        }
        return when {
            v != null && sets != null -> f.label + " " + format(f, v) + "（${sets.toLong()}组）"
            v != null -> f.label + " " + format(f, v)
            sets != null -> f.label + " ${sets.toLong()}组"
            else -> null
        }
    }

    /** 列表里一行摘要：只列填了的项，按固定顺序 */
    fun summary(log: FitnessLog): String {
        val parts = ALL.filter { it.kind != Kind.BODY }.mapNotNull { f -> itemText(f, log.metrics) }.toMutableList()
        val body = BODY_EXTRA.count { log.metrics[it.key] != null }
        if (body > 0) parts += "身体数据 $body 项"
        return if (parts.isEmpty()) "（没有填数据）" else parts.joinToString(" · ")
    }

    /** 身体数据展开后的文字：身高 172cm · 腰围 80cm · 静息心率 58次/分 · 睡眠 7.5小时 */
    fun bodyText(log: FitnessLog): String = BODY_EXTRA.mapNotNull { f -> log.metrics[f.key]?.let { f.label + " " + format(f, it) } }.joinToString(" · ")

    data class Stats(
        val latestWeight: Double?, val latestWeightDate: String?,
        val weightChange: Double?,                   // 和上一次记录体重相比的变化
        val weekTotals: List<Pair<Field, Double>>,   // 最近 7 天（含今天）各项目次数合计，只列大于 0 的
        val weekSets: Map<String, Double> = emptyMap()  // 同期各项目的组数合计（项目键 -> 组数），没填组数的项目不在里面
    )

    fun stats(logs: List<FitnessLog>, today: LocalDate): Stats {
        val withWeight = logs.filter { it.metrics["weight"] != null }.sortedByDescending { it.date }
        val latest = withWeight.firstOrNull()
        val prev = withWeight.getOrNull(1)
        val from = today.minusDays(6).toString()
        val to = today.toString()
        val week = logs.filter { it.date in from..to }
        val totals = SUMMABLE.map { f -> f to round(f, week.sumOf { it.metrics[f.key] ?: 0.0 }) }.filter { it.second > 0 }
        val sets = COUNT_FIELDS.associate { f -> f.key to week.sumOf { it.metrics[setsKey(f)] ?: 0.0 } }.filterValues { it > 0 }
        val change = if (latest != null && prev != null) Math.round((latest.metrics.getValue("weight") - prev.metrics.getValue("weight")) * 10) / 10.0 else null
        return Stats(latest?.metrics?.get("weight"), latest?.date, change, totals, sets)
    }
}

/** 7 天合计里一个项目的文字：引体向上 30（3组） */
fun FitnessFields.Stats.weekText(f: FitnessFields.Field, total: Double): String =
    f.label + " " + FitnessFields.format(f, total) + (weekSets[f.key]?.let { "（${it.toLong()}组）" } ?: "")

/** 过敏 / 哮喘记录：三个症状，每个四档程度 */
object AllergyLogic {
    val LEVELS = listOf("无", "轻", "中", "重")
    val SYMPTOMS = listOf("eye" to "眼睛过敏", "nose" to "鼻子过敏", "cough" to "咳嗽")

    fun level(log: AllergyLog, key: String): Int = when (key) {
        "eye" -> log.eye
        "nose" -> log.nose
        "cough" -> log.cough
        else -> 0
    }.coerceIn(0, 3)

    fun hasSymptom(log: AllergyLog): Boolean = log.eye > 0 || log.nose > 0 || log.cough > 0

    /** 列表里一行摘要：只列有症状的项 */
    fun summary(log: AllergyLog): String {
        val parts = SYMPTOMS.mapNotNull { (k, label) -> level(log, k).takeIf { it > 0 }?.let { "$label ${LEVELS[it]}" } }
        return if (parts.isEmpty()) "无症状" else parts.joinToString(" · ")
    }

    /** 最近 30 天（含今天）每个症状出现了几天 */
    fun last30Days(logs: List<AllergyLog>, today: LocalDate): List<Pair<String, Int>> {
        val from = today.minusDays(29).toString()
        val to = today.toString()
        val recent = logs.filter { it.date in from..to }
        return SYMPTOMS.map { (k, label) -> label to recent.count { level(it, k) > 0 } }
    }
}
