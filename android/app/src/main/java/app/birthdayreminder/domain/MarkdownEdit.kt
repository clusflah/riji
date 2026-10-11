package app.birthdayreminder.domain

data class EditResult(val text: String, val selStart: Int, val selEnd: Int)

/** 编辑器工具栏用的 Markdown 文本操作，输入输出都是 (文本, 选区)，不依赖 Compose。 */
object MarkdownEdit {

    /** 给选中文字包上标记（**加粗** / *斜体*）；已经被这个标记包住就再点一次取消 */
    fun wrap(text: String, start: Int, end: Int, mark: String): EditResult {
        val s = minOf(start, end).coerceIn(0, text.length)
        val e = maxOf(start, end).coerceIn(0, text.length)
        val m = mark.length
        val wrapped = s >= m && e + m <= text.length &&
            text.substring(s - m, s) == mark && text.substring(e, e + m) == mark
        // 斜体的单个 * 要排除“其实是加粗 ** 的一半”
        val partOfBolder = m == 1 && (
            (s - m - 1 >= 0 && text[s - m - 1] == mark[0]) || (e + m < text.length && text[e + m] == mark[0])
        )
        if (wrapped && !partOfBolder) {
            val nt = text.substring(0, s - m) + text.substring(s, e) + text.substring(e + m)
            return EditResult(nt, s - m, e - m)
        }
        val nt = text.substring(0, s) + mark + text.substring(s, e) + mark + text.substring(e)
        return EditResult(nt, s + m, e + m)
    }

    private fun lineStart(text: String, cursor: Int): Int =
        if (cursor <= 0) 0 else text.lastIndexOf('\n', cursor - 1) + 1

    /** 在光标所在行的行首加/去一个前缀（列表 "- "、引用 "> "） */
    fun toggleLinePrefix(text: String, cursor: Int, prefix: String): EditResult {
        val c = cursor.coerceIn(0, text.length)
        val ls = lineStart(text, c)
        return if (text.startsWith(prefix, ls)) {
            val nc = maxOf(ls, c - prefix.length)
            EditResult(text.removeRange(ls, ls + prefix.length), nc, nc)
        } else {
            val nc = c + prefix.length
            EditResult(text.substring(0, ls) + prefix + text.substring(ls), nc, nc)
        }
    }

    private val headingRegex = Regex("^#{1,6} ")

    /**
     * 光标所在行设成标题：level 1 = 标题（# ），2 = 小标题（## ），0 = 正文（去掉标题符号）。
     * 已经是这一级再点一次就变回正文；原来是别的级别会被替换。
     */
    fun toggleHeading(text: String, cursor: Int, level: Int = 2): EditResult {
        val c = cursor.coerceIn(0, text.length)
        val ls = lineStart(text, c)
        val rest = text.substring(ls)
        val old = headingRegex.find(rest)?.value ?: ""
        val want = if (level <= 0) "" else "#".repeat(level) + " "
        val target = if (old == want) "" else want
        val nt = text.substring(0, ls) + target + text.substring(ls + old.length)
        val nc = maxOf(ls + target.length, c - old.length + target.length)
        return EditResult(nt, nc, nc)
    }

    /** 列表的种类：待办、有序（1. ）、小圆点（* ）、小横线（- ） */
    enum class ListType { TASK, NUMBER, DOT, DASH }

    /** 行首已有的列表前缀（含缩进） */
    private val anyListPrefix = Regex("^(\\s*)(- \\[[ xX]\\] |[-*•] \\[[ xX]\\] |[*•+] |- |\\d{1,3}(?:\\. |、 ?))")

    private fun typeOf(prefix: String): ListType = when {
        prefix.contains("[") -> ListType.TASK
        prefix.trim().first().isDigit() -> ListType.NUMBER
        prefix.trim().startsWith("-") -> ListType.DASH
        else -> ListType.DOT
    }

    /** 上一行如果是有序列表，返回它的数字 */
    private fun previousNumber(text: String, ls: Int): Int? {
        if (ls == 0) return null
        val prevStart = lineStart(text, ls - 1)
        val prev = text.substring(prevStart, ls - 1)
        return Regex("^\\s*(\\d{1,3})(?:\\. |、 ?)").find(prev)?.groupValues?.get(1)?.toIntOrNull()
    }

    /**
     * 光标所在行设成某种列表。已经是这种列表再点一次就取消；
     * 原来是别的列表就直接换掉（不会叠成 “- 1. 内容”）。有序列表的数字接着上一行往下编。
     */
    fun toggleList(text: String, cursor: Int, type: ListType): EditResult {
        val c = cursor.coerceIn(0, text.length)
        val ls = lineStart(text, c)
        val rest = text.substring(ls)
        val m = anyListPrefix.find(rest)
        val indent = m?.groupValues?.get(1) ?: ""
        val oldLen = m?.value?.length ?: 0
        val newPrefix = if (m != null && typeOf(m.groupValues[2]) == type) "" else indent + when (type) {
            ListType.TASK -> "- [ ] "
            ListType.NUMBER -> "${(previousNumber(text, ls) ?: 0) + 1}. "
            ListType.DOT -> "* "
            ListType.DASH -> "- "
        }
        val nt = text.substring(0, ls) + newPrefix + text.substring(ls + oldLen)
        val nc = maxOf(ls + newPrefix.length, c - oldLen + newPrefix.length)
        return EditResult(nt, nc, nc)
    }

    /** 预览里点待办的复选框：第 lineIndex 行的 [ ] 和 [x] 互换 */
    fun toggleTask(text: String, lineIndex: Int): String {
        val lines = text.split("\n").toMutableList()
        val l = lines.getOrNull(lineIndex) ?: return text
        val r = Regex("^(\\s*[-*•] \\[)([ xX])(\\])")
        val m = r.find(l) ?: return text
        val flipped = if (m.groupValues[2] == " ") "x" else " "
        lines[lineIndex] = m.groupValues[1] + flipped + m.groupValues[3] + l.substring(m.range.last + 1)
        return lines.joinToString("\n")
    }

    const val TABLE_TEMPLATE = "| 列1 | 列2 | 列3 |\n| --- | --- | --- |\n|  |  |  |\n|  |  |  |"

    /** 在光标处插入一张 3 列表格模板（自己占几行，前后空一行），光标放到第一个空单元格 */
    fun insertTable(text: String, cursor: Int): EditResult {
        val c = cursor.coerceIn(0, text.length)
        val before = text.substring(0, c)
        val after = text.substring(c)
        val lead = when { before.isEmpty() -> ""; before.endsWith("\n\n") -> ""; before.endsWith("\n") -> "\n"; else -> "\n\n" }
        val tail = when { after.isEmpty() -> "\n"; after.startsWith("\n") -> "\n"; else -> "\n\n" }
        val nt = before + lead + TABLE_TEMPLATE + tail + after
        val firstCell = before.length + lead.length + TABLE_TEMPLATE.indexOf("|  |") + 2
        return EditResult(nt, firstCell, firstCell)
    }

    /**
     * 列表自动接续：在列表行末尾按回车时，新的一行自动带上同样的前缀（数字 +1，待办变成没勾的）；
     * 如果那一行只有前缀没有内容，再按回车就结束列表（去掉前缀，不多加空行）。
     * 不是“刚刚在某个列表行后面敲了一个回车”的情况返回 null，界面就按原样处理。
     */
    fun continueList(old: String, new: String, cursor: Int): EditResult? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length) return null
        val p = cursor - 1
        if (new[p] != '\n' || new.substring(0, p) != old.substring(0, p) || new.substring(cursor) != old.substring(p)) return null
        val ls = lineStart(new, p)
        val line = new.substring(ls, p)
        val m = anyListPrefix.find(line) ?: return null
        val content = line.substring(m.value.length)
        if (content.isBlank()) {
            // 空的列表行再回车：结束列表
            val nt = new.substring(0, ls) + new.substring(cursor)
            return EditResult(nt, ls, ls)
        }
        val indent = m.groupValues[1]
        val prefix = indent + when (typeOf(m.groupValues[2])) {
            ListType.TASK -> "- [ ] "
            ListType.NUMBER -> "${(Regex("\\d+").find(m.groupValues[2])?.value?.toIntOrNull() ?: 0) + 1}. "
            ListType.DOT -> "* "
            ListType.DASH -> "- "
        }
        val nt = new.substring(0, cursor) + prefix + new.substring(cursor)
        return EditResult(nt, cursor + prefix.length, cursor + prefix.length)
    }
}
