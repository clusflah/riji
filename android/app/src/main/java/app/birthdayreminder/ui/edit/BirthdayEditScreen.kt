package app.birthdayreminder.ui.edit

import app.birthdayreminder.ui.common.TgTopBar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.Birthday
import app.birthdayreminder.lunar.LunarCalendar
import app.birthdayreminder.ui.common.DropdownPicker
import app.birthdayreminder.ui.common.FieldLabel
import app.birthdayreminder.privacy.SecureWindow
import java.time.LocalDate
import java.util.UUID

private val ADVANCE_OPTIONS = listOf(0 to "当天", 1 to "提前1天", 3 to "提前3天", 7 to "提前7天")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BirthdayEditScreen(
    id: String?,
    onBack: () -> Unit,
    initialPrivate: Boolean = false,
    vm: BirthdayViewModel = viewModel()
) {
    var isPrivate by remember { mutableStateOf(initialPrivate) }
    SecureWindow(isPrivate)
    val today = remember { LocalDate.now() }
    var existing by remember { mutableStateOf<Birthday?>(null) }
    var name by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("solar") }
    var yearText by remember { mutableStateOf("") }
    var month by remember { mutableIntStateOf(today.monthValue) }
    var day by remember { mutableIntStateOf(today.dayOfMonth) }
    var leap by remember { mutableStateOf(false) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var advance by remember { mutableStateOf(app.birthdayreminder.notify.SettingsStore.get(ctx).defAdvance.toSet()) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id != null) {
            vm.get(id)?.let { b ->
                existing = b
                name = b.name
                type = b.type
                yearText = b.year?.toString() ?: ""
                month = b.month
                day = b.day
                leap = b.leap
                advance = b.advance.toSet()
                note = b.note
                isPrivate = b.isPrivate
            }
        }
    }

    fun save() {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) { error = "请填写姓名"; return }
        var year: Int? = null
        if (yearText.isNotBlank()) {
            val y = yearText.trim().toIntOrNull()
            if (y == null || y < 1900 || y > today.year) { error = "出生年份需要在 1900 到 ${today.year} 之间，或者留空"; return }
            year = y
        }
        if (type == "solar") {
            val maxDay = if (month == 2) 29 else LocalDate.of(2001, month, 1).lengthOfMonth()
            if (day > maxDay) { error = "${month}月没有${day}日"; return }
        }
        error = null
        val b = Birthday(
            id = existing?.id ?: UUID.randomUUID().toString(),
            name = trimmed,
            type = type,
            year = year,
            month = month,
            day = day,
            leap = type == "lunar" && leap,
            advance = advance.sorted(),
            note = note.trim(),
            isPrivate = isPrivate
        )
        vm.save(b, onBack)
    }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text(if (existing != null) "编辑生日" else "添加生日") },
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
                value = name,
                onValueChange = { name = it.take(30) },
                label = { Text("姓名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            FieldLabel("生日类型")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = type == "solar", onClick = { type = "solar"; leap = false }, label = { Text("公历") })
                FilterChip(selected = type == "lunar", onClick = { type = "lunar" }, label = { Text("农历") })
            }

            FieldLabel("出生年份（可选）")
            OutlinedTextField(
                value = yearText,
                onValueChange = { yearText = it.filter(Char::isDigit).take(4) },
                placeholder = { Text("例如 1990") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )

            FieldLabel("日期")
            val monthOptions = if (type == "lunar") {
                (1..12).map { LunarCalendar.monthLabel(it, false) }
            } else {
                (1..12).map { "${it}月" }
            }
            val dayOptions = if (type == "lunar") {
                (1..30).map { LunarCalendar.dayLabel(it) }
            } else {
                (1..31).map { "${it}日" }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                DropdownPicker("月", monthOptions, month - 1, { month = it + 1 }, Modifier.weight(1f))
                DropdownPicker("日", dayOptions, day - 1, { day = it + 1 }, Modifier.weight(1f))
            }
            if (type == "lunar") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("闰月", modifier = Modifier.weight(1f))
                    Switch(checked = leap, onCheckedChange = { leap = it })
                }
            }

            FieldLabel("提前提醒（可多选）")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState())
            ) {
                ADVANCE_OPTIONS.forEach { (v, label) ->
                    FilterChip(
                        selected = v in advance,
                        onClick = { advance = if (v in advance) advance - v else advance + v },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it.take(200) },
                label = { Text("备注（选填）") },
                modifier = Modifier.fillMaxWidth()
            )


            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            if (existing != null) {
                OutlinedButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("删除这个生日", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除生日") },
            text = { Text("确定删除“${existing?.name ?: ""}”的生日吗？") },
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
