package app.birthdayreminder.ui.health

import androidx.compose.runtime.Composable
import app.birthdayreminder.ui.common.LiDatePickerDialog
import java.time.LocalDate

private val WEEK = "日一二三四五六"

fun dateLabel(d: LocalDate): String = "${d.year}年${d.monthValue}月${d.dayOfMonth}日 周${WEEK[d.dayOfWeek.value % 7]}"

fun shortDate(s: String, today: LocalDate): String {
    val d = try { LocalDate.parse(s) } catch (e: Exception) { return s }
    return when (d) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        else -> if (d.year == today.year) "${d.monthValue}月${d.dayOfMonth}日 周${WEEK[d.dayOfWeek.value % 7]}" else "${d.year}年${d.monthValue}月${d.dayOfMonth}日"
    }
}

/** 选日期的弹窗（只能选今天及以前：这些是“记录”，不是计划） */
@Composable
fun RecordDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) =
    LiDatePickerDialog(initial, onPick, onDismiss, maxDate = LocalDate.now())
