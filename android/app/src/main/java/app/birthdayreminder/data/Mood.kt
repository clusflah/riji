package app.birthdayreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 一次快捷心情记录：一天可以记好几次。level 1~5（很差 → 很好），见 MoodLogic */
@Entity(tableName = "mood_logs")
data class MoodLog(
    @PrimaryKey val id: String,
    val date: String,          // yyyy-MM-dd
    val at: Long,              // 记录的时刻（毫秒）
    val level: Int,
    val note: String = ""
)
