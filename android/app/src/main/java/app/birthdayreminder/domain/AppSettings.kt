package app.birthdayreminder.domain

/**
 * 通用设置（对应旧版“通知与外观”页）。默认值和旧版一致，
 * 只有一处不同：系统通知默认开着（原生版启动时会直接申请通知权限，不用先去设置里打开）。
 */
data class AppSettings(
    val notify: Boolean = true,
    val dailyTime: String = "09:00",
    val defAdvance: List<Int> = listOf(0, 1),
    val weekStart: Int = 0,           // 0 = 周日开头，1 = 周一开头
    val showLunar: Boolean = true,
    val showFestival: Boolean = true,  // 日历显示中国节日和二十四节气
    val theme: String = "auto",       // auto / light / dark
    val holidayAlert: Boolean = true,
    val autoHoliday: Boolean = true,
    val lastBackup: Long = 0L,
    // 自动备份：频率 off / daily / weekly，备份文件夹（系统给的文件夹授权地址），上次自动备份时间、上次失败原因
    val autoBackup: String = "off",
    val autoBackupDir: String = "",
    val lastAutoBackup: Long = 0L,
    val autoBackupError: String = ""
) {
    companion object {
        val ADVANCE_CHOICES = listOf(0, 1, 3, 7)
        private val HM = Regex("^\\d{2}:\\d{2}$")

        fun validTime(s: String?): Boolean =
            s != null && HM.matches(s) && try { java.time.LocalTime.parse(s); true } catch (e: Exception) { false }

        fun encodeAdvance(l: List<Int>): String = l.sorted().joinToString(",")

        fun decodeAdvance(s: String?): List<Int> =
            if (s == null) listOf(0, 1) else s.split(",").mapNotNull { it.trim().toIntOrNull() }.filter { it in 0..7 }.distinct().sorted()
    }
}
