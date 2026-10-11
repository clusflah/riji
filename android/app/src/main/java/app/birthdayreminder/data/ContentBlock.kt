package app.birthdayreminder.data

import java.util.UUID

/**
 * 日记正文由一串有序的块组成：文本块 / 图片块 / 附件块，图片和附件可以插在任意两段文字之间。
 * 这是阶段3（日记模块）会用到的结构，这里先把数据形状定好，方便阶段1的 Room 表直接兼容。
 */
sealed class ContentBlock {
    abstract val id: String

    data class TextBlock(
        override val id: String = UUID.randomUUID().toString(),
        var text: String = ""
    ) : ContentBlock()

    data class ImageBlock(
        override val id: String = UUID.randomUUID().toString(),
        val imagePath: String
    ) : ContentBlock()

    /** 表格：rows[0] 是表头；每行的格子数一样多 */
    data class TableBlock(
        override val id: String = UUID.randomUUID().toString(),
        val rows: List<List<String>>
    ) : ContentBlock()

    /** 附件（PDF、文档、表格、压缩包等任意文件）：存在 App 私有目录 diary_files/ 里，name 是原来的文件名 */
    data class FileBlock(
        override val id: String = UUID.randomUUID().toString(),
        val path: String,
        val name: String
    ) : ContentBlock()
}

data class DiaryLocation(
    val lat: Double,
    val lng: Double,
    val address: String
)
