package app.birthdayreminder.ui.med

import app.birthdayreminder.privacy.SecureWindow
import app.birthdayreminder.ui.common.TgTopBar
import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.data.Medication
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.domain.MedLogic.MedPlan
import app.birthdayreminder.domain.MedLogic.MedTime
import java.time.LocalDate
import java.util.UUID

private val UNITS = listOf("片", "粒", "ml", "滴", "袋", "支", "喷", "单位")
private const val WEEK = "一二三四五六日"

/** 添加 / 编辑药品：名字、剂量、服药时间和用量、重复方式、起止日期、库存、低库存提醒、有效期、备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedEditScreen(id: String?, onBack: () -> Unit, initialPrivate: Boolean = false, vm: MedViewModel = viewModel()) {
    val ctx = LocalContext.current
    var existing by remember { mutableStateOf<Medication?>(null) }
    var name by remember { mutableStateOf("") }
    var dosage by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf("片") }
    var note by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(initialPrivate) }      // 在隐私空间里新建的就是私密的；编辑已有的保持原样
    SecureWindow(isPrivate)                                    // 私密内容不让截屏
    val times = remember { mutableStateListOf(MedTime("08:00")) }
    val amounts = remember { mutableStateListOf("1") }
    var mode by remember { mutableStateOf("daily") }
    val weekdays = remember { mutableStateListOf<Int>() }
    var every by remember { mutableStateOf("2") }
    var on by remember { mutableStateOf("21") }
    var off by remember { mutableStateOf("7") }
    var start by remember { mutableStateOf(LocalDate.now().toString()) }
    var end by remember { mutableStateOf<String?>(null) }
    var stock by remember { mutableStateOf("") }
    var low by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf<String?>(null) }
    var active by remember { mutableStateOf(true) }
    var ring by remember { mutableStateOf(false) }      // 像闹钟一样响（存在本机设置里）
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val m = vm.get(id) ?: return@LaunchedEffect
        existing = m
        name = m.name; dosage = m.dosage; unit = m.unit; note = m.note; isPrivate = m.isPrivate
        val p = MedLogic.planFromJson(m.plan)
        times.clear(); times.addAll(p.times); amounts.clear(); amounts.addAll(p.times.map { MedLogic.fmt(it.amount) })
        mode = p.mode; weekdays.clear(); weekdays.addAll(p.weekdays)
        every = p.every.toString(); on = p.on.toString(); off = p.off.toString()
        start = p.start.ifEmpty { LocalDate.now().toString() }; end = p.end
        stock = m.stock?.let { MedLogic.fmt(it) } ?: ""; low = m.lowStock?.let { MedLogic.fmt(it) } ?: ""
        expiry = m.expiry; active = m.active
        ring = m.id in Prefs.medRingIds(ctx)
    }

    fun pickDate(initial: String?, onPick: (String) -> Unit) {
        val d = initial?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } } ?: LocalDate.now()
        DatePickerDialog(ctx, { _, y, mo, day -> onPick(LocalDate.of(y, mo + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun pickTime(i: Int) {
        val (h, m) = times[i].time.split(":").map { it.toInt() }
        TimePickerDialog(ctx, { _, hh, mm -> times[i] = times[i].copy(time = "%02d:%02d".format(hh, mm)) }, h, m, true).show()
    }

    fun save() {
        val n = name.trim()
        if (n.isEmpty()) { error = "请填写药品名称"; return }
        val list = times.mapIndexed { i, t -> MedTime(t.time, amounts.getOrNull(i)?.toDoubleOrNull() ?: 0.0) }
        val plan = MedPlan(mode, list.sortedBy { it.time }, weekdays.toSet(), every.toIntOrNull() ?: 0, on.toIntOrNull() ?: 0, off.toIntOrNull() ?: 0, start, end)
        MedLogic.validate(plan)?.let { error = it; return }
        val st = stock.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (stock.isNotBlank() && (st == null || st < 0)) { error = "库存要填数字"; return }
        val lw = low.trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull()
        if (low.isNotBlank() && (lw == null || lw < 0)) { error = "低库存提醒要填数字"; return }
        error = null
        val medId = existing?.id ?: UUID.randomUUID().toString()
        Prefs.setMedRing(ctx, medId, ring)
        vm.save(Medication(medId, n.take(30), dosage.trim().take(30), note.trim().take(200),
            MedLogic.planToJson(plan), st, unit.trim().ifEmpty { "片" }.take(6), lw, expiry, active, isPrivate = isPrivate), onBack)
    }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(if (existing != null) "编辑药品" else "添加药品") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            actions = { TextButton(onClick = { save() }) { Text("保存") } }
        )
    }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, label = { Text("药品名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = dosage, onValueChange = { dosage = it.take(30) }, label = { Text("剂量说明（选填，比如 1 片 / 5ml / 饭后）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("单位", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                UNITS.forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(u) }) }
            }

            HorizontalDivider()
            Text("服药时间", style = MaterialTheme.typography.labelLarge)
            times.forEachIndexed { i, t ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickTime(i) }) { Text(t.time) }
                    OutlinedTextField(value = amounts.getOrElse(i) { "1" }, onValueChange = { v -> if (i < amounts.size) amounts[i] = v.filter { it.isDigit() || it == '.' }.take(6) },
                        singleLine = true, suffix = { Text(unit) }, label = { Text("用量") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                    if (times.size > 1) TextButton(onClick = { times.removeAt(i); if (i < amounts.size) amounts.removeAt(i) }) { Text("删除") }
                }
            }
            TextButton(onClick = {
                // 新的时间默认比最后一个晚 4 小时
                val last = times.lastOrNull()?.time?.split(":")?.get(0)?.toIntOrNull() ?: 8
                times.add(MedTime("%02d:00".format((last + 4) % 24))); amounts.add("1")
            }) { Text("＋ 添加一个时间") }

            Text("重复", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                MedLogic.MODES.forEach { (v, l) -> FilterChip(selected = mode == v, onClick = { mode = v }, label = { Text(l) }) }
            }
            when (mode) {
                "weekly" -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..7).forEach { d ->
                        FilterChip(selected = d in weekdays, onClick = { if (d in weekdays) weekdays.remove(d) else weekdays.add(d) }, label = { Text("周" + WEEK[d - 1]) })
                    }
                }
                "every" -> OutlinedTextField(value = every, onValueChange = { every = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true,
                    label = { Text("每隔几天吃一次（从开始日期算）") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                "cycle" -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = on, onValueChange = { on = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true, label = { Text("吃几天") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    OutlinedTextField(value = off, onValueChange = { off = it.filter { c -> c.isDigit() }.take(3) }, singleLine = true, label = { Text("停几天") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(start) { start = it } }) { Text("开始：$start") }
                OutlinedButton(onClick = { pickDate(end ?: start) { end = it } }) { Text("结束：" + (end ?: "不限")) }
                if (end != null) TextButton(onClick = { end = null }) { Text("不限") }
            }

            HorizontalDivider()
            Text("库存与有效期（选填）", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = stock, onValueChange = { stock = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, singleLine = true,
                    label = { Text("现有库存") }, suffix = { Text(unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                OutlinedTextField(value = low, onValueChange = { low = it.filter { c -> c.isDigit() || c == '.' }.take(7) }, singleLine = true,
                    label = { Text("少于多少提醒") }, suffix = { Text(unit) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
            Text("点“已服用”会自动扣库存，低于提醒数量时会通知你补药", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(expiry) { expiry = it } }) { Text("有效期：" + (expiry ?: "未填")) }
                if (expiry != null) TextButton(onClick = { expiry = null }) { Text("清除") }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("提醒")
                    Text(if (active) "到点会提醒" else "已停用：不再提醒，记录保留", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = active, onCheckedChange = { active = it })
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("像闹钟一样响")
                    Text("用闹钟铃声，一直响到点“已服用 / 跳过”。必须按时吃的药建议打开", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = ring, onCheckedChange = { ring = it }, enabled = active)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("删除这个药品", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除药品") },
            text = { Text("删除后不再提醒。以前的服药记录会保留在“记录”里。只是暂时不吃的话，可以关掉“提醒”开关。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; existing?.let { vm.delete(it.id, onBack) } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
