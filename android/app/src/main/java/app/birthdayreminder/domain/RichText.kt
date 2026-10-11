package app.birthdayreminder.domain

/**
 * 日记的“所见即所得”格式（不显示任何符号）。
 *
 * 存储：还是普通字符串，格式用 Unicode 私用区的字符做标记（用户看不见、也打不出来）：
 *  - 行首标记（一行最多一个）：大标题 / 小标题 / 1234 列表 / 圆点列表 / 横线列表 / 待办 / 已完成待办
 *  - 行内标记：划线开始 / 划线结束（成对出现在同一行里）
 * 显示：render() 把标记藏起来，换成要显示的前缀（“1. ”“• ”“– ”“☐ ”“☑ ”），并给出样式区间和
 * 原文 <-> 显示文字 的光标位置对照表（编辑框靠它把光标放对地方）。
 *
 * 纯逻辑，不依赖 Android / Compose，可以单独测试。
 */
object RichText {
    const val H1 = '\uE001'
    const val H2 = '\uE002'
    const val NUM = '\uE003'
    const val DOT = '\uE004'
    const val DASH = '\uE005'
    const val TODO = '\uE006'
    const val DONE = '\uE007'
    const val S_ON = '\uE010'
    const val S_OFF = '\uE011'

    val LINE_MARKS = setOf(H1, H2, NUM, DOT, DASH, TODO, DONE)
    val LIST_MARKS = setOf(NUM, DOT, DASH, TODO, DONE)
    private val ALL_MARKS = LINE_MARKS + setOf(S_ON, S_OFF)

    fun isMarker(c: Char) = c in ALL_MARKS
    fun hasMarks(s: String) = s.any { it in ALL_MARKS }

    // ---------------------------------------------------------------- 结构化表示
    /** 一行：行首格式（null = 正文）、看得见的文字、每个字是否划线 */
    data class Line(val mark: Char?, val text: String, val strike: List<Boolean>)

    fun parse(raw: String): List<Line> = raw.split("\n").map { l ->
        var mark: Char? = null
        var start = 0
        if (l.isNotEmpty() && l[0] in LINE_MARKS) { mark = l[0]; start = 1 }
        val sb = StringBuilder()
        val st = ArrayList<Boolean>()
        var on = false
        for (i in start until l.length) {
            val c = l[i]
            when {
                c == S_ON -> on = true
                c == S_OFF -> on = false
                c in LINE_MARKS -> {}          // 行中间的行首标记是坏数据，丢掉
                else -> { sb.append(c); st.add(on) }
            }
        }
        Line(mark, sb.toString(), st)
    }

    fun serialize(lines: List<Line>): String = lines.joinToString("\n") { ln ->
        val sb = StringBuilder()
        ln.mark?.let { sb.append(it) }
        var on = false
        for (i in ln.text.indices) {
            val s = ln.strike.getOrElse(i) { false }
            if (s && !on) { sb.append(S_ON); on = true }
            if (!s && on) { sb.append(S_OFF); on = false }
            sb.append(ln.text[i])
        }
        if (on) sb.append(S_OFF)
        sb.toString()
    }

    /** 整理：去掉放错位置的标记、空的划线、不成对的划线（编辑后调用） */
    fun normalize(raw: String): String = serialize(parse(raw))

    // ---------------------------------------------------------------- 光标位置换算
    /** 原文位置 -> (第几行, 这一行里前面有几个看得见的字) */
    fun rawToPos(raw: String, off: Int): Pair<Int, Int> {
        val o = off.coerceIn(0, raw.length)
        var line = 0; var col = 0
        for (i in 0 until o) {
            val c = raw[i]
            if (c == '\n') { line++; col = 0 } else if (!isMarker(c)) col++
        }
        return line to col
    }

    /** (行, 字) -> 原文位置：放在行首标记之后、隐藏的划线标记之前 */
    fun posToRaw(raw: String, line: Int, col: Int): Int {
        var ln = 0; var i = 0
        while (ln < line && i < raw.length) { if (raw[i] == '\n') ln++; i++ }
        if (i < raw.length && raw[i] in LINE_MARKS) i++
        var c = 0
        while (i < raw.length && raw[i] != '\n') {
            if (raw[i] == S_ON || raw[i] == S_OFF) { if (c == col) return i; i++; continue }
            if (raw[i] in LINE_MARKS) { i++; continue }
            if (c == col) return i
            c++; i++
        }
        return i
    }

    data class EditResult(val text: String, val selStart: Int, val selEnd: Int)

    /** 在保持“光标前后是哪些字”不变的前提下整理文字（编辑后用） */
    fun normalizeKeeping(raw: String, selStart: Int, selEnd: Int): EditResult {
        val n = normalize(raw)
        if (n == raw) return EditResult(raw, selStart, selEnd)
        val (l1, c1) = rawToPos(raw, selStart)
        val (l2, c2) = rawToPos(raw, selEnd)
        return EditResult(n, posToRaw(n, l1, c1), posToRaw(n, l2, c2))
    }

    // ---------------------------------------------------------------- 显示
    enum class Style { H1, H2, PREFIX, DONE, STRIKE }
    data class Span(val start: Int, val end: Int, val style: Style)

    class Rendered(
        val display: String,
        val spans: List<Span>,
        val origToTrans: IntArray,       // 长度 = 原文长度 + 1
        val transToOrig: IntArray,       // 长度 = 显示文字长度 + 1
        val checkboxes: List<Pair<IntRange, Int>>   // 显示文字里复选框的位置 -> 第几行（点了要勾选）
    )

    fun prefixOf(mark: Char?, number: Int): String = when (mark) {
        NUM -> "$number. "
        DOT -> "• "
        DASH -> "– "
        TODO -> "☐ "
        DONE -> "☑ "
        else -> ""
    }

    fun render(raw: String): Rendered {
        val n = raw.length
        val o2t = IntArray(n + 1)
        val disp = StringBuilder()
        val t2oList = ArrayList<Int>()          // 每个显示位置对应的原文位置（显示位置 = 下标）
        val spans = ArrayList<Span>()
        val boxes = ArrayList<Pair<IntRange, Int>>()
        var i = 0
        var lineIdx = 0
        var number = 0
        while (true) {
            // ---- 一行开始 ----
            val ls = i
            val mark = if (i < n && raw[i] in LINE_MARKS) raw[i] else null
            number = if (mark == NUM) number + 1 else 0
            val tLineStart = disp.length
            if (mark != null) {
                val prefix = prefixOf(mark, number)
                o2t[ls] = tLineStart
                for (k in prefix.indices) { t2oList.add(ls + 1); disp.append(prefix[k]) }
                if (prefix.isNotEmpty()) spans.add(Span(tLineStart, disp.length, Style.PREFIX))
                if (mark == TODO || mark == DONE) boxes.add(tLineStart until tLineStart + 2 to lineIdx)
                i++
            }
            val tContent = disp.length
            var strikeOn = false
            var strikeStart = -1
            var pending = -1      // 刚刚经过的隐藏标记里第一个的位置：显示位置统一对应到“隐藏标记之前”
            // 行首（显示位置 tLineStart 到 tContent）都对应到“行首标记之后”
            while (i < n && raw[i] != '\n') {
                val c = raw[i]
                if (isMarker(c)) {
                    o2t[i] = disp.length          // 隐藏字符：前后两个原文位置都对应同一个显示位置
                    if (pending < 0) pending = i
                    if (c == S_ON && !strikeOn) { strikeOn = true; strikeStart = disp.length }
                    else if (c == S_OFF && strikeOn) { strikeOn = false; if (disp.length > strikeStart) spans.add(Span(strikeStart, disp.length, Style.STRIKE)) }
                    i++
                    continue
                }
                o2t[i] = disp.length
                t2oList.add(if (pending >= 0) pending else i)
                pending = -1
                disp.append(c)
                i++
            }
            if (strikeOn && disp.length > strikeStart) spans.add(Span(strikeStart, disp.length, Style.STRIKE))
            when (mark) {
                H1 -> if (disp.length > tLineStart) spans.add(Span(tLineStart, disp.length, Style.H1))
                H2 -> if (disp.length > tLineStart) spans.add(Span(tLineStart, disp.length, Style.H2))
                DONE -> if (disp.length > tContent) spans.add(Span(tContent, disp.length, Style.DONE))
                else -> {}
            }
            if (i >= n) {
                o2t[n] = disp.length
                // 行尾位置：对应到原文里这一行最后一个可见字之后（隐藏的划线结束标记之前）
                t2oList.add(endOfVisible(raw, ls, n, mark != null))
                break
            }
            // 换行符：1 对 1
            o2t[i] = disp.length
            t2oList.add(endOfVisible(raw, ls, i, mark != null))
            disp.append('\n')
            i++
            lineIdx++
        }
        return Rendered(disp.toString(), spans, o2t, t2oList.toIntArray(), boxes)
    }

    /** 一行里最后一个可见字之后的原文位置（尾部的隐藏标记算在后面）；空行就是行首标记之后 */
    private fun endOfVisible(raw: String, ls: Int, end: Int, hasMark: Boolean): Int {
        var e = end
        val min = if (hasMark) ls + 1 else ls
        while (e > min && (raw[e - 1] == S_ON || raw[e - 1] == S_OFF)) e--
        return e
    }

    // ---------------------------------------------------------------- 编辑操作
    private fun lineIndexAt(raw: String, off: Int) = rawToPos(raw, off).first

    /**
     * 把选中的这几行（没选就是光标所在行）设成某种格式；都已经是这种格式就变回正文。
     * mark = null 表示设成正文。待办和已完成待办算同一种（点“待办”会把两种都取消）。
     */
    fun setLineMark(raw: String, selStart: Int, selEnd: Int, mark: Char?): EditResult {
        val lines = parse(raw).toMutableList()
        val a = lineIndexAt(raw, minOf(selStart, selEnd))
        val b = lineIndexAt(raw, maxOf(selStart, selEnd))
        fun same(m: Char?) = m == mark || (mark == TODO && m == DONE)
        val allSame = mark != null && (a..b).all { same(lines[it].mark) }
        val target = if (allSame) null else mark
        val (l1, c1) = rawToPos(raw, selStart)
        val (l2, c2) = rawToPos(raw, selEnd)
        for (k in a..b) lines[k] = lines[k].copy(mark = target)
        val out = serialize(lines)
        return EditResult(out, posToRaw(out, l1, c1), posToRaw(out, l2, c2))
    }

    /** 勾选 / 取消勾选第 lineIndex 行的待办 */
    fun toggleCheck(raw: String, lineIndex: Int): String {
        val lines = parse(raw).toMutableList()
        val l = lines.getOrNull(lineIndex) ?: return raw
        val m = when (l.mark) { TODO -> DONE; DONE -> TODO; else -> return raw }
        lines[lineIndex] = l.copy(mark = m)
        return serialize(lines)
    }

    /** 选中的文字加删除线；选中的已经全是划线的就取消。没选中文字不做任何事 */
    fun toggleStrike(raw: String, selStart: Int, selEnd: Int): EditResult {
        val s = minOf(selStart, selEnd); val e = maxOf(selStart, selEnd)
        val (l1, c1) = rawToPos(raw, s)
        val (l2, c2) = rawToPos(raw, e)
        if (l1 == l2 && c1 == c2) return EditResult(raw, selStart, selEnd)
        val lines = parse(raw).map { it.copy(strike = it.strike.toMutableList()) }.toMutableList()
        fun cols(li: Int): IntRange {
            val from = if (li == l1) c1 else 0
            val to = if (li == l2) c2 else lines[li].text.length
            return from until to
        }
        val all = (l1..l2).all { li -> cols(li).all { lines[li].strike.getOrElse(it) { false } } }
        for (li in l1..l2) {
            val st = lines[li].strike as MutableList<Boolean>
            for (c in cols(li)) if (c < st.size) st[c] = !all
        }
        val out = serialize(lines)
        return EditResult(out, posToRaw(out, l1, c1), posToRaw(out, l2, c2))
    }

    /**
     * 回车自动接续列表：在列表 / 待办行里按回车，新的一行带上同样的格式（已完成待办续成没勾的）；
     * 在只有格式没有文字的列表行按回车，就结束列表（去掉格式，不多加一行）。
     * 不是“刚刚敲了一个回车”的情况返回 null。
     */
    fun continueOnEnter(old: String, new: String, cursor: Int): EditResult? {
        if (new.length != old.length + 1 || cursor < 1 || cursor > new.length) return null
        val p = cursor - 1
        if (new[p] != '\n' || new.substring(0, p) != old.substring(0, p) || new.substring(cursor) != old.substring(p)) return null
        val lineStart = new.lastIndexOf('\n', p - 1) + 1
        val mark = new.getOrNull(lineStart)?.takeIf { it in LIST_MARKS } ?: return null
        val content = new.substring(lineStart + 1, p).filter { !isMarker(it) }
        if (content.isEmpty()) {
            // 空的列表行再回车：去掉格式和刚敲的回车
            val out = new.substring(0, lineStart) + new.substring(lineStart + 1, p) + new.substring(cursor)
            return EditResult(out, lineStart, lineStart)
        }
        val m = if (mark == DONE) TODO else mark
        val out = new.substring(0, cursor) + m + new.substring(cursor)
        return EditResult(out, cursor + 1, cursor + 1)
    }

    // ---------------------------------------------------------------- 转换
    /** 好读的纯文字（给 AI、搜索、列表摘要用） */
    fun toPlain(raw: String): String {
        val lines = parse(raw)
        var number = 0
        return lines.joinToString("\n") { l ->
            number = if (l.mark == NUM) number + 1 else 0
            prefixOf(l.mark, number) + l.text
        }
    }

    /** 转成 Markdown（备份文件里用，换手机恢复时还能还原格式） */
    fun toMarkdown(raw: String): String {
        val lines = parse(raw)
        var number = 0
        return lines.joinToString("\n") { l ->
            number = if (l.mark == NUM) number + 1 else 0
            val sb = StringBuilder()
            var on = false
            for (i in l.text.indices) {
                val s = l.strike.getOrElse(i) { false }
                if (s && !on) { sb.append("~~"); on = true }
                if (!s && on) { sb.append("~~"); on = false }
                sb.append(l.text[i])
            }
            if (on) sb.append("~~")
            val pre = when (l.mark) {
                H1 -> "# "; H2 -> "## "; NUM -> "$number. "; DOT -> "* "; DASH -> "- "; TODO -> "- [ ] "; DONE -> "- [x] "; else -> ""
            }
            pre + sb
        }
    }

    private val mdStrike = Regex("~~([^~\\n]+)~~")

    /**
     * 一行 Markdown（或上一版的纯文字“☐ / ☑ / • ”开头）-> 带格式的一行。
     * 加粗、斜体、下划线、代码这些现在不支持的样式：去掉符号只留文字。
     */
    fun lineFromMarkdown(line: String): String {
        val (mark, content) = when {
            line.startsWith("☐ ") -> TODO to line.substring(2)
            line.startsWith("☑ ") -> DONE to line.substring(2)
            line.startsWith("• ") -> DOT to line.substring(2)
            line.startsWith("– ") -> DASH to line.substring(2)
            else -> when (val k = BlockMarkdown.classify(line)) {
                is LineKind.Heading -> (if (k.level == 1) H1 else H2) to k.content
                is LineKind.Task -> (if (k.checked) DONE else TODO) to k.content
                is LineKind.Bullet -> (if (k.style == BulletStyle.DOT) DOT else DASH) to k.content
                is LineKind.Numbered -> NUM to k.content
                is LineKind.Quote -> null to k.content
                else -> null to line
            }
        }
        // 划线 ~~x~~ -> 隐藏标记；其它行内符号去掉
        var t = mdStrike.replace(content) { S_ON + it.groupValues[1] + S_OFF }
        t = MarkdownPlain.toPlain(t)
        return (mark?.toString() ?: "") + t
    }

    /** 整段 Markdown（或纯文字）-> 带格式的文字；已经带格式标记的原样返回（不重复转换） */
    fun fromMarkdown(text: String): String {
        if (hasMarks(text)) return text
        return normalize(text.split("\n").joinToString("\n") { lineFromMarkdown(it) })
    }
}
