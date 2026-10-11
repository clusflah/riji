package app.birthdayreminder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Re-arms the next alarm and refreshes the home-screen widget after reboot, app update, or a clock/time-zone change. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        AlarmStore.armNext(context);
        LiWidgetProvider.refreshAll(context);
        LiWidgetProvider.scheduleMidnight(context);
    }
}
