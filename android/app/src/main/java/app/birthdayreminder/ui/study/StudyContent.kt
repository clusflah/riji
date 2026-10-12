package app.birthdayreminder.ui.study

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.StudyLog
import app.birthdayreminder.domain.StudyItem
import app.birthdayreminder.domain.StudyLogic
import app.birthdayreminder.ui.common.EventCard
import app.birthdayreminder.ui.common.SoftCard
import app.birthdayreminder.ui.common.TgSectionTitle
import java.time.LocalDate
import java.util.UUID

private val WEEK = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/**
 * “学习”标签：上面是统计（今天、本周、本月、连续天数），常用项目 / 学习提醒 / 从日记导入三个入口，
 * 下面按日期列出学习记录，点一条可以修改。新建由右下角“+”触发（见 StudyUi）。
 */
@Composable
fun StudyContent(vm: StudyViewModel = viewModel()) {
    val logs by vm.logs.collectAsState()
    val today = remember { LocalDate.now() }
    var editing by remember { mutableStateOf<StudyLog?>(null) }
    var showPresets by remember { mutableStateOf(false) }
    var showReminders by remember { mutableStateOf(false) }
    val request by StudyUi.newRequest
    LaunchedEffect(request) {
        request?.let { d -> editing = StudyLog(UUID.randomUUID().toString(), (if (d.isAfter(today)) today else d).toString()); StudyUi.newRequest.value = null }
    }
    val week = remember(logs) { StudyLogic.stats(logs, today.minusDays((today.dayOfWeek.value - 1).toLong()), today) }
    val month = remember(logs) { StudyLogic.stats(logs, today.withDayOfMonth(1), today) }
    val todaySt = remember(logs) { StudyLogic.stats(logs, today, today) }
    val streak = remember(logs) { StudyLogic.streak(logs, today) }

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp)) {
        item(key = "stats") {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (todaySt.records == 0) "今天还没有学习记录" else "今天 " + listOf(StudyLogic.fmtMinutes(todaySt.minutes),
                        todaySt.items.take(4).joinToString("、") { it.name + " " + StudyLogic.fmtAmount(it.amount) + it.unit }).filter { it.isNotBlank() }.joinToString(" · "),
                        fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Text("本周学了 ${week.days} 天" + StudyLogic.fmtMinutes(week.minutes).let { if (it.isEmpty()) "" else "，共 $it" } +
                        " · 本月 ${month.days} 天" + StudyLogic.fmtMinutes(month.minutes).let { if (it.isEmpty()) "" else "，共 $it" } +
                        (if (streak > 1) " · 已连续 $streak 天" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (month.items.isNotEmpty()) Text("本月合计：" + month.items.take(6).joinToString("、") { it.name + " " + StudyLogic.fmtAmount(it.amount) + it.unit },
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).horizontalScroll(rememberScrollState())) {
                AssistChip(onClick = { showPresets = true }, label = { Text("常用项目") })
                AssistChip(onClick = { showReminders = true }, label = { Text("学习提醒") })
                AssistChip(onClick = { vm.scanDiary() }, label = { Text("从日记导入") })
            }
        }
        if (logs.isEmpty()) item(key = "empty") {
            Text("还没有学习记录，点右下角“+”记一条。以前写在日记里的学习内容，可以点上面的“从日记导入”。",
                modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        logs.groupBy { it.date }.forEach { (date, list) ->
            item(key = "d$date") {
                val d = runCatching { LocalDate.parse(date) }.getOrNull()
                TgSectionTitle(d?.let { "${if (it.year != today.year) "${it.year}年" else ""}${it.monthValue}月${it.dayOfMonth}日 ${WEEK[it.dayOfWeek.value - 1]}" +
                    if (it == today) " · 今天" else "" } ?: date,
                    hint = StudyLogic.fmtMinutes(list.sumOf { it.minutes }).ifEmpty { null })
            }
            items(list, key = { it.id }) { l ->
                val items = remember(l.items) { StudyLogic.itemsFromJson(l.items) }
                EventCard("study", StudyLogic.summary(items).ifEmpty { "学习" },
                    subtitle = listOf(l.time, StudyLogic.fmtMinutes(l.minutes), l.note).filter { it.isNotBlank() }.joinToString(" · ").ifEmpty { null },
                    onClick = { editing = l })
            }
        }
    }

    editing?.let { l -> StudyEditor(vm, l, isNew = logs.none { it.id == l.id }, onDismiss = { editing = null }) }
    if (showPresets) PresetsDialog(vm, onDismiss = { showPresets = false })
    if (showReminders) RemindersDialog(vm, onDismiss = { showReminders = false })
    vm.importCandidates.value?.let { ImportDialog(vm, it, onDismiss = { vm.importCandidates.value = null }) }
}

/** 编辑框里的一行：用字符串存，输入时不会被格式化打断 */
private class Row3(name: String, amount: String, unit: String) {
    var name by mutableStateOf(name); var amount by mutableStateOf(amount); var unit by mutableStateOf(unit)
    fun toItem() = StudyItem(name.trim(), amount.trim().toDoubleOrNull(), unit.trim())
}

@Composable
private fun StudyEditor(vm: StudyViewModel, log: StudyLog, isNew: Boolean, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val presets by vm.presets.collectAsState()
    var date by remember { mutableStateOf(runCatching { LocalDate.parse(log.date) }.getOrDefault(LocalDate.now())) }
    val rows = remember { mutableStateListOf<Row3>().apply {
        StudyLogic.itemsFromJson(log.items).forEach { add(Row3(it.name, StudyLogic.fmtAmount(it.amount), it.unit)) }
        if (isEmpty()) add(Row3("", "", ""))
    } }
    var minutes by remember { mutableStateOf(if (log.minutes > 0) log.minutes.toString() else "") }
    var time by remember { mutableStateOf(log.time) }
    var note by remember { mutableStateOf(log.note) }
    var confirmDelete by remember { mutableStateOf(false) }
    // 点常用项目：第一行还是空的就填进去，否则加一行
    fun addPreset(name: String, unit: String, amount: Double?) {
        val empty = rows.firstOrNull { it.name.isBlank() && it.amount.isBlank() }
        if (empty != null) { empty.name = name; empty.unit = unit; empty.amount = StudyLogic.fmtAmount(amount) }
        else rows.add(Row3(name, StudyLogic.fmtAmount(amount), unit))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "记录学习" else "修改学习记录") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = {
                        DatePickerDialog(ctx, { _, y, m, d -> date = LocalDate.of(y, m + 1, d).let { if (it.isAfter(LocalDate.now())) LocalDate.now() else it } },
                            date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }) { Text("${date.monthValue}月${date.dayOfMonth}日 ${WEEK[date.dayOfWeek.value - 1]}") }
                    TextButton(onClick = {
                        val (h, m) = time.split(":").mapNotNull { it.toIntOrNull() }.let { if (it.size == 2) it[0] to it[1] else java.time.LocalTime.now().let { t -> t.hour to t.minute } }
                        TimePickerDialog(ctx, { _, hh, mm -> time = "%02d:%02d".format(hh, mm) }, h, m, true).show()
                    }) { Text(time.ifEmpty { "开始时间（选填）" }) }
                    if (time.isNotEmpty()) IconButton(onClick = { time = "" }) { Icon(Icons.Default.Close, contentDescription = "清除时间") }
                }
                if (presets.isNotEmpty()) {
                    Text("常用项目（点一下加一行）", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        presets.forEach { p -> AssistChip(onClick = { addPreset(p.name, p.unit, p.amount) }, label = { Text(p.name) }) }
                    }
                }
                Text("学习内容", style = MaterialTheme.typography.labelLarge)
                rows.forEachIndexed { i, r ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(value = r.name, onValueChange = { r.name = it.take(40) }, singleLine = true, label = { Text("内容") }, modifier = Modifier.weight(1.6f))
                        OutlinedTextField(value = r.amount, onValueChange = { v -> r.amount = v.filter { it.isDigit() || it == '.' }.take(7) }, singleLine = true,
                            label = { Text("量") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(0.8f))
                        OutlinedTextField(value = r.unit, onValueChange = { r.unit = it.take(6) }, singleLine = true, label = { Text("单位") }, modifier = Modifier.weight(0.7f))
                        Column {
                            // 存成常用项目
                            IconButton(enabled = r.name.isNotBlank(), onClick = { vm.savePreset(r.toItem()); android.widget.Toast.makeText(ctx, "已存为常用项目", android.widget.Toast.LENGTH_SHORT).show() }) {
                                Icon(Icons.Outlined.StarOutline, contentDescription = "存为常用项目")
                            }
                            if (rows.size > 1) IconButton(onClick = { rows.removeAt(i) }) { Icon(Icons.Default.Close, contentDescription = "删掉这一行") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick = { rows.add(Row3("", "", "")) }) { Text("＋ 加一行") }
                    StudyLogic.UNITS.take(4).forEach { u ->
                        val last = rows.lastOrNull()
                        if (last != null && last.unit.isBlank()) AssistChip(onClick = { last.unit = u }, label = { Text(u) })
                    }
                }
                Text("学习时长（分钟）", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    OutlinedTextField(value = minutes, onValueChange = { v -> minutes = v.filter { it.isDigit() }.take(4) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(96.dp))
                    listOf(15, 30, 45, 60, 90, 120).forEach { n -> FilterChip(selected = minutes == n.toString(), onClick = { minutes = n.toString() }, label = { Text(StudyLogic.fmtMinutes(n)) }) }
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(500) }, label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth())
                if (!isNew) TextButton(onClick = { confirmDelete = true }) { Text("删除这条记录", color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val items = rows.map { it.toItem() }.filter { it.name.isNotBlank() }
                if (items.isEmpty() && (minutes.toIntOrNull() ?: 0) == 0 && note.isBlank()) {
                    android.widget.Toast.makeText(ctx, "至少填一项学习内容或时长", android.widget.Toast.LENGTH_SHORT).show(); return@TextButton
                }
                vm.save(log.copy(date = date.toString(), items = StudyLogic.itemsToJson(items), minutes = (minutes.toIntOrNull() ?: 0).coerceIn(0, 24 * 60),
                    time = time, note = note.trim()))
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("删除这条学习记录？") },
        confirmButton = { TextButton(onClick = { vm.delete(log.id); confirmDelete = false; onDismiss() }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
    )
}

/** 常用项目：列表、删除、添加（名称、默认数量、单位） */
@Composable
private fun PresetsDialog(vm: StudyViewModel, onDismiss: () -> Unit) {
    val presets by vm.presets.collectAsState()
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("题") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("常用项目") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("记录时点一下常用项目就能加一行。记录里每行右边的☆也能把那一项存成常用项目。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (presets.isEmpty()) Text("还没有常用项目", color = MaterialTheme.colorScheme.onSurfaceVariant)
                presets.forEach { p ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(p.name + (if (p.amount != null || p.unit.isNotBlank()) "  " + StudyLogic.fmtAmount(p.amount) + p.unit else ""), modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deletePreset(p.id) }) { Icon(Icons.Default.Close, contentDescription = "删除") }
                    }
                }
                HorizontalDivider()
                Text("添加", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true, label = { Text("名称，比如：逻辑填空") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(7) }, singleLine = true,
                        label = { Text("默认数量（选填）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = unit, onValueChange = { unit = it.take(6) }, singleLine = true, label = { Text("单位") }, modifier = Modifier.weight(0.7f))
                }
                OutlinedButton(enabled = name.isNotBlank(), onClick = { vm.savePreset(StudyItem(name, amount.toDoubleOrNull(), unit)); name = ""; amount = "" }) { Text("添加") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** 学习提醒：每天固定时间提醒（用的是普通提醒，提醒功能要开着才会响） */
@Composable
private fun RemindersDialog(vm: StudyViewModel, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val reminders by vm.reminders.collectAsState()
    var title by remember { mutableStateOf("该学习了") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("学习提醒") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("每天到点发一条提醒通知。它们也会出现在“提醒”里；在功能开关里关掉“提醒”的话就不会响。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (reminders.isEmpty()) Text("还没有学习提醒", color = MaterialTheme.colorScheme.onSurfaceVariant)
                reminders.sortedBy { it.time }.forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("每天 ${r.time}  ${r.title}", modifier = Modifier.weight(1f))
                        IconButton(onClick = { vm.deleteReminder(r) }) { Icon(Icons.Default.Close, contentDescription = "删除") }
                    }
                }
                HorizontalDivider()
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true, label = { Text("提醒内容") }, modifier = Modifier.fillMaxWidth())
                OutlinedButton(onClick = {
                    TimePickerDialog(ctx, { _, h, m -> vm.addReminder(title, "%02d:%02d".format(h, m)) }, 20, 0, true).show()
                }) { Text("选时间并添加") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } }
    )
}

/** 从日记导入：列出识别出来的日记，勾选后导入（日记本身不删） */
@Composable
private fun ImportDialog(vm: StudyViewModel, list: List<StudyViewModel.ImportCandidate>, onDismiss: () -> Unit) {
    val chosen = remember(list) { mutableStateListOf<StudyViewModel.ImportCandidate>().apply { addAll(list) } }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从日记导入") },
        text = {
            if (list.isEmpty()) Text("没有找到能识别的学习内容。日记里要写成“名称 数字”的样子，比如“逻辑填空 20 图推 15”。")
            else Column {
                Text("找到 ${list.size} 篇，导入后每篇变成一条学习记录（时长空着，可以再补），日记本身不动。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyColumn(modifier = Modifier.padding(top = 8.dp)) {
                    items(list, key = { it.date + it.text.hashCode() }) { c ->
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (c in chosen) chosen.remove(c) else chosen.add(c) }) {
                            Checkbox(checked = c in chosen, onCheckedChange = { if (it) chosen.add(c) else chosen.remove(c) })
                            Column {
                                Text(c.date, style = MaterialTheme.typography.labelMedium)
                                Text(StudyLogic.summary(c.items), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (list.isNotEmpty()) TextButton(enabled = chosen.isNotEmpty(), onClick = { vm.importSelected(chosen.toList()) }) { Text("导入 ${chosen.size} 条") }
            else TextButton(onClick = onDismiss) { Text("知道了") }
        },
        dismissButton = { if (list.isNotEmpty()) TextButton(onClick = onDismiss) { Text("取消") } }
    )
}
