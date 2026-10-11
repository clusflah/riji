package app.birthdayreminder.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 药品。服药计划（几点吃、吃多少、怎么重复）存成 JSON 放在 plan 里（格式见 MedLogic.MedPlan）。
 * 功能设计参考开源项目 MedTimer（https://github.com/Futsch1/medTimer，MIT 许可证）。
 */
@Entity(tableName = "medications")
data class Medication(
    @PrimaryKey val id: String,
    val name: String,
    val dosage: String = "",          // 每次剂量说明，比如“1 片”“5 ml”
    val note: String = "",
    val plan: String = "",            // 服药计划 JSON
    val stock: Double? = null,        // 库存数量（不记库存就为空）
    val unit: String = "片",          // 库存 / 用量的单位
    val lowStock: Double? = null,     // 低于多少提醒
    val expiry: String? = null,       // 有效期 yyyy-MM-dd
    val active: Boolean = true,       // 停用后不再提醒
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false       // 私密：只在隐私空间里显示
)

/**
 * 一次服药记录。按计划的那次：id = 药品id|日期|时间（同一次只有一条）；临时加的：随机 id。
 * status：taken 已服用 / skipped 跳过
 */
@Entity(tableName = "med_logs")
data class MedLog(
    @PrimaryKey val id: String,
    val medId: String,
    val medName: String,
    val date: String,                 // yyyy-MM-dd（这一次属于哪天）
    val time: String,                 // HH:MM（计划时间；临时加的是记录时的时间）
    val amount: Double,
    val status: String,
    val actionAt: Long,               // 点“已服用 / 跳过”的时间
    val scheduled: Boolean = true     // false = 临时加的一次
)

/**
 * 生病就医记录：一次看病。medIds = 这次开的药（药品 id 的 JSON 列表），images = 病历 / 检查单照片（相对路径的 JSON 列表）。
 * followUp = 复诊日期，到时候提醒。
 */
@Entity(tableName = "medical_records")
data class MedicalRecord(
    @PrimaryKey val id: String,
    val date: String,                 // 就诊日期 yyyy-MM-dd
    val illness: String,              // 病情 / 诊断
    val symptoms: String = "",
    val hospital: String = "",
    val department: String = "",
    val doctor: String = "",
    val advice: String = "",          // 医嘱 / 处理
    val followUp: String? = null,     // 复诊日期
    val medIds: String = "",
    val images: String = "",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val isPrivate: Boolean = false       // 私密：只在隐私空间里显示
)

/**
 * 生理期的每日记录（一天一条）。flow：0 无、1 点滴、2 少、3 中、4 多（2 及以上算经期）。
 * symptoms = 症状键的 JSON 列表。功能设计参考开源项目 Mooneva Cycle（只参考功能，没有复制代码）。
 * profile = 这条记录属于哪一份数据：外面的是 ""，隐私空间里的是 CycleProfiles.PRIVATE，两份完全独立（同一天可以各有一条）。
 */
@Entity(tableName = "cycle_days", primaryKeys = ["profile", "date"])
data class CycleDay(
    val date: String,
    val flow: Int = 0,
    val symptoms: String = "",
    val mood: String = "",
    val discharge: String = "",
    val note: String = "",
    val updatedAt: Long = System.currentTimeMillis(),
    val profile: String = CycleProfiles.MAIN
)

/** 生理期数据分几份：外面一份，隐私空间一份；以后要分角色，再加别的值就行 */
object CycleProfiles {
    const val MAIN = ""
    const val PRIVATE = "private"
}
