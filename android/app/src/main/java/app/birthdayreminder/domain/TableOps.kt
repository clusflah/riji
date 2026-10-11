package app.birthdayreminder.domain

import app.birthdayreminder.data.ContentBlock

/** 表格块的操作（纯逻辑）：改格子、加减行列、和 Markdown 表格互转 */
object TableOps {
    val EMPTY = listOf(listOf("", "", ""), listOf("", "", ""), listOf("", "", ""))
    const val MAX_COLS = 6
    const val MAX_ROWS = 50

    private fun cols(rows: List<List<String>>) = rows.maxOfOrNull { it.size } ?: 0

    /** 所有行补齐到一样多的格子 */
    fun pad(rows: List<List<String>>): List<List<String>> {
        val c = maxOf(1, cols(rows))
        return rows.map { r -> r + List(c - r.size) { "" } }
    }

    fun setCell(rows: List<List<String>>, r: Int, c: Int, text: String): List<List<String>> =
        rows.mapIndexed { ri, row -> if (ri != r) row else row.mapIndexed { ci, v -> if (ci == c) text.replace("\n", " ") else v } }

    fun addRow(rows: List<List<String>>) = if (rows.size >= MAX_ROWS) rows else rows + listOf(List(maxOf(1, cols(rows))) { "" })
    fun addCol(rows: List<List<String>>) = if (cols(rows) >= MAX_COLS) rows else rows.map { it + "" }
    /** 删掉最后一行（至少留表头和一行） */
    fun removeRow(rows: List<List<String>>) = if (rows.size <= 2) rows else rows.dropLast(1)
    /** 删掉最后一列（至少留一列） */
    fun removeCol(rows: List<List<String>>) = if (cols(rows) <= 1) rows else rows.map { it.dropLast(1) }

    /** 表格 -> Markdown（第一行是表头，下面加分隔行） */
    fun toMarkdown(rows: List<List<String>>): String {
        val p = pad(rows)
        fun line(r: List<String>) = "| " + r.joinToString(" | ") { it.replace("|", "／").ifEmpty { " " } } + " |"
        val sb = StringBuilder(line(p[0]))
        sb.append("\n|").append(" --- |".repeat(p[0].size))
        for (r in p.drop(1)) sb.append("\n").append(line(r))
        return sb.toString()
    }

    /**
     * 一段 Markdown 文字 -> 若干块：连续的表格行变成一个表格块（有分隔行的话第一行当表头），
     * 其它文字还原成带格式的文字块。
     */
    fun blocksFromMarkdown(text: String): List<ContentBlock> {
        val out = ArrayList<ContentBlock>()
        val buf = ArrayList<String>()
        fun flushText() {
            val t = buf.joinToString("\n").trim('\n')
            buf.clear()
            if (t.isNotBlank()) out.add(ContentBlock.TextBlock(text = RichText.fromMarkdown(t)))
        }
        for (b in BlockMarkdown.parse(text)) {
            when (b) {
                is MdBlock.Table -> {
                    flushText()
                    val rows = listOfNotNull(b.header) + b.rows
                    if (rows.isNotEmpty()) out.add(ContentBlock.TableBlock(rows = pad(if (rows.size == 1) rows + listOf(List(rows[0].size) { "" }) else rows)))
                }
                is MdBlock.Line -> buf.add(lineText(text, b.lineIndex))
            }
        }
        flushText()
        return out
    }

    private fun lineText(text: String, i: Int) = text.split("\n").getOrElse(i) { "" }
}
