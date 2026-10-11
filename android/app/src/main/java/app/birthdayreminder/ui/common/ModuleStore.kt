package app.birthdayreminder.ui.common

import app.birthdayreminder.notify.CycleScheduler
import android.content.Context
import androidx.compose.runtime.mutableIntStateOf
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.Features
import app.birthdayreminder.WidgetUpdater
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.notify.MedScheduler
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.notify.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 功能总开关的读写。读：任何地方（包括后台的提醒、闹钟）都直接从设置里读，保证准确；
 * 改：保存后重新排提醒、闹钟、用药提醒，刷新小组件，并通知界面刷新。
 */
object ModuleStore {
    /** 开关变了就 +1，界面用它作为 remember 的键 */
    val version = mutableIntStateOf(0)

    fun disabled(ctx: Context): Set<String> = Prefs.disabledModules(ctx)

    fun isOn(ctx: Context, key: String): Boolean = Modules.isOn(Prefs.disabledModules(ctx), key, Features.allergy)

    fun set(ctx: Context, key: String, on: Boolean) {
        val app = ctx.applicationContext
        val now = Prefs.disabledModules(app)
        Prefs.setDisabledModules(app, if (on) now - key else now + key)
        version.intValue++
        // 提醒、闹钟、用药提醒、小组件都按新的开关重新来一遍
        CoroutineScope(Dispatchers.IO).launch {
            try { ReminderScheduler.reschedule(app) } catch (e: Exception) { }
            try { AlarmEngine.sync(app) } catch (e: Exception) { }
            try { MedScheduler.rescheduleNow(app) } catch (e: Exception) { }
            try { CycleScheduler.rescheduleNow(app) } catch (e: Exception) { }
            try { WidgetUpdater.sync(app) } catch (e: Exception) { }
        }
    }
}
