package app.birthdayreminder.lunar

import java.util.GregorianCalendar

/**
 * 农历换算工具，覆盖 1900-01-31（农历1900年正月初一）到 2100-12-31。
 *
 * lunarInfo 里每个数用 20 bit 编码一年的农历信息：
 *   bit16-19: 闰月是哪个月（0 表示当年没有闰月）
 *   bit4-15 : 从高位到低位依次表示正月到十二月，1=大月(30天) 0=小月(29天)
 *   bit0-3  : 闰月是大月还是小月（仅当当年有闰月时有意义），1=30天 0=29天
 *
 * ⚠️ 这张表是农历天文历法数据（很多开源农历库都用同一套值，相当于查表得到的历法事实，
 * 不是某个人的创作内容），但因为是从记忆里整表敲出来的，强烈建议接入前先跑一下
 * 下面的 [selfTest]，用几个公开可查的农历新年日期核对一遍，别直接信任。
 */
object LunarCalendar {
    private const val BASE_YEAR = 1900
    private const val MAX_YEAR = 2100

    private val lunarInfo = intArrayOf(
        0x04bd8,0x04ae0,0x0a570,0x054d5,0x0d260,0x0d950,0x16554,0x056a0,0x09ad0,0x055d2, // 1900-1909
        0x04ae0,0x0a5b6,0x0a4d0,0x0d250,0x1d255,0x0b540,0x0d6a0,0x0ada2,0x095b0,0x14977, // 1910-1919
        0x04970,0x0a4b0,0x0b4b5,0x06a50,0x06d40,0x1ab54,0x02b60,0x09570,0x052f2,0x04970, // 1920-1929
        0x06566,0x0d4a0,0x0ea50,0x06e95,0x05ad0,0x02b60,0x186e3,0x092e0,0x1c8d7,0x0c950, // 1930-1939
        0x0d4a0,0x1d8a6,0x0b550,0x056a0,0x1a5b4,0x025d0,0x092d0,0x0d2b2,0x0a950,0x0b557, // 1940-1949
        0x06ca0,0x0b550,0x15355,0x04da0,0x0a5d0,0x14573,0x052d0,0x0a9a8,0x0e950,0x06aa0, // 1950-1959
        0x0aea6,0x0ab50,0x04b60,0x0aae4,0x0a570,0x05260,0x0f263,0x0d950,0x05b57,0x056a0, // 1960-1969
        0x096d0,0x04dd5,0x04ad0,0x0a4d0,0x0d4d4,0x0d250,0x0d558,0x0b540,0x0b6a0,0x195a6, // 1970-1979
        0x095b0,0x049b0,0x0a974,0x0a4b0,0x0b27a,0x06a50,0x06d40,0x0af46,0x0ab60,0x09570, // 1980-1989
        0x04af5,0x04970,0x064b0,0x074a3,0x0ea50,0x06b58,0x055c0,0x0ab60,0x096d5,0x092e0, // 1990-1999
        0x0c960,0x0d954,0x0d4a0,0x0da50,0x07552,0x056a0,0x0abb7,0x025d0,0x092d0,0x0cab5, // 2000-2009
        0x0a950,0x0b4a0,0x0baa4,0x0ad50,0x055d9,0x04ba0,0x0a5b0,0x15176,0x052b0,0x0a930, // 2010-2019
        0x07954,0x06aa0,0x0ad50,0x05b52,0x04b60,0x0a6e6,0x0a4e0,0x0d260,0x0ea65,0x0d530, // 2020-2029
        0x05aa0,0x076a3,0x096d0,0x04afb,0x04ad0,0x0a4d0,0x1d0b6,0x0d250,0x0d520,0x0dd45, // 2030-2039
        0x0b5a0,0x056d0,0x055b2,0x049b0,0x0a577,0x0a4b0,0x0aa50,0x1b255,0x06d20,0x0ada0, // 2040-2049
        0x14b63,0x09370,0x049f8,0x04970,0x064b0,0x168a6,0x0ea50,0x06b20,0x1a6c4,0x0aae0, // 2050-2059
        0x0a2e0,0x0d2e3,0x0c960,0x0d557,0x0d4a0,0x0da50,0x05d55,0x056a0,0x0a6d0,0x055d4, // 2060-2069
        0x052d0,0x0a9b8,0x0a950,0x0b4a0,0x0b6a6,0x0ad50,0x055a0,0x0aba4,0x0a5b0,0x052b0, // 2070-2079
        0x0b273,0x06930,0x07337,0x06aa0,0x0ad50,0x14b55,0x04b60,0x0a570,0x054e4,0x0d160, // 2080-2089
        0x0e968,0x0d520,0x0daa0,0x16aa6,0x056d0,0x04ae0,0x0a9d4,0x0a2d0,0x0d150,0x0f252, // 2090-2099
        0x0d520 // 2100
    )

    private fun leapMonth(year: Int): Int = lunarInfo[year - BASE_YEAR] and 0xf
    private fun leapDays(year: Int): Int =
        if (leapMonth(year) != 0) (if (lunarInfo[year - BASE_YEAR] and 0x10000 != 0) 30 else 29) else 0
    private fun monthDays(year: Int, month: Int): Int =
        if (lunarInfo[year - BASE_YEAR] and (0x10000 shr month) != 0) 30 else 29
    private fun yearDays(year: Int): Int {
        var sum = 348
        var i = 0x8000
        while (i > 0x8) {
            sum += if (lunarInfo[year - BASE_YEAR] and i != 0) 1 else 0
            i = i shr 1
        }
        return sum + leapDays(year)
    }

    data class LunarDate(
        val year: Int,
        val month: Int,     // 1-12
        val day: Int,       // 1-30
        val isLeap: Boolean
    )

    /** 按农历顺序排出这一年所有月份（含闰月），每项为 (是否闰月, 月份号, 天数) */
    private fun monthsInOrder(year: Int): List<Triple<Boolean, Int, Int>> {
        val leap = leapMonth(year)
        val result = mutableListOf<Triple<Boolean, Int, Int>>()
        for (m in 1..12) {
            result.add(Triple(false, m, monthDays(year, m)))
            if (leap == m) {
                result.add(Triple(true, m, leapDays(year)))
            }
        }
        return result
    }

    /** 阳历 -> 农历 */
    fun solarToLunar(y: Int, m: Int, d: Int): LunarDate {
        val base = GregorianCalendar(BASE_YEAR, 0, 31)
        val target = GregorianCalendar(y, m - 1, d)
        var offset = ((target.timeInMillis - base.timeInMillis) / 86400000L).toInt()

        var lunarYear = BASE_YEAR
        while (lunarYear < MAX_YEAR) {
            val daysOfYear = yearDays(lunarYear)
            if (offset < daysOfYear) break
            offset -= daysOfYear
            lunarYear++
        }

        for ((isLeap, month, days) in monthsInOrder(lunarYear)) {
            if (offset < days) {
                return LunarDate(lunarYear, month, offset + 1, isLeap)
            }
            offset -= days
        }
        // 理论上不会走到这里（说明 yearDays 和 monthsInOrder 的总和对不上，属于表数据错误）
        return LunarDate(lunarYear, 12, 1, false)
    }

    private val monthNames = arrayOf("正","二","三","四","五","六","七","八","九","十","冬","腊")
    private val dayNames1 = arrayOf("初","十","廿","三")
    private val dayNames2 = arrayOf("一","二","三","四","五","六","七","八","九","十")

    fun monthLabel(month: Int, isLeap: Boolean): String =
        (if (isLeap) "闰" else "") + monthNames[month - 1] + "月"

    fun dayLabel(day: Int): String {
        if (day == 10) return "初十"
        if (day == 20) return "二十"
        if (day == 30) return "三十"
        val tenPart = dayNames1[day / 10]
        val onePart = dayNames2[(day % 10) - 1]
        return tenPart + onePart
    }

    /** 日历格子里展示用的短文案：初一显示月份名，其余显示日期 */
    fun cellLabel(lunar: LunarDate): String =
        if (lunar.day == 1) monthLabel(lunar.month, lunar.isLeap) else dayLabel(lunar.day)

    /**
     * 拿几个公开可查的农历新年日期核对表是否敲对了，接入项目前务必先跑一下这个函数，
     * 全部返回 true 才说明表是对的。
     */
    fun selfTest(): Map<String, Boolean> {
        val cases = listOf(
            Triple(1901, 2, 19) to LunarDate(1901, 1, 1, false),
            Triple(2000, 2, 5) to LunarDate(2000, 1, 1, false),
            Triple(2020, 1, 25) to LunarDate(2020, 1, 1, false),
            Triple(2023, 1, 22) to LunarDate(2023, 1, 1, false),
            Triple(2025, 1, 29) to LunarDate(2025, 1, 1, false),
            Triple(2033, 1, 31) to LunarDate(2033, 1, 1, false), // 2033年历法有名的"闰十一月"争议年份，重点核对
            Triple(2033, 12, 22) to LunarDate(2033, 11, 1, true)
        )
        return cases.associate { (solar, expected) ->
            val (y, m, d) = solar
            val got = solarToLunar(y, m, d)
            val label = "$y-$m-$d -> 期望闰月=${expected.isLeap} 农历${expected.month}月${expected.day}日"
            label to (got.year == expected.year && got.month == expected.month &&
                got.day == expected.day && got.isLeap == expected.isLeap)
        }
    }
}
