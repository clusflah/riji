package app.birthdayreminder.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** AlarmManager 到点后触发，负责把这一条提醒弹成系统通知（提醒事项会带“完成 / 稍后提醒”按钮）。 */
class NotifyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        NotificationHelper.show(
            context,
            intent.getIntExtra("id", 0),
            intent.getStringExtra("title") ?: "提醒",
            intent.getStringExtra("body") ?: "",
            intent.getStringExtra("rid"),
            intent.getStringExtra("occ")
        )
    }
}
