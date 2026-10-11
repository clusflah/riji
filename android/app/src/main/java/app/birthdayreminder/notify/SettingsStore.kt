package app.birthdayreminder.notify

import android.content.Context
import app.birthdayreminder.domain.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 通用设置的存取。和 Prefs 用同一个文件，所以之前存过的“每日提醒时间”会原样沿用。
 * 界面用 flow 订阅，改了设置日历、主题这些会立刻跟着变。
 */
object SettingsStore {
    private const val FILE = "li_native_prefs"

    private val _flow = MutableStateFlow(AppSettings())
    val flow: StateFlow<AppSettings> = _flow

    @Volatile private var loaded = false

    private fun prefs(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private fun read(ctx: Context): AppSettings {
        val p = prefs(ctx)
        val d = AppSettings()
        return AppSettings(
            notify = p.getBoolean("notify", d.notify),
            dailyTime = p.getString("dailyTime", d.dailyTime)?.takeIf { AppSettings.validTime(it) } ?: d.dailyTime,
            defAdvance = AppSettings.decodeAdvance(p.getString("defAdvance", null)),
            weekStart = p.getInt("weekStart", d.weekStart).let { if (it == 1) 1 else 0 },
            showLunar = p.getBoolean("showLunar", d.showLunar),
            showFestival = p.getBoolean("showFestival", d.showFestival),
            theme = p.getString("theme", d.theme)?.takeIf { it in listOf("auto", "light", "dark") } ?: d.theme,
            holidayAlert = p.getBoolean("holidayAlert", d.holidayAlert),
            autoHoliday = p.getBoolean("autoHoliday", d.autoHoliday),
            lastBackup = p.getLong("lastBackup", 0L),
            autoBackup = p.getString("autoBackup", "off")?.takeIf { it in listOf("off", "daily", "weekly") } ?: "off",
            autoBackupDir = p.getString("autoBackupDir", "") ?: "",
            lastAutoBackup = p.getLong("lastAutoBackup", 0L),
            autoBackupError = p.getString("autoBackupError", "") ?: ""
        )
    }

    /** 第一次用之前先加载（Activity 启动时调一次；其它地方 get() 也会兜底） */
    fun load(ctx: Context) {
        if (loaded) return
        synchronized(this) { if (!loaded) { _flow.value = read(ctx); loaded = true } }
    }

    fun get(ctx: Context): AppSettings { load(ctx); return _flow.value }

    fun update(ctx: Context, change: (AppSettings) -> AppSettings) {
        load(ctx)
        val s = change(_flow.value)
        prefs(ctx).edit()
            .putBoolean("notify", s.notify)
            .putString("dailyTime", s.dailyTime)
            .putString("defAdvance", AppSettings.encodeAdvance(s.defAdvance))
            .putInt("weekStart", s.weekStart)
            .putBoolean("showLunar", s.showLunar)
            .putBoolean("showFestival", s.showFestival)
            .putString("theme", s.theme)
            .putBoolean("holidayAlert", s.holidayAlert)
            .putBoolean("autoHoliday", s.autoHoliday)
            .putLong("lastBackup", s.lastBackup)
            .putString("autoBackup", s.autoBackup)
            .putString("autoBackupDir", s.autoBackupDir)
            .putLong("lastAutoBackup", s.lastAutoBackup)
            .putString("autoBackupError", s.autoBackupError)
            .apply()
        _flow.value = s
    }
}
