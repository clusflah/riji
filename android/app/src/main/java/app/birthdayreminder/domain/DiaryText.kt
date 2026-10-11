package app.birthdayreminder.domain

import app.birthdayreminder.data.ContentBlock

/** 日记在列表里显示用的摘要、封面等，纯逻辑。 */
object DiaryText {
    // 行首：标题、引用、待办 [ ]/[x]、列表符号、数字编号
    private val linePrefix = Regex("(?m)^\\s*(#{1,6} |> ?|[-*•+] \\[[ xX]\\] ?|[-*•+] |\\d{1,3}(?:\\. |、 ?))")
    private val tableSep = Regex("(?m)^\\s*\\|?\\s*:?-{2,}.*$")
    private val inlineMarks = Regex("\\*\\*|\\+\\+|[*_`~]|\\[|\\]\\([^)]*\\)")

    fun snippet(blocks: List<ContentBlock>, max: Int = 40): String {
        val raw = blocks.mapNotNull { b ->
            when (b) {
                is ContentBlock.TextBlock -> RichText.toPlain(b.text)
                is ContentBlock.TableBlock -> b.rows.joinToString(" ") { r -> r.joinToString(" ") }
                else -> null
            }
        }.joinToString("\n")
        var s = tableSep.replace(raw, "")
        s = linePrefix.replace(s, "")
        s = s.replace("|", " ")
        s = inlineMarks.replace(s, "")
        s = s.replace(Regex("\\s+"), " ").trim()
        return if (s.length > max) s.take(max) + "…" else s
    }

    /** 只取文字块，块之间用换行连起来，保留原文（给 AI 读日记用） */
    /** 好读的纯文字：格式变成“1. ”“• ”“☐ ”这样的前缀，表格每行用空格隔开（给 AI、搜索用） */
    fun plainText(blocks: List<ContentBlock>): String =
        blocks.mapNotNull { b ->
            when (b) {
                is ContentBlock.TextBlock -> RichText.toPlain(b.text)
                is ContentBlock.TableBlock -> b.rows.joinToString("\n") { r -> r.joinToString("  ") }
                else -> null
            }
        }.joinToString("\n").trim()

    /** 多行文字压成一行、截断，列表里显示用（不去掉 Markdown 符号，常用语是原样内容） */
    fun oneLine(text: String, max: Int): String {
        val t = text.replace(Regex("\\s+"), " ").trim()
        return if (t.length > max) t.take(max) + "…" else t
    }

    fun imageCount(blocks: List<ContentBlock>): Int = blocks.count { it is ContentBlock.ImageBlock }

    fun fileCount(blocks: List<ContentBlock>): Int = blocks.count { it is ContentBlock.FileBlock }

    fun firstImage(blocks: List<ContentBlock>): String? =
        blocks.filterIsInstance<ContentBlock.ImageBlock>().firstOrNull()?.imagePath
}
