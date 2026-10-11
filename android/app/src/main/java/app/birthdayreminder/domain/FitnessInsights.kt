package app.birthdayreminder.domain

import app.birthdayreminder.ai.MiniJson
import app.birthdayreminder.data.FitnessLog
import java.time.LocalDate

/** 健身数据的分析：体重曲线、个人最好、连续打卡（纯逻辑） */
object FitnessInsights {
    data class Point(val date: LocalDate, val value: Double)

    /** 体重曲线上的点：按日期排，只取最近 days 天（默认 90 天）有体重的记录 */
    fun weightSeries(logs: List<FitnessLog>, today: LocalDate, days: Int = 90): List<Point> {
        val from = today.minusDays((days - 1).toLong())
        return logs.mapNotNull { l ->
            val d = try { LocalDate.parse(l.date) } catch (e: Exception) { return@mapNotNull null }
            val w = l.metrics["weight"] ?: return@mapNotNull null
            if (d.isBefore(from) || d.isAfter(today)) null else Point(d, w)
        }.sortedBy { it.date }
    }

    data class Best(val field: FitnessFields.Field, val value: Double, val date: String)

    /** 每个项目（体重以外）的单日最多（同样多时取最早那天，代表“第一次达到”） */
    fun bests(logs: List<FitnessLog>): List<Best> =
        FitnessFields.SUMMABLE.mapNotNull { f ->
            logs.filter { (it.metrics[f.key] ?: 0.0) > 0 }
                .sortedWith(compareByDescending<FitnessLog> { it.metrics.getValue(f.key) }.thenBy { it.date })
                .firstOrNull()?.let { Best(f, it.metrics.getValue(f.key), it.date) }
        }

    /** 每个次数项目的单组最多（分组和极限组里最高的一组；同样多时取最早那天） */
    fun bestSets(logs: List<FitnessLog>): List<Best> =
        FitnessFields.COUNT_FIELDS.mapNotNull { f ->
            logs.mapNotNull { l ->
                val top = (FitnessFields.setsOf(f, l.metrics) + listOfNotNull(FitnessFields.maxSetOf(f, l.metrics))).maxOrNull()
                top?.let { l.date to it }
            }.sortedWith(compareByDescending<Pair<String, Double>> { it.second }.thenBy { it.first })
                .firstOrNull()?.let { Best(f, it.second, it.first) }
        }

    data class Streak(val current: Int, val longest: Int, val trainedToday: Boolean)

    /**
     * 连续打卡：有填任何训练数据（次数、散步、跑步、自定义项目都算，只记体重不算）的日子算一天。
     * 当前连续：从今天往回数；如果今天还没练，从昨天往回数（不会因为今天还没练就清零）。
     */
    fun streak(logs: List<FitnessLog>, today: LocalDate): Streak {
        val days = logs.filter { l -> FitnessFields.SUMMABLE.any { (l.metrics[it.key] ?: 0.0) > 0 } }
            .mapNotNull { try { LocalDate.parse(it.date) } catch (e: Exception) { null } }
            .filter { !it.isAfter(today) }.toSortedSet()
        val trainedToday = today in days
        var cur = 0
        var d = if (trainedToday) today else today.minusDays(1)
        while (d in days) { cur++; d = d.minusDays(1) }
        var longest = 0; var run = 0; var prev: LocalDate? = null
        for (x in days) {
            run = if (prev != null && prev.plusDays(1) == x) run + 1 else 1
            if (run > longest) longest = run
            prev = x
        }
        return Streak(cur, longest, trainedToday)
    }
}

/** 常用训练组合：一个名字 + 若干项目的次数 / 组数（不含体重），可一键填进记录页 */
data class FitnessTemplate(val name: String, val metrics: Map<String, Double>)

object FitnessTemplates {
    const val MAX = 20

    /** 从记录页当前填的内容生成组合：只保留次数和组数，体重每天不同不放进去 */
    fun fromMetrics(name: String, metrics: Map<String, Double>): FitnessTemplate? {
        val n = name.trim().take(12)
        if (n.isEmpty()) return null
        val fields = FitnessFields.BY_KEY
        val m = metrics.filterKeys { k -> k != "weight" && ((fields[k]?.isBody == false) ||
            FitnessFields.fieldOfSetsKey(k, fields) != null || FitnessFields.partOfKey(k, fields) != null) }
            .filterValues { it > 0 }
        return if (m.isEmpty()) null else FitnessTemplate(n, m)
    }

    /** 保存：同名覆盖，新的放最前，最多 20 个 */
    fun upsert(list: List<FitnessTemplate>, t: FitnessTemplate): List<FitnessTemplate> =
        (listOf(t) + list.filter { it.name != t.name }).take(MAX)

    fun remove(list: List<FitnessTemplate>, name: String): List<FitnessTemplate> = list.filter { it.name != name }

    fun toJson(list: List<FitnessTemplate>): String = MiniJson.write(list.map { mapOf("name" to it.name, "metrics" to it.metrics) })

    @Suppress("UNCHECKED_CAST")
    fun fromJson(json: String?): List<FitnessTemplate> = try {
        (MiniJson.parse(json ?: "[]") as? List<Any?>).orEmpty().mapNotNull { x ->
            val m = x as? Map<String, Any?> ?: return@mapNotNull null
            val name = (m["name"] as? String)?.trim().orEmpty()
            val metrics = (m["metrics"] as? Map<String, Any?>).orEmpty().mapNotNull { (k, v) ->
                val n = (v as? Number)?.toDouble() ?: return@mapNotNull null
                val fields = FitnessFields.BY_KEY
                if (k == "weight" || n <= 0) null
                else if ((fields[k]?.isBody == false) || FitnessFields.fieldOfSetsKey(k, fields) != null ||
                    FitnessFields.partOfKey(k, fields) != null) k to n else null
            }.toMap()
            if (name.isEmpty() || metrics.isEmpty()) null else FitnessTemplate(name, metrics)
        }
    } catch (e: Exception) { emptyList() }

    /** 一键填入：把组合里的项目填进输入框（键 -> 文字）；已经填了体重等其它项不动 */
    fun applyTo(inputs: Map<String, String>, t: FitnessTemplate): Map<String, String> {
        val out = inputs.toMutableMap()
        for ((k, v) in t.metrics) {
            val f = FitnessFields.BY_KEY[k]
            out[k] = if (f != null) FitnessFields.inputText(f, v) else v.toLong().toString()      // 跑步 5.5 公里不会变成 5，分组按整数
        }
        return out
    }

    /** 组合的一句话说明，比如“引体向上 30（3组）· 俯卧撑 50” */
    fun describe(t: FitnessTemplate): String =
        FitnessFields.ALL.mapNotNull { FitnessFields.itemText(it, t.metrics) }.joinToString(" · ")
}
