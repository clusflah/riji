package app.birthdayreminder.domain

import app.birthdayreminder.data.Birthday
import app.birthdayreminder.data.Phrase
import app.birthdayreminder.data.Reminder

/** 搜索用的一篇日记（正文已经从文字块里取出来，图片不参与搜索） */
data class SearchDiary(val id: String, val date: String, val text: String, val address: String?, val isPrivate: Boolean = false)

data class SearchHit(
    val kind: String,           // diary / reminder / birthday / phrase
    val id: String,
    val title: String,          // 列表里第一行
    val snippet: String,        // 第二行：截取关键词附近的一段
    val sortKey: String         // 同类里怎么排：日记按日期新到旧，其它按标题
)

/**
 * 全局搜索：同时搜日记、提醒、生日、常用语。
 * - 不区分大小写；空格分开的多个词要同时出现（比如“医院 复查”）；
 * - 私密内容不出现；
 * - 摘要截取第一个关键词附近的一段，关键词位置用 highlights 算出来给界面加粗。
 * 纯逻辑，可测试。
 */
object SearchLogic {
    const val SNIPPET = 60
    val KIND_LABEL = mapOf("diary" to "日记", "reminder" to "提醒", "birthday" to "生日", "phrase" to "常用语")
    val KIND_ORDER = listOf("diary", "reminder", "birthday", "phrase")

    fun terms(q: String): List<String> = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.map { it.lowercase() }

    private fun matchesAll(text: String, ts: List<String>): Boolean {
        val l = text.lowercase()
        return ts.all { l.contains(it) }
    }

    /** 截取第一个关键词附近的一段，前后加省略号；多行压成一行 */
    fun snippet(text: String, ts: List<String>, max: Int = SNIPPET): String {
        val flat = text.replace(Regex("\\s+"), " ").trim()
        if (flat.length <= max) return flat
        val l = flat.lowercase()
        val first = ts.mapNotNull { t -> l.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: 0
        val start = (first - max / 3).coerceIn(0, flat.length - max)
        val end = (start + max).coerceAtMost(flat.length)
        return (if (start > 0) "…" else "") + flat.substring(start, end) + (if (end < flat.length) "…" else "")
    }

    /** 关键词在文字里出现的所有位置（[开始, 结束)），给界面加粗用；重叠的合并 */
    fun highlights(text: String, ts: List<String>): List<IntRange> {
        val l = text.lowercase()
        val ranges = ArrayList<IntRange>()
        for (t in ts) {
            var i = l.indexOf(t)
            while (i >= 0) { ranges.add(i until i + t.length); i = l.indexOf(t, i + t.length) }
        }
        val sorted = ranges.sortedBy { it.first }
        val out = ArrayList<IntRange>()
        for (r in sorted) {
            val last = out.lastOrNull()
            if (last != null && r.first <= last.last + 1) out[out.size - 1] = last.first..maxOf(last.last, r.last)
            else out.add(r)
        }
        return out
    }

    fun search(
        query: String, diaries: List<SearchDiary>, reminders: List<Reminder>,
        birthdays: List<Birthday>, phrases: List<Phrase>
    ): List<SearchHit> {
        val ts = terms(query)
        if (ts.isEmpty()) return emptyList()
        val out = ArrayList<SearchHit>()
        for (d in diaries) {
            if (d.isPrivate) continue
            val all = d.text + " " + (d.address ?: "")
            if (!matchesAll(all, ts)) continue
            val body = if (matchesAll(d.text, ts.take(1))) d.text else all
            out += SearchHit("diary", d.id, d.date + (if (!d.address.isNullOrBlank()) " · 📍" + d.address else ""), snippet(body, ts), d.date)
        }
        for (r in reminders) {
            if (r.isPrivate) continue
            if (!matchesAll(r.title + " " + r.note, ts)) continue
            val whenText = if (Occurrence.isTodo(r)) "待办" else r.date + (if (r.time.isNotBlank()) " " + r.time else "")
            out += SearchHit("reminder", r.id, r.title, whenText + (if (r.note.isNotBlank()) " · " + snippet(r.note, ts, 40) else ""), r.title)
        }
        for (b in birthdays) {
            if (b.isPrivate) continue
            if (!matchesAll(b.name + " " + b.note, ts)) continue
            out += SearchHit("birthday", b.id, "🎂 " + b.name, Occurrence.birthdayDesc(b) + (if (b.note.isNotBlank()) " · " + b.note else ""), b.name)
        }
        for (p in phrases) {
            if (p.isPrivate) continue
            if (!matchesAll(p.title + " " + p.value + " " + p.category, ts)) continue
            out += SearchHit("phrase", p.id, p.title, snippet(p.value, ts, 40), p.title)
        }
        return out.sortedWith(compareBy<SearchHit> { KIND_ORDER.indexOf(it.kind) }.thenComparator { a, b ->
            if (a.kind == "diary") b.sortKey.compareTo(a.sortKey) else a.sortKey.compareTo(b.sortKey)
        })
    }
}
