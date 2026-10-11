package app.birthdayreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 常用语：一个标题 + 一段内容（学号、邮箱、手机号这类经常要复制的信息），可以分类。对应旧版 S.data.phrases。 */
@Entity(tableName = "phrases")
data class Phrase(
    @PrimaryKey val id: String,
    val category: String = "",
    val title: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
    /** 私密：只在隐私空间里显示（日历、列表、小组件、AI 助手都看不到） */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false
)
