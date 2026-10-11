package app.birthdayreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对应旧版 S.data.alarms 的一条闹钟。
 * repeat: "once" 仅一次 / "daily" 每天 / "workday" 工作日(跳过法定假期、含调休补班) /
 *         "restday" 休息日 / "custom" 每周自定义(days)
 * days: 0=周日 … 6=周六，只在 repeat=custom 时有意义
 * onceAt: repeat=once 时，下一次响铃的时间戳(毫秒)
 */
@Entity(tableName = "alarms")
data class Alarm(
    @PrimaryKey val id: String,
    val time: String = "07:00",
    val label: String = "",
    val repeat: String = "workday",
    val days: List<Int> = listOf(1, 2, 3, 4, 5),
    val snooze: Int = 10,
    val vibrate: Boolean = true,
    val soundPath: String? = null,
    val soundName: String? = null,
    val enabled: Boolean = true,
    val onceAt: Long? = null,
    val isPrivate: Boolean = false       // 私密：只在隐私空间里显示
)
