package app.birthdayreminder.ui.health

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

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
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val todayMs = LocalDate.now().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        selectableDates = object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis <= todayMs
        }
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) { DatePicker(state = state) }
}
