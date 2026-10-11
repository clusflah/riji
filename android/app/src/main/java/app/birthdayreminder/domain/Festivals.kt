package app.birthdayreminder.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.sin

/** 某一天的节日信息：festivals = 节日名（农历节日在前），term = 节气（没有为 null） */
data class DayFestival(val festivals: List<String>, val term: String?) {
    /** 日历格子里代替农历显示的文字：农历节日 > 公历节日 > 节气；都没有返回 null */
    val cellText: String? get() = festivals.firstOrNull() ?: term
    val isEmpty: Boolean get() = festivals.isEmpty() && term == null
    /** 当天日程面板里的一行，如“中秋节 · 秋分” */
    val panelText: String? get() = (festivals + listOfNotNull(term)).distinct().takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * 中国节日与二十四节气。
 * 节气用的是旧版“历”里的天文算法（太阳视黄经每 15° 一个节气，按北京时间取日期），原样移植，结果和旧版一致；
 * 节日只保留中国的传统节日（农历）和国内的公历节日。
 */
object Festivals {
    val TERMS = listOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分", "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分", "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    /** 公历节日（月-日） */
    val SOLAR = mapOf(
        "1-1" to "元旦", "3-8" to "妇女节", "3-12" to "植树节", "5-1" to "劳动节", "5-4" to "青年节",
        "6-1" to "儿童节", "7-1" to "建党节", "8-1" to "建军节", "9-10" to "教师节", "10-1" to "国庆节"
    )

    /** 农历节日（农历月-日，只认非闰月） */
    val LUNAR = mapOf(
        "1-1" to "春节", "1-15" to "元宵节", "2-2" to "龙抬头", "5-5" to "端午节", "7-7" to "七夕",
        "7-15" to "中元节", "8-15" to "中秋节", "9-9" to "重阳节", "10-1" to "寒衣节", "12-8" to "腊八节", "12-23" to "小年"
    )

    private const val DEG = PI / 180   // 角度 -> 弧度

    private fun sunLon(t: Double): Double {
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = (357.52911 + 35999.05029 * t - 0.0001537 * t * t) * DEG
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) + (0.019993 - 0.000101 * t) * sin(2 * m) + 0.000289 * sin(3 * m)
        val om = (125.04 - 1934.136 * t) * DEG
        val lon = l0 + c - 0.00569 - 0.00478 * sin(om)
        return ((lon % 360) + 360) % 360
    }

    private val cache = HashMap<Int, Map<LocalDate, String>>()

    /** 某一年 24 个节气各在哪一天（北京时间） */
    @Synchronized
    fun terms(year: Int): Map<LocalDate, String> = cache.getOrPut(year) {
        val map = HashMap<LocalDate, String>()
        for (i in 0 until 24) {
            val target = ((285 + 15 * i) % 360).toDouble()
            // 1 月 5 日 0 点(UTC) 的儒略日，再按每个节气约 15.2184 天往后推，作为迭代起点
            var jd = LocalDate.of(year, 1, 5).toEpochDay() + 2440587.5 + i * 15.2184
            repeat(6) {
                val t = (jd - 2451545) / 36525
                var diff = target - sunLon(t)
                diff = ((diff + 540) % 360) - 180
                jd += diff / 0.9856474
            }
            val ms = ((jd - 2440587.5) * 86400000).toLong() + 8L * 3600000   // 转成北京时间
            val d = Instant.ofEpochMilli(ms).atOffset(ZoneOffset.UTC).toLocalDate()
            map[d] = TERMS[i]
        }
        map
    }


    fun of(d: LocalDate): DayFestival {
        val fests = ArrayList<String>()
        val l = LunarCache.of(d)
        if (!l.isLeap) {
            LUNAR["${l.month}-${l.day}"]?.let { fests.add(it) }
            // 除夕：腊月的最后一天（有的年份是廿九）
            if (l.month == 12 && fests.isEmpty()) {
                val n = LunarCache.of(d.plusDays(1))
                if (n.month == 1 && n.day == 1 && !n.isLeap) fests.add("除夕")
            }
        }
        SOLAR["${d.monthValue}-${d.dayOfMonth}"]?.let { fests.add(it) }
        val term = terms(d.year)[d]
        return DayFestival(fests, term)
    }

}
