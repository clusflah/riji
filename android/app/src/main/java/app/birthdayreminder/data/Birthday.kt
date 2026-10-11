package app.birthdayreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对应 JS 版 S.data.birthdays 里的一条记录，字段名和含义保持一致：
 * type: "solar" 阳历 / "lunar" 农历；leap 仅在 type=lunar 时有意义（是否闰月）；
 * advance: 提前几天提醒的列表，如 [0,1] 表示当天和提前1天；note: 备注。
 */
@Entity(tableName = "birthdays")
data class Birthday(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,           // "solar" | "lunar"
    val year: Int? = null,      // 出生年份，可为空（不知道/不显示年龄）
    val month: Int,
    val day: Int,
    val leap: Boolean = false,
    val advance: List<Int> = listOf(0, 1),
    val note: String = "",
    /** 私密：只在隐私空间里显示（日历、列表、小组件、AI 助手都看不到） */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false
)
