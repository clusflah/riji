package app.birthdayreminder.health

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.birthdayreminder.domain.HealthImport
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Health Connect 自动同步：
 *  - 打开 App 时同步最近 7 天（两次至少隔 15 分钟），只需要普通的读取授权；
 *  - 后台每 6 小时同步一次，需要 Health Connect 的“在后台读取”权限（较新的系统才有），没有就只在打开 App 时同步。
 * 都只填空着的项，不覆盖手动填的。
 */
object HealthAutoSync {
    private const val SP = "health_sync"
    private const val WORK = "health_auto_sync"
    /** Health Connect 的“在后台读取健康数据”权限 */
    const val BACKGROUND_PERMISSION = "android.permission.health.READ_HEALTH_DATA_IN_BACKGROUND"

    /** 状态变了就 +1，界面刷新用 */
    val version = mutableIntStateOf(0)

    private fun sp(ctx: Context) = ctx.getSharedPreferences(SP, Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = sp(ctx).getBoolean("on", false)
    fun lastAt(ctx: Context) = sp(ctx).getLong("at", 0L)

    fun status(ctx: Context): String {
        val s = sp(ctx)
        return HealthImport.statusText(s.getLong("at", 0L), s.getInt("filled", 0), s.getBoolean("bg", false),
            s.getString("err", null), System.currentTimeMillis(), java.time.ZoneId.systemDefault())
    }

    fun setEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("on", on).apply()
        schedule(ctx)
        version.intValue++
    }

    /** 按开关排 / 取消后台定时同步（每 6 小时；没有后台权限时任务会自己跳过） */
    fun schedule(ctx: Context) {
        val wm = try { WorkManager.getInstance(ctx.applicationContext) } catch (e: Exception) { return }
        if (enabled(ctx) && Build.VERSION.SDK_INT >= 26) {
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<HealthSyncWorker>(6, TimeUnit.HOURS).build())
        } else wm.cancelUniqueWork(WORK)
    }

    /** 打开 App 时调用：开着、隔了 15 分钟以上就同步一次 */
    fun onForeground(ctx: Context) {
        val app = ctx.applicationContext
        if (!HealthImport.shouldAutoSync(enabled(app), lastAt(app), System.currentTimeMillis())) return
        CoroutineScope(Dispatchers.IO).launch { try { runNow(app, background = false) } catch (e: Exception) { } }
    }

    /** 同步最近 7 天；返回提示文字。会记下这次的结果 */
    suspend fun runNow(ctx: Context, background: Boolean): String {
        val app = ctx.applicationContext
        fun done(filled: Int, err: String?): String {
            sp(app).edit().putLong("at", System.currentTimeMillis()).putInt("filled", filled).putBoolean("bg", background)
                .putString("err", err).apply()
            version.intValue++
            return err ?: if (filled > 0) "已同步，填入 $filled 项" else "没有新数据"
        }
        if (!Prefs.moduleOn(app, Modules.FITNESS)) return "健身数据功能已关闭"
        if (Build.VERSION.SDK_INT < 26 || HealthConnectSource.state(app) != HealthConnectSource.State.OK) return done(0, "Health Connect 不可用")
        val granted = HealthConnectSource.granted(app)
        if (granted.none { it in HealthConnectSource.PERMISSIONS }) return done(0, "没有授权读取健康数据")
        if (background && BACKGROUND_PERMISSION !in granted) return "没有后台读取权限"     // 后台没权限：不记，等打开 App 时再同步
        val today = LocalDate.now()
        val r = HealthConnectSource.import(app, today.minusDays(HealthImport.AUTO_DAYS - 1), today)
        return done(r.filled, null)
    }

    suspend fun backgroundGranted(ctx: Context): Boolean =
        Build.VERSION.SDK_INT >= 26 && BACKGROUND_PERMISSION in HealthConnectSource.granted(ctx)
}

/** 后台定时同步任务 */
class HealthSyncWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        if (!HealthAutoSync.enabled(applicationContext)) return Result.success()
        try { HealthAutoSync.runNow(applicationContext, background = true) } catch (e: Exception) { }
        return Result.success()
    }
}
