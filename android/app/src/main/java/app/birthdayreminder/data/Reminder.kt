package app.birthdayreminder.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对应 JS 版 S.data.reminders。
 * repeat: "none" | "daily" | "weekly" | "monthly" | "yearly" | "interval"
 * done: 日期字符串(yyyy-MM-dd) -> 是否已完成，用于重复提醒逐次打勾
 */
@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String,
    val title: String,
    val date: String,           // yyyy-MM-dd，首次/基准日期
    val time: String = "",      // HH:mm，空字符串表示全天
    val repeat: String = "none",
    val advance: Int = 0,       // 提前提醒的分钟数
    val note: String = "",
    val done: Map<String, Boolean> = emptyMap(),
    val intervalUnit: String? = null,  // repeat=interval 时: "day" | "month" | "year"
    val intervalN: Int? = null,
    /** 私密：只在隐私空间里显示（日历、列表、小组件、AI 助手都看不到） */
    @ColumnInfo(defaultValue = "0") val isPrivate: Boolean = false
)
