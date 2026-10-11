package app.birthdayreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 过敏 / 哮喘记录：一天一条（日期就是主键）。
 * 三个症状各记一个程度：0 无、1 轻、2 中、3 重。
 */
@Entity(tableName = "allergy_log")
data class AllergyLog(
    @PrimaryKey val date: String,        // yyyy-MM-dd
    val eye: Int = 0,                    // 眼睛过敏
    val nose: Int = 0,                   // 鼻子过敏
    val cough: Int = 0,                  // 咳嗽
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false       // 私密：只在隐私空间里显示
)

/**
 * 每日健身数据：一天一条（日期就是主键）。
 * 各项数值放在 metrics 里（键见 FitnessFields，比如 weight=体重kg、pullup=引体向上次数），
 * 没填的项就不存，以后想加新的项目也不用改数据库。
 */
@Entity(tableName = "fitness_log")
data class FitnessLog(
    @PrimaryKey val date: String,        // yyyy-MM-dd
    val metrics: Map<String, Double> = emptyMap(),
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false       // 私密：只在隐私空间里显示
)
