package app.birthdayreminder.domain

/**
 * 日记不再用 Markdown：把以前写的（或者 AI 写的）带符号的文字转成好读的纯文字。
 *  # 标题 -> 标题        **加粗** / *斜体* / ++下划线++ / `代码` -> 只留文字
 *  - [ ] 待办 -> ☐ 待办   - [x] 完成 -> ☑ 完成      * 苹果 / - 香蕉 -> • 苹果 / • 香蕉
 *  1. 第一 -> 1. 第一（不变） > 引用 -> 引用           [文字](链接) -> 文字（链接）
 *  | 表格 | 行 | -> 表格  行（分隔行去掉）
 * 已经是纯文字的原样返回（反复转换结果不变）。
 */
object MarkdownPlain {
    fun toPlain(text: String): String {
        if (text.isEmpty()) return text
        val out = ArrayList<String>()
        for (line in text.split("\n")) {
            when (val k = BlockMarkdown.classify(line)) {
                is LineKind.Heading -> out.add(inline(k.content))
                is LineKind.Task -> out.add((if (k.checked) "☑ " else "☐ ") + inline(k.content))
                is LineKind.Bullet -> out.add("• " + inline(k.content))
                is LineKind.Numbered -> out.add("${k.number}. " + inline(k.content))
                is LineKind.Quote -> out.add(inline(k.content))
                is LineKind.TableSeparator -> {}
                is LineKind.TableRow -> out.add(k.cells.joinToString("  ") { inline(it) }.trim())
                is LineKind.Blank -> out.add("")
                is LineKind.Plain -> out.add(inline(line))
            }
        }
        return out.joinToString("\n")
    }

    private val link = Regex("\\[([^\\]\\n]+)]\\((https?://[^)\\s]+)\\)")
    private val bold = Regex("\\*\\*([^*\\n]+)\\*\\*")
    private val under = Regex("\\+\\+([^+\\n]+)\\+\\+")
    private val italic = Regex("(?<![*\\w])\\*([^*\\n]+)\\*(?![*\\w])")
    private val code = Regex("`([^`\\n]+)`")

    private fun inline(s: String): String {
        var t = link.replace(s) { "${it.groupValues[1]}（${it.groupValues[2]}）" }
        t = bold.replace(t, "$1")
        t = under.replace(t, "$1")
        t = code.replace(t, "$1")
        t = italic.replace(t, "$1")
        return t
    }

    /** 一篇日记里有没有需要转换的符号（没有就不动，避免无意义的保存） */
    fun needsConversion(text: String): Boolean = toPlain(text) != text
}
