package app.birthdayreminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** AlarmManager 到点后触发，负责把这一条提醒弹成系统通知（提醒事项会带“完成 / 稍后提醒”按钮）。 */
class NotifyReceiver : BroadcastReceiver() {
    companion object {
        /** 属于哪一类（生日/提醒、生理期），对应 Delivery 里各自的“答应过”名单 */
        const val EXTRA_GROUP = "group"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 补发的和原来那条都到了，只弹一次
                if (Delivery.claim(app, intent.getStringExtra(EXTRA_GROUP) ?: ReminderScheduler.GROUP, intent)) {
                    NotificationHelper.show(
                        app,
                        intent.getIntExtra("id", 0),
                        intent.getStringExtra("title") ?: "提醒",
                        intent.getStringExtra("body") ?: "",
                        intent.getStringExtra("rid"),
                        intent.getStringExtra("occ")
                    )
                }
                // 每弹一条就接着往后排：只排了最近几次的提醒（比如每天的只排 8 天），不打开 App 也能一直提醒下去
                if (intent.action == ReminderScheduler.ACTION) ReminderScheduler.reschedule(app)
            } catch (e: Exception) {
            } finally {
                pending.finish()
            }
        }
    }
}
