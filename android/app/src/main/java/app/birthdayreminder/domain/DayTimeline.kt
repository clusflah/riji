package app.birthdayreminder.domain

/**
 * 首页的时间轴：选中这一天的事，“全天”的放最上面（保持原来的顺序），有时间的按时间排。纯逻辑，可测试。
 */
object DayTimeline {
    data class Entry(val time: String?, val kind: String, val title: String, val subtitle: String? = null, val ref: String? = null)

    /** “8:05” -> “08:05”；看不懂的返回 null（当作全天） */
    fun normalize(t: String?): String? {
        val m = Regex("^\\s*(\\d{1,2}):(\\d{2})\\s*$").find(t ?: return null) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        return "%02d:%02d".format(h, mi)
    }

    fun order(entries: List<Entry>): List<Entry> {
        val withT = entries.map { it.copy(time = normalize(it.time)) }
        return withT.filter { it.time == null } + withT.filter { it.time != null }.sortedBy { it.time }
    }
}
