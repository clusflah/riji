package app.birthdayreminder;

import androidx.core.content.ContextCompat;
import android.app.KeyguardManager;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Full-screen alarm screen shown over the lock screen while an alarm is ringing. */
public class AlarmActivity extends Activity {
    private BroadcastReceiver stopReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!AlarmService.ringing) {
            finish();
            return;
        }
        // 锁屏上显示、点亮屏幕：Android 8.1 起用新接口；更老的系统用窗口标志
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) km.requestDismissKeyguard(this, null);
        } else {
            addLegacyLockScreenFlags();
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(buildUi());

        stopReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                finish();
            }
        };
        IntentFilter filter = new IntentFilter(AlarmService.ACTION_STOPPED);
        ContextCompat.registerReceiver(this, stopReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    /** Android 8.0 及以下没有 setShowWhenLocked 等接口，只能用（已过时的）窗口标志 */
    @SuppressWarnings("deprecation")
    private void addLegacyLockScreenFlags() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF1B1B21);
        root.setPadding(dp(28), dp(48), dp(28), dp(48));

        TextView time = new TextView(this);
        time.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        time.setTextColor(Color.WHITE);
        time.setTextSize(TypedValue.COMPLEX_UNIT_SP, 72);
        time.setGravity(Gravity.CENTER);
        root.addView(time, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView label = new TextView(this);
        label.setText(AlarmService.currentLabel);
        label.setTextColor(0xFFD9D9DF);
        label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        lp.bottomMargin = dp(64);
        root.addView(label, lp);

        int snooze = AlarmService.currentSnooze;
        if (snooze > 0) {
            root.addView(makeButton(getString(R.string.alarm_snooze_fmt, snooze), 0xFF34343D, Color.WHITE,
                    AlarmService.ACTION_SNOOZE), buttonParams());
        }
        root.addView(makeButton(getString(R.string.alarm_dismiss), 0xFFFF4D6D, Color.WHITE,
                AlarmService.ACTION_DISMISS), buttonParams());
        return root;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        p.topMargin = dp(14);
        return p;
    }

    private Button makeButton(String text, int bg, int fg, final String action) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(fg);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(29));
        b.setBackground(d);
        b.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startService(new Intent(AlarmActivity.this, AlarmService.class).setAction(action));
                finish();
            }
        });
        return b;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    @Override
    protected void onDestroy() {
        if (stopReceiver != null) {
            try { unregisterReceiver(stopReceiver); } catch (IllegalArgumentException ignored) { }
            stopReceiver = null;
        }
        super.onDestroy();
    }
}
