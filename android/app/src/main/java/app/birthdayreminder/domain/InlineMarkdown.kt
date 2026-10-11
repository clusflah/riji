package app.birthdayreminder.domain

enum class InlineKind { BOLD, ITALIC, UNDERLINE, CODE, LINK }
data class InlineSpan(val start: Int, val end: Int, val kind: InlineKind, val url: String? = null)
data class InlineResult(val text: String, val spans: List<InlineSpan>)

/**
 * 一行文字里的行内 Markdown（**加粗**、*斜体*、++下划线++、`代码`、[文字](链接)）解析成
 * “去掉符号后的纯文本 + 每段文字对应的样式区间”，方便渲染成 AnnotatedString。
 * 图片不在这里处理——原生版图片是独立的 ContentBlock.ImageBlock，不走 markdown 语法。
 */
object InlineMarkdown {
    private val token = Regex(
        "`([^`\\n]+)`" +
            "|\\*\\*([^*\\n]+)\\*\\*" +
            "|\\+\\+([^+\\n]+)\\+\\+" +
            "|\\*([^*\\n]+)\\*" +
            "|\\[([^\\]\\n]+)\\]\\((https?://[^)\\s]+)\\)"
    )

    fun parse(raw: String): InlineResult {
        val sb = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        var last = 0
        for (m in token.findAll(raw)) {
            sb.append(raw, last, m.range.first)
            val g = m.groupValues
            val start = sb.length
            when {
                g[1].isNotEmpty() -> { sb.append(g[1]); spans += InlineSpan(start, sb.length, InlineKind.CODE) }
                g[2].isNotEmpty() -> { sb.append(g[2]); spans += InlineSpan(start, sb.length, InlineKind.BOLD) }
                g[3].isNotEmpty() -> { sb.append(g[3]); spans += InlineSpan(start, sb.length, InlineKind.UNDERLINE) }
                g[4].isNotEmpty() -> { sb.append(g[4]); spans += InlineSpan(start, sb.length, InlineKind.ITALIC) }
                g[5].isNotEmpty() -> { sb.append(g[5]); spans += InlineSpan(start, sb.length, InlineKind.LINK, g[6]) }
            }
            last = m.range.last + 1
        }
        sb.append(raw, last, raw.length)
        return InlineResult(sb.toString(), spans)
    }
}
