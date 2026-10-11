package app.birthdayreminder;

import android.content.Context;

/** Resolves the 42 generated cell view ids (cal_0..cal_41) declared in widget_calendar.xml. */
final class CalendarCellIds {
    private CalendarCellIds() {}

    static int[] resolve(Context c) {
        int[] ids = new int[42];
        for (int i = 0; i < 42; i++) {
            ids[i] = c.getResources().getIdentifier("cal_" + i, "id", c.getPackageName());
        }
        return ids;
    }
}
