package app.birthdayreminder;

import androidx.core.content.ContextCompat;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Home-screen widget: one provider, five configurable types (calendar / birthday / reminder / alarm / diary).
 *  Calendar renders as a mini month grid (2x2 / resizable default) or, on the wide 4x2 size, as a
 *  macOS-style "today + upcoming agenda" card. The four single-category widgets (birthday / reminder /
 *  alarm / diary) share a list layout with a colour-coded chip per item. */
public class LiWidgetProvider extends AppWidgetProvider {
    static final String PREFS = "li_widget";
    static final String KEY_DATA = "data";
    static final String KEY_TYPE_PREFIX = "type_";
    static final String ACTION_REFRESH = "app.birthdayreminder.WIDGET_REFRESH";
    static final String EXTRA_OPEN = "li_open";
    private static final int REQ_MIDNIGHT = 9001;

    /** Chip kinds, matching the wc_chip_<kind> / wc_dot_<kind> drawables and wc_chip_<kind>_fg colours. */
    private static final String[] KINDS = {"holiday", "birthday", "reminder", "alarm", "diary", "work"};

    @Override
    public void onUpdate(Context context, AppWidgetManager mgr, int[] ids) {
        for (int id : ids) updateOne(context, mgr, id);
        scheduleMidnight(context);
    }

    /** 用户拖动改变小组件大小后重画一次：今天的圆要按新的格子大小重新选直径 */
    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager mgr, int id, android.os.Bundle newOptions) {
        super.onAppWidgetOptionsChanged(context, mgr, id, newOptions);
        updateOne(context, mgr, id);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent != null && ACTION_REFRESH.equals(intent.getAction())) {
            // 午夜刷新：如果小组件数据由原生 Kotlin 版负责，先用最新数据重算一遍（需要读数据库，放后台线程）
            final PendingResult pending = goAsync();
            final Context app = context.getApplicationContext();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        WidgetUpdater.syncBlockingIfOwner(app);
                        refreshAll(app);
                        scheduleMidnight(app);
                    } finally {
                        pending.finish();
                    }
                }
            }).start();
        }
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        SharedPreferences.Editor e = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        for (int id : ids) e.remove(KEY_TYPE_PREFIX + id);
        e.apply();
    }

    private static final Class<?>[] PROVIDER_CLASSES = {
            LiWidgetProvider.class, LiWidgetProvider2x2.class, LiWidgetProvider4x2.class
    };

    static void refreshAll(Context context) {
        AppWidgetManager mgr = AppWidgetManager.getInstance(context);
        for (Class<?> cls : PROVIDER_CLASSES) {
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, cls));
            for (int id : ids) updateOne(context, mgr, id);
        }
    }

    /** "2x2" (square compact), "4x2" (wide compact) or "flex" (the resizable default, full month grid). */
    private static String sizeOf(Context context, int id) {
        try {
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(context).getAppWidgetInfo(id);
            if (info != null && info.provider != null) {
                String cls = info.provider.getClassName();
                if (cls.endsWith("LiWidgetProvider2x2")) return "2x2";
                if (cls.endsWith("LiWidgetProvider4x2")) return "4x2";
            }
        } catch (Exception ignored) {
            // fall through to the flexible default
        }
        return "flex";
    }

    static void updateOne(Context context, AppWidgetManager mgr, int id) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String type = prefs.getString(KEY_TYPE_PREFIX + id, null);
        JSONObject data = readData(context);
        String size = sizeOf(context, id);
        RemoteViews views;
        if (type == null) {
            views = new RemoteViews(context.getPackageName(), R.layout.widget_info);
            views.setTextViewText(R.id.w_icon, "📌");
            views.setTextViewText(R.id.w_title, context.getString(R.string.app_name));
            views.setTextViewText(R.id.w_line1, context.getString(R.string.widget_not_configured));
            views.setTextViewText(R.id.w_line2, "");
        } else if ("calendar".equals(type)) {
            // 按小组件现在的实际大小选样式：又宽又矮（比如 4×2）显示“今天 + 接下来几件事”，其它（2×2、4×3 …）显示月历格子
            int[] wh = widgetSizeDp(context, mgr, id);
            boolean wideAndShort = wh[0] >= 200 && wh[1] < 150;
            views = wideAndShort ? buildAgendaViews(context, data) : buildCalendarGridViews(context, data, todayDiameterDp(context, mgr, id));
        } else if ("reminder".equals(type)) {
            views = buildTodoViews(context, data, widgetSizeDp(context, mgr, id)[1], id);
        } else {
            views = buildListViews(context, type, data, widgetSizeDp(context, mgr, id)[1]);
        }
        Intent open = new Intent(context, MainActivityCompose.class)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN, type == null ? "" : type);
        PendingIntent pi = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pi);
        mgr.updateAppWidget(id, views);
    }

    private static JSONObject readData(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        try {
            return new JSONObject(prefs.getString(KEY_DATA, "{}"));
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    private static void zero(Calendar c) {
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
    }

    private static String richDayLabelFuture(Context c, long targetMs) {
        Calendar a = Calendar.getInstance();
        zero(a);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(targetMs);
        zero(b);
        long days = (b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L;
        if (days <= 0) return c.getString(R.string.widget_today);
        if (days == 1) return c.getString(R.string.widget_tomorrow);
        if (days == 2) return c.getString(R.string.widget_day_after_tomorrow);
        return new SimpleDateFormat("M\u6708d\u65e5 EEEE", Locale.CHINA).format(new Date(targetMs));
    }

    private static String richDayLabelPast(Context c, long targetMs) {
        Calendar a = Calendar.getInstance();
        zero(a);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(targetMs);
        zero(b);
        long days = (a.getTimeInMillis() - b.getTimeInMillis()) / 86400000L;
        if (days <= 0) return c.getString(R.string.widget_today);
        if (days == 1) return c.getString(R.string.widget_yesterday);
        if (days == 2) return c.getString(R.string.widget_day_before_yesterday);
        return new SimpleDateFormat("M\u6708d\u65e5", Locale.CHINA).format(new Date(targetMs));
    }

    /** Colours the dot + chip background for `kind`, and shows only the text line matching that kind. */
    private static final int MAX_ROWS = 6;

    /** 新项目没有专门的颜色样式，借用最接近的：健身蓝、过敏橙、用药灰蓝、生理期粉橙、常用语紫 */
    private static String chipKind(String type) {
        switch (type) {
            case "fitness": return "alarm";
            case "allergy": return "holiday";
            case "med": return "work";
            case "cycle": return "birthday";
            case "phrase": return "diary";
            default: return type;
        }
    }

    private static void setChip(Context c, RemoteViews v, String prefix, String kind, String text) {
        int chipId = idOf(c, prefix + "_chip");
        int dotId = idOf(c, prefix + "_dot");
        if (chipId != 0) v.setInt(chipId, "setBackgroundResource", drawableId(c, "wc_chip_" + kind));
        if (dotId != 0) v.setInt(dotId, "setBackgroundResource", drawableId(c, "wc_dot_" + kind));
        for (int i = 0; i < KINDS.length; i++) {
            int textId = idOf(c, prefix + "_t" + i);
            if (textId == 0) continue;
            boolean match = KINDS[i].equals(kind);
            v.setViewVisibility(textId, match ? View.VISIBLE : View.GONE);
            if (match) v.setTextViewText(textId, text);
        }
    }

    private static int idOf(Context c, String name) {
        return c.getResources().getIdentifier(name, "id", c.getPackageName());
    }

    private static int drawableId(Context c, String name) {
        return c.getResources().getIdentifier(name, "drawable", c.getPackageName());
    }

    /**
     * 提醒小组件：不带图标的标题 + 列表。先是没做完的无时间待办（空心圆圈），
     * 再是接下来有日期的提醒（实心圆点 + 日期时间）。按小组件高度决定显示几行，放不下的写“还有 N 项”。
     */
    private static RemoteViews buildTodoViews(Context context, JSONObject data, int heightDp, int appWidgetId) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_todo);
        v.setTextViewText(R.id.td_title, context.getString(R.string.widget_title_reminder));
        JSONArray list = data.optJSONArray("todoList");
        int total = list == null ? 0 : list.length();
        int maxRows = 12;
        int rows = heightDp <= 0 ? 4 : Math.max(1, Math.min(maxRows, (heightDp - 24 - 17 - 17) / 23));
        int shown = Math.min(total, rows);
        if (total > rows && rows > 1) shown = rows - 1;     // 留一行写“还有 N 项”
        for (int i = 0; i < maxRows; i++) {
            int row = idOf(context, "td_" + i);
            JSONObject it = i < shown ? list.optJSONObject(i) : null;
            if (it == null) { v.setViewVisibility(row, View.GONE); continue; }
            v.setViewVisibility(row, View.VISIBLE);
            v.setTextViewText(idOf(context, "td_" + i + "_t"), it.optString("title", ""));
            // 点这一行 = 勾掉（待办打勾；有日期的提醒给那一天那次打勾），勾完小组件自动刷新、这一行消失
            String rid = it.optString("id", "");
            if (!rid.isEmpty()) {
                Intent done = new Intent(context, app.birthdayreminder.notify.ReminderActionReceiver.class)
                        .setAction(app.birthdayreminder.notify.ReminderActionReceiver.ACTION_DONE)
                        .putExtra("rid", rid);
                String occ = it.optString("occ", "");
                if (!occ.isEmpty()) done.putExtra("occ", occ);
                // 每个小组件的每一行各用一个请求码，互不覆盖
                PendingIntent pi = PendingIntent.getBroadcast(context, 50000 + appWidgetId * 16 + i, done,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                v.setOnClickPendingIntent(row, pi);
            }
            boolean todo = it.optBoolean("todo", false);
            v.setInt(idOf(context, "td_" + i + "_mark"), "setBackgroundResource",
                    drawableId(context, todo ? "wc_todo_ring" : "wc_todo_dot"));
            int dateId = idOf(context, "td_" + i + "_d");
            if (todo) {
                v.setViewVisibility(dateId, View.GONE);
            } else {
                String time = it.optString("time", "");
                v.setViewVisibility(dateId, View.VISIBLE);
                v.setTextViewText(dateId, shortDayLabel(context, it.optLong("dateMs")) + (time.isEmpty() ? "" : " " + time));
            }
        }
        int more = total - shown;
        v.setViewVisibility(R.id.td_more, more > 0 ? View.VISIBLE : View.GONE);
        if (more > 0) v.setTextViewText(R.id.td_more, "\u8fd8\u6709 " + more + " \u9879");
        v.setViewVisibility(R.id.td_empty, total == 0 ? View.VISIBLE : View.GONE);
        if (total == 0) v.setTextViewText(R.id.td_empty, context.getString(R.string.widget_empty_reminder));
        return v;
    }

    /** 今天 / 明天 / 后天 / 10月2日 */
    private static String shortDayLabel(Context c, long targetMs) {
        Calendar a = Calendar.getInstance();
        zero(a);
        Calendar b = Calendar.getInstance();
        b.setTimeInMillis(targetMs);
        zero(b);
        long days = (b.getTimeInMillis() - a.getTimeInMillis()) / 86400000L;
        if (days <= 0) return c.getString(R.string.widget_today);
        if (days == 1) return c.getString(R.string.widget_tomorrow);
        if (days == 2) return c.getString(R.string.widget_day_after_tomorrow);
        return new SimpleDateFormat("M\u6708d\u65e5", Locale.CHINA).format(new Date(targetMs));
    }

    /** Builds the "up to two upcoming/recent items" list used for birthday / alarm / diary widgets. */
    private static RemoteViews buildListViews(Context context, String type, JSONObject data, int heightDp) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_list);
        String icon;
        String title;
        String emptyText;
        if ("birthday".equals(type)) {
            icon = "🎂";
            title = context.getString(R.string.widget_title_birthday);
            emptyText = context.getString(R.string.widget_empty_birthday);
        } else if ("reminder".equals(type)) {
            icon = "🔔";
            title = context.getString(R.string.widget_title_reminder);
            emptyText = context.getString(R.string.widget_empty_reminder);
        } else if ("alarm".equals(type)) {
            icon = "⏰";
            title = context.getString(R.string.widget_title_alarm);
            emptyText = context.getString(R.string.widget_empty_alarm);
        } else if ("fitness".equals(type)) {
            icon = "💪";
            title = context.getString(R.string.widget_title_fitness);
            emptyText = context.getString(R.string.widget_empty_fitness);
        } else if ("allergy".equals(type)) {
            icon = "🌿";
            title = context.getString(R.string.widget_title_allergy);
            emptyText = context.getString(R.string.widget_empty_allergy);
        } else if ("med".equals(type)) {
            icon = "💊";
            title = context.getString(R.string.widget_title_med);
            emptyText = context.getString(R.string.widget_empty_med);
        } else if ("cycle".equals(type)) {
            icon = "🩸";
            title = context.getString(R.string.widget_title_cycle);
            emptyText = context.getString(R.string.widget_empty_cycle);
        } else if ("phrase".equals(type)) {
            icon = "📋";
            title = context.getString(R.string.widget_title_phrase);
            emptyText = context.getString(R.string.widget_empty_phrase);
        } else {
            icon = "📝";
            title = context.getString(R.string.widget_title_diary);
            emptyText = context.getString(R.string.widget_empty_diary);
        }
        v.setTextViewText(R.id.w_icon, icon);
        v.setTextViewText(R.id.w_title, title);

        JSONArray list = data.optJSONArray(type + "List");
        int n = list == null ? 0 : list.length();
        // 按小组件高度决定显示几行（标题约 46dp，每行约 34dp），最多 6 行；拖大就多显示几行
        int rows = Math.max(1, Math.min(MAX_ROWS, (heightDp - 46) / 34));
        n = Math.min(n, rows);
        for (int i = 0; i < MAX_ROWS; i++) {
            fillListItem(context, v, "ls_i" + (i + 1), type, i < n ? list.optJSONObject(i) : null);
        }

        v.setViewVisibility(R.id.w_empty, n == 0 ? View.VISIBLE : View.GONE);
        if (n == 0) v.setTextViewText(R.id.w_empty, emptyText);
        return v;
    }

    private static void fillListItem(Context c, RemoteViews v, String prefix, String type, JSONObject it) {
        int wrapId = idOf(c, prefix + "_wrap");
        if (it == null) {
            v.setViewVisibility(wrapId, View.GONE);
            return;
        }
        v.setViewVisibility(wrapId, View.VISIBLE);
        String dateLabel;
        String text;
        if (it.has("label")) {
            // 健身、过敏、用药、生理期、常用语：数据里已经写好了左边标签和内容
            v.setTextViewText(idOf(c, prefix + "_date"), it.optString("label", ""));
            setChip(c, v, prefix, chipKind(type), it.optString("text", ""));
            return;
        }
        if ("birthday".equals(type)) {
            dateLabel = richDayLabelFuture(c, it.optLong("dateMs"));
            int age = it.optInt("age", -1);
            text = it.optString("name", "") + "\u7684\u751f\u65e5" + (age > 0 ? "\uff08\u5c06\u6ee1" + age + "\u5c81\uff09" : "");
        } else if ("reminder".equals(type)) {
            dateLabel = richDayLabelFuture(c, it.optLong("dateMs"));
            String time = it.optString("time", "");
            text = it.optString("title", "") + (time.isEmpty() ? "" : " · " + time);
        } else if ("alarm".equals(type)) {
            long at = it.optLong("atMs");
            String hm = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(at));
            dateLabel = richDayLabelFuture(c, at) + " " + hm;
            String label = it.optString("label", "");
            text = label.isEmpty() ? c.getString(R.string.alarm_default_label) : label;
        } else {
            dateLabel = richDayLabelPast(c, it.optLong("dateMs"));
            text = it.optString("text", "");
        }
        v.setTextViewText(idOf(c, prefix + "_date"), dateLabel);
        setChip(c, v, prefix, type, text);
    }

    /** Wide (4x2) macOS-style card: big "today" number + lunar day + weekday on the left, next two
     *  upcoming items (holiday / birthday / reminder, whichever comes first) on the right. */
    private static RemoteViews buildAgendaViews(Context context, JSONObject data) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_agenda);
        JSONObject today = data.optJSONObject("today");
        v.setTextViewText(R.id.ag_day, today == null ? "" : String.valueOf(today.optInt("dayNum")));
        v.setTextViewText(R.id.ag_lunar, today == null ? "" : today.optString("lunarDayOnly", ""));
        v.setTextViewText(R.id.ag_week, today == null ? "" : today.optString("weekLabel", ""));

        JSONObject todayItem = data.optJSONObject("todayItem");
        boolean hasToday = todayItem != null;
        v.setViewVisibility(R.id.ag_none, hasToday ? View.GONE : View.VISIBLE);
        v.setViewVisibility(R.id.ag_ev, hasToday ? View.VISIBLE : View.GONE);
        if (!hasToday) v.setTextViewText(R.id.ag_none, context.getString(R.string.widget_no_event));
        else v.setTextViewText(R.id.ag_ev, todayItem.optString("text", ""));

        JSONArray agenda = data.optJSONArray("agendaList");
        int n = agenda == null ? 0 : agenda.length();
        fillAgendaItem(context, v, "ag_i1", n >= 1 ? agenda.optJSONObject(0) : null);
        fillAgendaItem(context, v, "ag_i2", n >= 2 ? agenda.optJSONObject(1) : null);
        v.setViewVisibility(R.id.ag_empty, n == 0 ? View.VISIBLE : View.GONE);
        if (n == 0) v.setTextViewText(R.id.ag_empty, context.getString(R.string.widget_empty_agenda));
        return v;
    }

    private static void fillAgendaItem(Context c, RemoteViews v, String prefix, JSONObject it) {
        int wrapId = idOf(c, prefix + "_wrap");
        if (it == null) {
            v.setViewVisibility(wrapId, View.GONE);
            return;
        }
        v.setViewVisibility(wrapId, View.VISIBLE);
        v.setTextViewText(idOf(c, prefix + "_date"), it.optString("label", ""));
        String kind = it.optString("kind", "holiday");
        setChip(c, v, prefix, kind, it.optString("text", ""));
    }

    /** Mini month grid card (macOS-style): month title + lunar-day subtitle, weekday row, day cells;
     *  today is a filled red dot, days with an event get a thin red underline. Used for the 2x2 size
     *  and for the resizable default (flex) size. */
    /**
     * 小组件当前实际大小（dp）。系统给的是一个范围：竖屏时宽取最小值、高取最大值，横屏反过来。
     * 读不到时返回 {0, 0}。
     */
    static int[] widgetSizeDp(Context context, AppWidgetManager mgr, int id) {
        try {
            android.os.Bundle o = mgr.getAppWidgetOptions(id);
            boolean portrait = context.getResources().getConfiguration().orientation
                    != android.content.res.Configuration.ORIENTATION_LANDSCAPE;
            int w = o.getInt(portrait ? AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH : AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0);
            int h = o.getInt(portrait ? AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT : AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
            return new int[]{w, h};
        } catch (Exception e) {
            return new int[]{0, 0};
        }
    }

    /**
     * 今天那一格红色圆的直径：一格放得下的最大正圆（宽、高取较小的那个），
     * 原来按横屏的偏小尺寸算、还多减了 1dp，所以圆偏小、两位数的日期露在外面。
     */
    static int todayDiameterDp(Context context, AppWidgetManager mgr, int id) {
        int[] sz = widgetSizeDp(context, mgr, id);
        return todayDiameterFor(sz[0], sz[1]);
    }

    static int todayDiameterFor(int w, int h) {
        if (w <= 0 || h <= 0) return 20;
        float rowH = (h - 58f) / 6f;      // 58dp = 上下内边距 + 月份标题 + 星期表头
        float cellW = (w - 24f) / 7f;     // 24dp = 左右内边距
        int d = (int) Math.floor(Math.min(rowH, cellW));
        return Math.max(12, Math.min(30, d));
    }

    private static RemoteViews buildCalendarGridViews(Context context, JSONObject data, int todayDp) {
        RemoteViews v = new RemoteViews(context.getPackageName(), R.layout.widget_calendar);
        JSONObject grid = data.optJSONObject("grid");
        JSONObject today = data.optJSONObject("today");
        v.setTextViewText(R.id.w_cal_title, grid == null ? "" : (grid.optInt("m") + "\u6708"));
        v.setTextViewText(R.id.w_cal_sub, today == null ? "" : today.optString("lunarPlain", ""));
        JSONArray cells = grid == null ? null : grid.optJSONArray("cells");
        for (int i = 0; i < 42; i++) {
            int id = idOf(context, "cal_" + i);
            if (id == 0) continue;
            JSONObject cell = cells != null && i < cells.length() ? cells.optJSONObject(i) : null;
            boolean inMonth = cell != null && cell.optBoolean("inMonth", true);
            boolean isToday = cell != null && cell.optBoolean("today", false);
            boolean hasEvent = cell != null && cell.optBoolean("event", false);
            int day = cell == null ? 0 : cell.optInt("d", 0);
            v.setTextViewText(id, day > 0 ? String.valueOf(day) : "");
            int color;
            if (isToday) color = Color.WHITE;
            else if (inMonth) color = ContextCompat.getColor(context, R.color.wc_text);
            else color = ContextCompat.getColor(context, R.color.wc_muted);
            v.setTextColor(id, color);
            int bg;
            if (isToday) {
                bg = drawableId(context, "wc_today_" + todayDp);
                // 两位数 11sp 大约需要 18dp 的圆才能完整装下；格子太小时缩小今天的数字
                if (todayDp < 18) v.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, Math.max(8f, todayDp * 0.6f));
            }
            else if (hasEvent) bg = drawableId(context, "wc_bar_soft");
            else bg = android.R.color.transparent;
            v.setInt(id, "setBackgroundResource", bg);
        }
        return v;
    }

    static void scheduleMidnight(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, 1);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 5);
        Intent i = new Intent(context, LiWidgetProvider.class).setAction(ACTION_REFRESH);
        PendingIntent pi = PendingIntent.getBroadcast(context, REQ_MIDNIGHT, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setAndAllowWhileIdle(AlarmManager.RTC, c.getTimeInMillis(), pi);
        } catch (SecurityException e) {
            am.set(AlarmManager.RTC, c.getTimeInMillis(), pi);
        }
    }
}
