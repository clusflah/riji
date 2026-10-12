package app.birthdayreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 学习记录：一条记录里可以有好几项内容（items = JSON 列表，每项：名称、数量、单位，见 StudyLogic），
 * 再加上学习时长（分钟）、开始时间（HH:mm，可以不填）、备注。一天可以记好几条。
 */
@Entity(tableName = "study_logs")
data class StudyLog(
    @PrimaryKey val id: String,
    val date: String,                 // yyyy-MM-dd
    val items: String = "[]",
    val minutes: Int = 0,
    val time: String = "",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis()
)

/** 学习的常用项目：记录时点一下就加一行（带默认单位和数量） */
@Entity(tableName = "study_presets")
data class StudyPreset(
    @PrimaryKey val id: String,
    val name: String,
    val unit: String = "",
    val amount: Double? = null,
    val sort: Int = 0,
    val updatedAt: Long = System.currentTimeMillis()
)
