package app.birthdayreminder.ui.health

import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.birthdayreminder.domain.FitnessConfig
import app.birthdayreminder.domain.FitnessFields
import app.birthdayreminder.notify.Prefs

/**
 * 健身项目设置的存取：读出来交给 FitnessFields.configure()，改了就存下来并通知界面刷新。
 * App 启动时调用 load()；恢复备份带了项目设置时调用 save()。
 */
object FitnessFieldStore {
    /** 设置变了就 +1，界面用它作为 remember 的键，自动刷新 */
    val version = mutableIntStateOf(0)

    fun load(ctx: Context) {
        FitnessFields.configure(FitnessConfig.fromJson(Prefs.fitnessFieldsJson(ctx)))
        version.intValue++
    }

    fun save(ctx: Context, list: List<FitnessFields.Field>) {
        val merged = FitnessConfig.merge(list)
        Prefs.setFitnessFieldsJson(ctx, FitnessConfig.toJson(merged))
        FitnessFields.configure(merged)
        version.intValue++
    }

    /** 自定义项目的键：c_ + 8 位随机小写字母数字 */
    fun newKey(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
        return "c_" + (1..8).map { chars.random() }.joinToString("")
    }
}
