package app.birthdayreminder.domain

/** 列表的样式：小圆点 •、小横线 – */
enum class BulletStyle { DOT, DASH }

sealed class LineKind {
    data class Heading(val level: Int, val content: String) : LineKind()
    /** 可以勾选的待办：- [ ] 内容 / - [x] 内容 */
    data class Task(val checked: Boolean, val content: String) : LineKind()
    /** 无序列表：* 内容（小圆点）/ - 内容（小横线） */
    data class Bullet(val style: BulletStyle, val content: String) : LineKind()
    /** 有序列表：1. 内容 */
    data class Numbered(val number: Int, val content: String) : LineKind()
    data class Quote(val content: String) : LineKind()
    /** 表格的一行：| 单元格 | 单元格 | */
    data class TableRow(val cells: List<String>) : LineKind()
    /** 表格表头下面的分隔行：| --- | --- | */
    data object TableSeparator : LineKind()
    data class Plain(val content: String) : LineKind()
    data object Blank : LineKind()
}

/** 渲染用的段落：普通的一行（带它在原文里是第几行，勾待办时要用），或者合并好的一整张表格 */
sealed class MdBlock {
    data class Line(val kind: LineKind, val lineIndex: Int) : MdBlock()
    data class Table(val header: List<String>?, val rows: List<List<String>>) : MdBlock()
}

/**
 * 日记正文的行格式识别（纯逻辑）：
 *  # 标题  ## 小标题  ### 更小的标题
 *  - [ ] 待办 / - [x] 已完成   1. 有序列表   * 小圆点列表   - 小横线列表   > 引用
 *  | 表格 | 的一行 |
 */
object BlockMarkdown {
    private val heading = Regex("^(#{1,6}) (.*)$")
    private val task = Regex("^\\s*[-*•] \\[([ xX])\\] ?(.*)$")
    private val dot = Regex("^\\s*[*•+] (.*)$")
    private val dash = Regex("^\\s*- (.*)$")
    private val numbered = Regex("^\\s*(\\d{1,3})(?:\\. |、 ?)(.*)$")      // 1. 内容 / 1、内容
    private val quote = Regex("^>[ ]?(.*)$")
    private val tableSep = Regex("^\\s*\\|?\\s*:?-{2,}:?\\s*(\\|\\s*:?-{2,}:?\\s*)*\\|?\\s*$")

    fun classify(line: String): LineKind {
        if (line.isBlank()) return LineKind.Blank
        heading.find(line)?.let { return LineKind.Heading(it.groupValues[1].length, it.groupValues[2]) }
        task.find(line)?.let { return LineKind.Task(it.groupValues[1].lowercase() == "x", it.groupValues[2]) }
        dot.find(line)?.let { return LineKind.Bullet(BulletStyle.DOT, it.groupValues[1]) }
        dash.find(line)?.let { return LineKind.Bullet(BulletStyle.DASH, it.groupValues[1]) }
        numbered.find(line)?.let { return LineKind.Numbered(it.groupValues[1].toInt(), it.groupValues[2]) }
        quote.find(line)?.let { return LineKind.Quote(it.groupValues[1]) }
        val t = line.trim()
        if (t.startsWith("|") && t.indexOf('|', 1) >= 0) {   // 至少两根竖线才算表格行
            if (tableSep.matches(t)) return LineKind.TableSeparator
            return LineKind.TableRow(splitCells(t))
        }
        return LineKind.Plain(line)
    }

    /** | a | b | -> [a, b]（首尾的竖线去掉，单元格去空白） */
    fun splitCells(row: String): List<String> {
        var t = row.trim()
        if (t.startsWith("|")) t = t.substring(1)
        if (t.endsWith("|")) t = t.substring(0, t.length - 1)
        return t.split("|").map { it.trim() }
    }

    fun classifyAll(text: String): List<LineKind> = text.split("\n").map { classify(it) }

    /**
     * 整段解析成渲染用的段落：连续的表格行合成一张表（有分隔行的话第一行是表头），
     * 其它每行一个 Line（记下原文行号）。
     */
    fun parse(text: String): List<MdBlock> {
        val kinds = classifyAll(text)
        val out = ArrayList<MdBlock>()
        var i = 0
        while (i < kinds.size) {
            val k = kinds[i]
            if (k is LineKind.TableRow || k is LineKind.TableSeparator) {
                val rows = ArrayList<List<String>>()
                var header: List<String>? = null
                var sawSep = false
                while (i < kinds.size && (kinds[i] is LineKind.TableRow || kinds[i] is LineKind.TableSeparator)) {
                    val r = kinds[i]
                    if (r is LineKind.TableSeparator) {
                        if (!sawSep && rows.size == 1 && header == null) { header = rows.removeAt(0) }
                        sawSep = true
                    } else if (r is LineKind.TableRow) rows.add(r.cells)
                    i++
                }
                // 列数对齐：短的行补空单元格
                val cols = (listOfNotNull(header) + rows).maxOfOrNull { it.size } ?: 0
                fun pad(r: List<String>) = r + List(cols - r.size) { "" }
                out += MdBlock.Table(header?.let { pad(it) }, rows.map { pad(it) })
                continue
            }
            out += MdBlock.Line(k, i)
            i++
        }
        return out
    }
}
