package app.birthdayreminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 开机、升级、改系统时间/时区之后，AlarmManager 里的定时会丢，这里重新排一遍。 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                LegacyNotifications.cancelAll(context)     // 从网页版升级上来的：清掉旧版排过的定时通知
                ReminderScheduler.reschedule(context)
        MedScheduler.reschedule(context)      // 用药提醒也要重新排
        CycleScheduler.reschedule(context)    // 生理期提醒
            } finally {
                pending.finish()
            }
        }
    }
}
