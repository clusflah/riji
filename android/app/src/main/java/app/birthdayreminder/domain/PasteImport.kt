package app.birthdayreminder.domain

/**
 * 粘贴 / 键盘一次送进来一大段（AI 的回答最常见）：判断要不要转成日记自己的格式，以及转之前的整理。
 * 转换本身用和备份一样的 DiaryBackupText.toBlocks：标题、列表、待办、划线变成日记格式，
 * 表格变成表格块，独占一行的 ![](网址) 变成图片块。
 *
 * 纯逻辑，不依赖 Android。
 */
object PasteImport {
    private val mdImage = Regex("!\\[[^\\]\\n]*]\\(([^)\\s]+)[^)]*\\)")
    private val imageUrlLine = Regex("^\\s*(https?://\\S+?\\.(?:png|jpe?g|gif|webp)(?:[?#]\\S*)?)\\s*$", RegexOption.IGNORE_CASE)
    private val fence = Regex("^\\s*(```|~~~)")
    private val mdSignal = Regex("(?m)^\\s{0,3}(#{1,6}\\s|[-*+]\\s|\\d{1,3}[.)]\\s|>\\s|\\|.*\\||```)|\\*\\*[^*\\n]+\\*\\*")

    /** 一次多出来的这段文字值得转换：有图片，或者是多行的 Markdown；平常打字（一次一两个字）不会碰到 */
    fun worthConverting(inserted: String): Boolean {
        if (inserted.length < 4) return false
        if (mdImage.containsMatchIn(inserted)) return true
        if (!inserted.contains('\n')) return false
        return inserted.lines().any { imageUrlLine.matches(it) } || mdSignal.containsMatchIn(inserted)
    }

    /**
     * 转换前整理：行中间的 ![](…) 拆到单独一行（toBlocks 只认独占一行的图片），
     * 单独一行的图片网址（输入法助手“输入”时图片会变成网址）也当图片，代码块的 ``` 行去掉。
     */
    fun prepare(text: String): String {
        val out = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").split("\n")) {
            if (fence.containsMatchIn(raw)) continue
            val url = imageUrlLine.find(raw)
            if (url != null) { out.add("![](${url.groupValues[1]})"); continue }
            var rest = raw
            while (true) {
                val m = mdImage.find(rest) ?: break
                val before = rest.substring(0, m.range.first)
                if (before.isNotBlank()) out.add(before.trimEnd())
                out.add("![](${m.groupValues[1]})")
                rest = rest.substring(m.range.last + 1).trimStart()
            }
            if (rest.isNotEmpty() || raw.isEmpty()) out.add(rest)
        }
        return out.joinToString("\n")
    }
}
