package app.birthdayreminder.domain

import app.birthdayreminder.data.ContentBlock

/**
 * 日记正文“块列表”的纯逻辑操作（不碰任何 Android / Compose 类，方便单独测试）。
 * 正文 = 有序的 文本块 / 图片块 / 附件块，图片和附件可以插在任意两段文字之间。
 */
object BlockOps {
    data class Result(val blocks: List<ContentBlock>, val focusId: String?)

    /** 保证列表不为空，且最后一块是文本块（这样图片下面总能继续打字） */
    fun ensureTrailingText(blocks: List<ContentBlock>): List<ContentBlock> {
        if (blocks.isEmpty()) return listOf(ContentBlock.TextBlock(text = ""))
        return if (blocks.last() !is ContentBlock.TextBlock) blocks + ContentBlock.TextBlock(text = "") else blocks
    }

    /**
     * 在光标处插入图片：把当前文本块从光标处拆成 前文 / 图片 / 后文 三块。
     * focusedId 找不到（比如还没点过输入框）就插到末尾。
     * 返回新列表，以及之后应该聚焦的文本块 id（下一张图片会接着插在它前面，顺序就不会乱）。
     */
    fun insertImage(blocks: List<ContentBlock>, focusedId: String?, cursor: Int, path: String): Result =
        insertBlock(blocks, focusedId, cursor, ContentBlock.ImageBlock(imagePath = path))

    /** 在光标处插入附件，规则和插图片一样 */
    fun insertFile(blocks: List<ContentBlock>, focusedId: String?, cursor: Int, path: String, name: String): Result =
        insertBlock(blocks, focusedId, cursor, ContentBlock.FileBlock(path = path, name = name))

    /** 在光标处插入一张表格（3 列：表头 + 2 行空格子） */
    fun insertTable(blocks: List<ContentBlock>, focusedId: String?, cursor: Int): Result =
        insertBlock(blocks, focusedId, cursor, ContentBlock.TableBlock(rows = TableOps.EMPTY))

    private fun insertBlock(blocks: List<ContentBlock>, focusedId: String?, cursor: Int, image: ContentBlock): Result {
        val idx = blocks.indexOfFirst { it.id == focusedId && it is ContentBlock.TextBlock }

        if (idx < 0) {
            val last = blocks.lastOrNull()
            if (last is ContentBlock.TextBlock && last.text.isBlank()) {
                val list = blocks.toMutableList()
                list.add(list.size - 1, image)
                return Result(list, last.id)
            }
            val tail = ContentBlock.TextBlock(text = "")
            return Result(blocks + image + tail, tail.id)
        }

        val target = blocks[idx] as ContentBlock.TextBlock
        val pos = cursor.coerceIn(0, target.text.length)
        val before = target.copy(text = target.text.substring(0, pos).trimEnd('\n'))
        val after = ContentBlock.TextBlock(text = target.text.substring(pos).trimStart('\n'))
        val list = blocks.toMutableList()
        list.removeAt(idx)
        list.addAll(idx, listOf(before, image, after))
        return Result(list, after.id)
    }

    /** 删除一张图片或一个附件；它前后如果都是文本块，就合并成一块，避免留下一堆空块 */
    fun removeImage(blocks: List<ContentBlock>, imageId: String): List<ContentBlock> {
        val idx = blocks.indexOfFirst { it.id == imageId && it !is ContentBlock.TextBlock }
        if (idx < 0) return blocks
        val list = blocks.toMutableList()
        list.removeAt(idx)
        val prev = list.getOrNull(idx - 1)
        val next = list.getOrNull(idx)
        if (prev is ContentBlock.TextBlock && next is ContentBlock.TextBlock) {
            val merged = when {
                prev.text.isBlank() -> next.text
                next.text.isBlank() -> prev.text
                else -> prev.text + "\n" + next.text
            }
            list[idx - 1] = prev.copy(text = merged)
            list.removeAt(idx)
        }
        return ensureTrailingText(list)
    }

    /** 保存前去掉首尾多余的空文本块（至少保留一块） */
    fun trimForSave(blocks: List<ContentBlock>): List<ContentBlock> {
        val list = blocks.toMutableList()
        while (list.size > 1 && list.first().let { it is ContentBlock.TextBlock && it.text.isBlank() }) list.removeAt(0)
        while (list.size > 1 && list.last().let { it is ContentBlock.TextBlock && it.text.isBlank() }) list.removeAt(list.size - 1)
        return list
    }

    fun imagePaths(blocks: List<ContentBlock>): List<String> =
        blocks.filterIsInstance<ContentBlock.ImageBlock>().map { it.imagePath }

    fun filePaths(blocks: List<ContentBlock>): List<String> =
        blocks.filterIsInstance<ContentBlock.FileBlock>().map { it.path }

    /** 没有任何文字、图片、附件 */
    fun isEffectivelyEmpty(blocks: List<ContentBlock>): Boolean =
        blocks.none { it !is ContentBlock.TextBlock || it.text.isNotBlank() }
}
