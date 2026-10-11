package app.birthdayreminder;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shown once when the widget is dropped on the home screen: pick which of the five types to display. */
public class WidgetConfigActivity extends Activity {
    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);
        Intent intent = getIntent();
        Bundle extras = intent == null ? null : intent.getExtras();
        if (extras != null) {
            appWidgetId = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF1B1B21);
        root.setPadding(dp(24), dp(40), dp(24), dp(24));

        TextView title = new TextView(this);
        title.setText(getString(R.string.widget_config_title));
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        tp.bottomMargin = dp(20);
        root.addView(title, tp);

        // 可选内容包含所有项目；关掉的功能不显示（日历一直在）
        String[][] options = {
                {"calendar", "📅", getString(R.string.widget_title_calendar)},
                {"birthday", "🎂", getString(R.string.widget_title_birthday)},
                {"reminder", "🔔", getString(R.string.widget_title_reminder)},
                {"alarm", "⏰", getString(R.string.widget_title_alarm)},
                {"diary", "📝", getString(R.string.widget_title_diary)},
                {"fitness", "💪", getString(R.string.widget_title_fitness)},
                {"allergy", "🌿", getString(R.string.widget_title_allergy)},
                {"med", "💊", getString(R.string.widget_title_med)},
                {"cycle", "🩸", getString(R.string.widget_title_cycle)},
                {"phrase", "📋", getString(R.string.widget_title_phrase)}
        };
        for (String[] opt : options) {
            String key = opt[0];
            if (!"calendar".equals(key)) {
                if (!app.birthdayreminder.notify.Prefs.INSTANCE.moduleOn(this, key)) continue;
                if ("allergy".equals(key) && !Features.INSTANCE.getAllergy()) continue;
            }
            root.addView(makeRow(opt[0], opt[1], opt[2]), rowParams());
        }
        return root;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(58));
        p.bottomMargin = dp(12);
        return p;
    }

    private View makeRow(final String type, String icon, String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), 0, dp(18), 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF2A2A32);
        bg.setCornerRadius(dp(16));
        row.setBackground(bg);

        TextView ic = new TextView(this);
        ic.setText(icon);
        ic.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        LinearLayout.LayoutParams icp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        icp.rightMargin = dp(14);
        row.addView(ic, icp);

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        row.addView(tv, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finishWith(type);
            }
        });
        return row;
    }

    private void finishWith(String type) {
        getSharedPreferences(LiWidgetProvider.PREFS, MODE_PRIVATE).edit()
                .putString(LiWidgetProvider.KEY_TYPE_PREFIX + appWidgetId, type)
                .apply();
        AppWidgetManager mgr = AppWidgetManager.getInstance(this);
        LiWidgetProvider.updateOne(this, mgr, appWidgetId);
        Intent result = new Intent();
        result.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
