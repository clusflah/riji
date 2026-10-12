package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.data.StudyLog
import java.time.LocalDate

/** 学习记录里的一项：比如“逻辑填空 20 题” */
data class StudyItem(val name: String, val amount: Double? = null, val unit: String = "")

/**
 * 学习的规则（纯逻辑）：一条记录里各项的存取、显示文字、统计，以及从旧日记里识别“名称 数字”的写法。
 */
object StudyLogic {
    val UNITS = listOf("题", "页", "篇", "个", "章", "节", "课", "词")

    fun itemsToJson(list: List<StudyItem>): String = MiniJson.write(list.filter { it.name.isNotBlank() }.map { i ->
        mapOf("name" to i.name.trim(), "amount" to i.amount, "unit" to i.unit.trim())
    })

    fun itemsFromJson(s: String?): List<StudyItem> = (try { MiniJson.parse(s ?: "") } catch (e: Exception) { null } as? List<*>).orEmpty().mapNotNull { x ->
        val m = x as? Map<*, *> ?: return@mapNotNull null
        val name = (m["name"] as? String)?.trim()?.ifEmpty { null } ?: return@mapNotNull null
        StudyItem(name, (m["amount"] as? Number)?.toDouble(), (m["unit"] as? String).orEmpty())
    }

    /** 20.0 显示成 20，2.5 还是 2.5 */
    fun fmtAmount(d: Double?): String = when {
        d == null -> ""
        d == Math.floor(d) && !d.isInfinite() -> d.toLong().toString()
        else -> "%.2f".format(d).trimEnd('0').trimEnd('.')
    }

    fun itemText(i: StudyItem): String = listOf(i.name, fmtAmount(i.amount) + i.unit).filter { it.isNotBlank() }.joinToString(" ")

    /** 一条记录的摘要：逻辑填空 20题 · 图推 20题 */
    fun summary(items: List<StudyItem>): String = items.joinToString(" · ") { itemText(it) }

    /** 85 -> “1小时25分”，30 -> “30分钟” */
    fun fmtMinutes(m: Int): String = when {
        m <= 0 -> ""
        m < 60 -> "${m}分钟"
        m % 60 == 0 -> "${m / 60}小时"
        else -> "${m / 60}小时${m % 60}分"
    }

    data class ItemTotal(val name: String, val unit: String, val amount: Double, val times: Int)
    data class Stats(val minutes: Int, val days: Int, val records: Int, val items: List<ItemTotal>)

    /** 一段时间（含首尾）的统计：总时长、学了几天、每项内容合计（同名同单位的加在一起，按次数多少排） */
    fun stats(logs: List<StudyLog>, from: LocalDate, to: LocalDate): Stats {
        val f = from.toString(); val t = to.toString()
        val inRange = logs.filter { it.date in f..t }
        val totals = LinkedHashMap<Pair<String, String>, ItemTotal>()
        for (l in inRange) for (i in itemsFromJson(l.items)) {
            val k = i.name to i.unit
            val old = totals[k] ?: ItemTotal(i.name, i.unit, 0.0, 0)
            totals[k] = old.copy(amount = old.amount + (i.amount ?: 0.0), times = old.times + 1)
        }
        return Stats(inRange.sumOf { it.minutes }, inRange.map { it.date }.distinct().size, inRange.size,
            totals.values.sortedWith(compareByDescending<ItemTotal> { it.times }.thenByDescending { it.amount }))
    }

    /** 连续学习天数（到今天或昨天为止） */
    fun streak(logs: List<StudyLog>, today: LocalDate): Int {
        val days = logs.map { it.date }.toSet()
        var x = if (today.toString() in days) today else today.minusDays(1)
        var n = 0
        while (x.toString() in days) { n++; x = x.minusDays(1) }
        return n
    }

    // ---------------- 从旧日记导入 ----------------
    private val ITEM = Regex("([\\p{IsHan}A-Za-z][\\p{IsHan}A-Za-z·]*)\\s*(\\d+(?:\\.\\d+)?)\\s*(" + UNITS.joinToString("|") + "|道)?")

    /**
     * 识别日记正文里的“名称 数字”：比如“逻辑填空 20 图推 20 逻辑判断 10”→ 三项。
     * 识别出来的部分要占正文的大半（去掉空白后 ≥ 60%），不然多半是普通日记，返回空，免得误导入。
     */
    fun parseDiaryText(text: String): List<StudyItem> {
        val compact = text.filterNot { it.isWhitespace() }
        if (compact.isEmpty()) return emptyList()
        val found = ITEM.findAll(text).toList()
        if (found.isEmpty()) return emptyList()
        val covered = found.sumOf { m -> m.value.count { !it.isWhitespace() } }
        if (covered * 10 < compact.length * 6) return emptyList()
        return found.map { m ->
            StudyItem(m.groupValues[1], m.groupValues[2].toDoubleOrNull(), m.groupValues[3].let { if (it == "道") "题" else it })
        }
    }
}
