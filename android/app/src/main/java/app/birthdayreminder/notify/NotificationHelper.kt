package app.birthdayreminder.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import app.birthdayreminder.MainActivityCompose
import app.birthdayreminder.domain.ReminderActions
import androidx.core.content.ContextCompat

object NotificationHelper {
    // 特意用新的渠道ID，不和旧版(Capacitor)的 "reminders" 渠道混在一起
    const val CHANNEL_ID = "li_native_reminders"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val ch = NotificationChannel(CHANNEL_ID, "生日与提醒", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "生日、提醒事项提醒"
            enableVibration(true)
            // 有勿扰权限时每次重建都会按这里的值覆盖“可以打扰”，所以这里也要带上，不然会被改回去
            if (nm.isNotificationPolicyAccessGranted) setBypassDnd(true)
        }
        nm.createNotificationChannel(ch)
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, id: Int, title: String, body: String, reminderId: String? = null, occurrence: String? = null) {
        ensureChannel(ctx)
        if (!hasPermission(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivityCompose::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val b = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
        // 提醒事项：通知上直接“完成”或“10 分钟后再提醒”，不用打开 App
        if (reminderId != null) {
            fun action(act: String): PendingIntent {
                val i = Intent(ctx, ReminderActionReceiver::class.java).apply {
                    action = act
                    putExtra("id", id); putExtra("rid", reminderId); putExtra("occ", occurrence)
                    putExtra("title", title); putExtra("body", body)
                }
                // 每条通知、每个按钮各用一个请求码，互不覆盖
                val code = id * 4 + (if (act == ReminderActionReceiver.ACTION_DONE) 1 else 2)
                return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            }
            b.addAction(0, "完成", action(ReminderActionReceiver.ACTION_DONE))
            b.addAction(0, "${ReminderActions.SNOOZE_MINUTES} 分钟后提醒", action(ReminderActionReceiver.ACTION_SNOOZE))
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}
