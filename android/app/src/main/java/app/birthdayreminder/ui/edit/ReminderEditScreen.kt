package app.birthdayreminder.ui.edit

import app.birthdayreminder.ui.common.TgTopBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.Reminder
import app.birthdayreminder.ui.common.FieldLabel
import app.birthdayreminder.privacy.SecureWindow
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.util.UUID

private val REPEAT_OPTIONS = listOf(
    "none" to "不重复", "daily" to "每天", "weekly" to "每周",
    "monthly" to "每月", "yearly" to "每年", "interval" to "自定义周期"
)
private val ADVANCE_OPTIONS = listOf(0 to "准时", 10 to "提前10分钟", 60 to "提前1小时", 1440 to "提前1天")
private val UNIT_OPTIONS = listOf("day" to "天", "month" to "个月", "year" to "年")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderEditScreen(
    id: String?,
    initialDate: LocalDate,
    onBack: () -> Unit,
    initialPrivate: Boolean = false,
    vm: ReminderViewModel = viewModel()
) {
    var isPrivate by remember { mutableStateOf(initialPrivate) }
    SecureWindow(isPrivate)
    var existing by remember { mutableStateOf<Reminder?>(null) }
    var isTodo by remember { mutableStateOf(false) }   // 无时间待办：没有日期、不提醒，做完打勾
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(initialDate) }
    var time by remember { mutableStateOf("") } // 空字符串 = 全天
    var repeat by remember { mutableStateOf("none") }
    var advance by remember { mutableIntStateOf(0) }
    var intervalText by remember { mutableStateOf("2") }
    var intervalUnit by remember { mutableStateOf("day") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            vm.get(id)?.let { r ->
                existing = r
                title = r.title
                isTodo = r.date.isBlank()
                isPrivate = r.isPrivate
                date = try { LocalDate.parse(r.date) } catch (e: Exception) { initialDate }
                time = r.time
                repeat = r.repeat
                advance = r.advance
                intervalText = (r.intervalN ?: 2).toString()
                intervalUnit = r.intervalUnit ?: "day"
                note = r.note
            }
        }
    }

    fun save() {
        val t = title.trim()
        if (t.isEmpty()) { error = "请填写提醒内容"; return }
        if (isTodo) {
            error = null
            val old = existing
            // 从“有日期的提醒”改成待办：原来按日期打的勾不再有意义，只保留待办自己的完成状态
            val done = if (old != null && old.date.isBlank()) old.done else emptyMap()
            vm.save(
                Reminder(
                    id = old?.id ?: UUID.randomUUID().toString(), title = t, date = "", time = "",
                    repeat = "none", advance = 0, note = note.trim(), done = done, isPrivate = isPrivate
                ), onBack
            )
            return
        }
        val n = intervalText.toIntOrNull() ?: 0
        if (repeat == "interval" && n < 1) { error = "自定义周期至少为 1"; return }
        error = null
        val r = Reminder(
            id = existing?.id ?: UUID.randomUUID().toString(),
            title = t,
            date = date.toString(),
            time = time,
            repeat = repeat,
            advance = advance,
            note = note.trim(),
            done = existing?.done?.filterKeys { it != "todo" } ?: emptyMap(),
            intervalUnit = if (repeat == "interval") intervalUnit else null,
            intervalN = if (repeat == "interval") n else null,
            isPrivate = isPrivate
        )
        vm.save(r, onBack)
    }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text(if (existing != null) (if (isTodo) "编辑待办" else "编辑提醒") else (if (isTodo) "添加待办" else "添加提醒")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = { TextButton(onClick = { save() }) { Text("保存") } }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it.take(60) },
                label = { Text("提醒内容") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FieldLabel("类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = !isTodo, onClick = { isTodo = false }, label = { Text("有日期的提醒") })
                FilterChip(selected = isTodo, onClick = { isTodo = true }, label = { Text("无时间待办") })
            }
            if (isTodo) {
                Text(
                    "待办没有日期和时间，不会发通知；在“全部 → 提醒”和今天的日程里能看到，做完打个勾就行。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!isTodo) {
            FieldLabel("日期")
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) {
                Text("${date.year}年${date.monthValue}月${date.dayOfMonth}日")
            }

            FieldLabel("时间")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = time.isBlank(), onClick = { time = "" }, label = { Text("全天") })
                OutlinedButton(onClick = { showTime = true }) {
                    Text(if (time.isBlank()) "指定时间" else time)
                }
            }

            FieldLabel("重复")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                REPEAT_OPTIONS.forEach { (v, label) ->
                    FilterChip(selected = repeat == v, onClick = { repeat = v }, label = { Text(label) })
                }
            }
            if (repeat == "interval") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    Text("每")
                    OutlinedTextField(
                        value = intervalText,
                        onValueChange = { intervalText = it.filter(Char::isDigit).take(3) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    UNIT_OPTIONS.forEach { (v, label) ->
                        FilterChip(selected = intervalUnit == v, onClick = { intervalUnit = v }, label = { Text(label) })
                    }
                }
            }

            FieldLabel("提前提醒")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ADVANCE_OPTIONS.forEach { (v, label) ->
                    FilterChip(selected = advance == v, onClick = { advance = v }, label = { Text(label) })
                }
            }
            } // if (!isTodo)

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("备注（选填）") },
                modifier = Modifier.fillMaxWidth()
            )


            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isTodo) "删除这条待办" else "删除这条提醒", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDate = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("取消") } }
        ) { DatePicker(state = state) }
    }

    if (showTime) {
        val initial = try { LocalTime.parse(time) } catch (e: Exception) { LocalTime.of(9, 0) }
        val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = {
                    time = "%02d:%02d".format(state.hour, state.minute)
                    showTime = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
            text = { TimePicker(state = state) }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除提醒") },
            text = { Text("确定删除“${existing?.title ?: ""}”吗？") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    existing?.let { vm.delete(it, onBack) }
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
