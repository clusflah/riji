package app.birthdayreminder.ui.med

import app.birthdayreminder.ui.common.SwipeTabs
import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.AppTabs
import app.birthdayreminder.ui.common.TgAvatar
import androidx.compose.animation.AnimatedContent
import app.birthdayreminder.ui.common.CardListItem
import app.birthdayreminder.ui.common.SoftCard
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.Medication
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.domain.MedLogic.Status
import app.birthdayreminder.domain.MedicalLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime

private val SUB_TABS = listOf("今天", "药品", "就医", "记录")

/**
 * “用药就医”（全部页里的一个标签）：里面再分 今天 / 药品 / 就医 / 记录 四个子页。
 * 功能设计参考开源项目 MedTimer（https://github.com/Futsch1/medTimer，MIT 许可证）。
 */
@Composable
fun MedContent(onEdit: (String) -> Unit, onEditRecord: (String) -> Unit, vm: MedViewModel = viewModel()) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    // 每分钟刷新一次（“待服 / 漏服 / 未到时间”会随时间变化）
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now() } }
    Column(modifier = Modifier.fillMaxSize()) {
        // 子页可以左右滑动切换；滑到第一页 / 最后一页再滑，就切换到“全部”页的上一个 / 下一个项目
        SwipeTabs(SUB_TABS, sub, { sub = it }, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TodayPage(vm, now)
                1 -> MedsPage(vm, now.toLocalDate(), onEdit)
                2 -> VisitsPage(vm, now.toLocalDate(), onEditRecord)
                else -> HistoryPage(vm, now, onEditRecord)
            }
        }
    }
}

@Composable
private fun statusColor(s: Status): Color = when (s) {
    // 健康页面不铺蓝色：绿色正常、橙色提醒、红色异常、灰色次要
    Status.TAKEN -> LiColors.done
    Status.SKIPPED -> MaterialTheme.colorScheme.onSurfaceVariant
    Status.DUE -> LiColors.holiday
    Status.MISSED -> MaterialTheme.colorScheme.error
    Status.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
}

// ---------------------------------------------------------------- 今天
@Composable
private fun TodayPage(vm: MedViewModel, now: LocalDateTime) {
    val meds by vm.meds.collectAsState()
    val logs by vm.logs.collectAsState()
    val today = now.toLocalDate()
    val list = remember(meds, logs, now) { MedLogic.day(meds, logs, today, now) }
    val week = remember(meds, logs, now) { MedLogic.adherence(meds, logs, today.minusDays(6), today, now) }
    val extras = remember(logs, today) { logs.filter { !it.scheduled && it.date == today.toString() } }
    val unit = remember(meds) { meds.associate { it.id to it.unit } }
    var showExtra by remember { mutableStateOf(false) }

    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val done = list.count { it.status == Status.TAKEN }
                    Text(if (list.isEmpty()) "今天没有要吃的药" else "今天 ${list.size} 次，已服用 $done 次", fontWeight = FontWeight.SemiBold)
                    Text("最近 7 天服药率：" + (week.rate?.let { "$it%（应服 ${week.due} 次，漏服 ${week.missed}、跳过 ${week.skipped}）" } ?: "还没有记录"),
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (meds.isEmpty()) item {
            Text("还没有添加药品。点右下角“+”添加，设置好服药时间后，到点会提醒你。",
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(list, key = { it.dose.key }) { s ->
            val d = s.dose
            CardListItem(
                leadingContent = { TgAvatar(d.medName) },
                overlineContent = { Text(d.time, color = statusColor(s.status), fontWeight = FontWeight.SemiBold) },
                headlineContent = { Text(d.medName, fontWeight = FontWeight.Medium) },
                supportingContent = { Text(MedLogic.fmt(d.amount) + unit[d.medId].orEmpty() + " · " + MedLogic.statusText(s.status), color = statusColor(s.status)) },
                trailingContent = {
                    Row {
                        when (s.status) {
                            Status.TAKEN, Status.SKIPPED -> TextButton(onClick = { s.log?.let { vm.undo(it) } }) { Text("撤销") }
                            Status.MISSED -> TextButton(onClick = { vm.take(d) }) { Text("补记已服用") }
                            else -> {
                                TextButton(onClick = { vm.skip(d) }) { Text("跳过") }
                                TextButton(onClick = { vm.take(d) }) { Text("已服用") }
                            }
                        }
                    }
                }
            )
        }
        if (extras.isNotEmpty()) {
            item { Text("临时加的", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 8.dp)) }
            items(extras, key = { it.id }) { l ->
                CardListItem(
                    leadingContent = { TgAvatar(l.medName) },
                    overlineContent = { Text(l.time) },
                    headlineContent = { Text(l.medName, fontWeight = FontWeight.Medium) },
                    supportingContent = { Text(MedLogic.fmt(l.amount) + unit[l.medId].orEmpty() + " · 已服用") },
                    trailingContent = { TextButton(onClick = { vm.undo(l) }) { Text("撤销") } }
                )
            }
        }
        if (meds.any { it.active }) item {
            OutlinedButton(onClick = { showExtra = true }, modifier = Modifier.padding(16.dp)) { Text("＋ 临时加一次") }
        }
    }
    if (showExtra) ExtraDoseDialog(meds.filter { it.active }, onDismiss = { showExtra = false }) { m, a -> vm.extra(m, a); showExtra = false }
}

@Composable
private fun ExtraDoseDialog(meds: List<Medication>, onDismiss: () -> Unit, onOk: (Medication, Double) -> Unit) {
    var pick by remember { mutableStateOf(meds.firstOrNull()) }
    var amount by remember { mutableStateOf("1") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("临时加一次") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                meds.forEach { m -> FilterChip(selected = pick?.id == m.id, onClick = { pick = m }, label = { Text(m.name) }) }
                OutlinedTextField(value = amount, onValueChange = { v -> amount = v.filter { it.isDigit() || it == '.' }.take(6) }, singleLine = true,
                    label = { Text("用量") }, suffix = { Text(pick?.unit.orEmpty()) })
                Text("会记在今天、现在这个时间，并扣减库存", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = pick != null && (amount.toDoubleOrNull() ?: 0.0) > 0, onClick = { pick?.let { onOk(it, amount.toDouble()) } }) { Text("记下") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

// ---------------------------------------------------------------- 药品
@Composable
private fun MedsPage(vm: MedViewModel, today: LocalDate, onEdit: (String) -> Unit) {
    val meds by vm.meds.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (meds.isEmpty()) item {
            Text("还没有药品，点右下角“+”添加", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(meds, key = { it.id }) { m ->
            val plan = remember(m.plan) { MedLogic.planFromJson(m.plan) }
            val left = remember(m, today) { MedLogic.daysLeft(m, today) }
            val past = remember(m, today) { MedLogic.daysPastExpiry(m, today) }
            val warn = ArrayList<String>()
            if (MedLogic.isLow(m)) warn += "库存不足"
            if (past != null && past >= 0) warn += "已过期" else if (past != null && past >= -30) warn += "${-past} 天后过期"
            CardListItem(
                leadingContent = { TgAvatar(m.name) },
                headlineContent = { Text(m.name + (if (!m.active) "（已停用）" else ""), color = if (m.active) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant) },
                supportingContent = {
                    Column {
                        Text((if (m.dosage.isNotBlank()) m.dosage + " · " else "") + MedLogic.describe(plan, m.unit))
                        val stockText = m.stock?.let { "剩 ${MedLogic.fmt(it)}${m.unit}" + (left?.let { l -> if (l >= 366) "" else "，约够 $l 天" } ?: "") }
                        if (stockText != null || warn.isNotEmpty()) {
                            Text(listOfNotNull(stockText, warn.joinToString("、").ifEmpty { null }).joinToString(" · "),
                                color = if (warn.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                },
                modifier = Modifier.clickable { onEdit(m.id) }
            )
        }
        item {
            Text("功能设计参考开源项目 MedTimer（github.com/Futsch1/medTimer，MIT 许可证）。本功能不能代替医生建议，用药请遵医嘱。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

// ---------------------------------------------------------------- 就医
@Composable
private fun VisitsPage(vm: MedViewModel, today: LocalDate, onEditRecord: (String) -> Unit) {
    val records by vm.records.collectAsState()
    val meds by vm.meds.collectAsState()
    val next = remember(records, today) { MedicalLogic.upcomingFollowUps(records, today) }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            OutlinedButton(onClick = { onEditRecord("new") }, modifier = Modifier.padding(16.dp)) { Text("＋ 添加就医记录") }
        }
        if (next.isNotEmpty()) item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("接下来的复诊", fontWeight = FontWeight.SemiBold)
                    next.take(5).forEach { f -> Text("${f.date} · " + MedicalLogic.followUpText(f.record), style = MaterialTheme.typography.bodySmall) }
                    Text("复诊前一天晚上 8 点、当天早上 9 点会提醒你", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (records.isEmpty()) item {
            Text("还没有就医记录。看完病记一笔：病情、医院、医嘱、开的药、复诊时间，以后查起来方便，也能导出给医生看。",
                modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(records, key = { it.id }) { r ->
            val names = MedicalLogic.medNames(r, meds)
            val photos = MedicalLogic.listFromJson(r.images).size
            CardListItem(
                leadingContent = { TgAvatar(r.illness) },
                overlineContent = { Text(r.date) },
                headlineContent = { Text(r.illness, fontWeight = FontWeight.Medium) },
                supportingContent = {
                    Column {
                        MedicalLogic.where(r).takeIf { it.isNotBlank() }?.let { Text(it) }
                        val extra = listOfNotNull(
                            if (names.isNotEmpty()) "开药：" + names.joinToString("、") else null,
                            r.followUp?.let { "复诊 $it" },
                            if (photos > 0) "📷 $photos 张" else null
                        )
                        if (extra.isNotEmpty()) Text(extra.joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                    }
                },
                modifier = Modifier.clickable { onEditRecord(r.id) }
            )
        }
    }
}

// ---------------------------------------------------------------- 记录
@Composable
private fun HistoryPage(vm: MedViewModel, now: LocalDateTime, onEditRecord: (String) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meds by vm.meds.collectAsState()
    val logs by vm.logs.collectAsState()
    val today = now.toLocalDate()
    val records by vm.records.collectAsState()
    val rows = remember(meds, logs, now) { MedLogic.history(meds, logs, today.minusDays(29), today, now) }
    // 服药记录和就医记录合成一条时间线
    val timeline = remember(rows, records, today) { MedicalLogic.timeline(rows, records, today.minusDays(29), today) }
    val w7 = remember(meds, logs, now) { MedLogic.adherence(meds, logs, today.minusDays(6), today, now) }
    val w30 = remember(meds, logs, now) { MedLogic.adherence(meds, logs, today.minusDays(29), today, now) }
    var editing by remember { mutableStateOf<MedLogic.HistoryRow?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            val err = vm.exportCsv(uri)
            Toast.makeText(ctx, err ?: "已导出最近 90 天的服药和就医记录", Toast.LENGTH_SHORT).show()
        }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("服药率", fontWeight = FontWeight.SemiBold)
                    Text("最近 7 天：" + (w7.rate?.let { "$it%" } ?: "—") + "　最近 30 天：" + (w30.rate?.let { "$it%" } ?: "—"))
                    Text("30 天里应服 ${w30.due} 次：已服用 ${w30.taken}、跳过 ${w30.skipped}、漏服 ${w30.missed}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { exporter.launch("用药就医记录-${today}.csv") }) { Text("导出最近 90 天（CSV，含就医记录，可给医生看）") }
                    Text("记错了？点下面的服药记录可以修改", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (timeline.isEmpty()) item {
            Text("最近 30 天还没有服药或就医记录", modifier = Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        timeline.groupBy { it.date }.forEach { (date, list) ->
            item(key = "h_$date") {
                Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 2.dp))
            }
            items(list) { e ->      // 不指定键：极少数情况下两行内容可能完全一样，重复的键会让列表崩溃
                when (e) {
                    is MedicalLogic.Entry.Visit -> {
                        val r = e.record
                        SoftCard(modifier = Modifier.fillMaxWidth().clickable { onEditRecord(r.id) }) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("🏥 " + r.illness, fontWeight = FontWeight.SemiBold)
                                MedicalLogic.where(r).takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                                val names = MedicalLogic.medNames(r, meds)
                                if (names.isNotEmpty()) Text("开药：" + names.joinToString("、"), style = MaterialTheme.typography.bodySmall)
                                r.followUp?.let { Text("复诊：$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                            }
                        }
                    }
                    is MedicalLogic.Entry.Dose -> {
                        val r = e.row
                        // 点一下可以改：选错了“已服用 / 跳过”、忘了记的漏服都能在这里补救
                        Row(modifier = Modifier.fillMaxWidth().clickable { editing = r }.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(r.time, modifier = Modifier.width(56.dp))
                            Text(r.medName + " " + MedLogic.fmt(r.amount) + r.unit + (if (r.extra) "（临时）" else ""), modifier = Modifier.weight(1f))
                            Text(MedLogic.statusText(r.status), color = statusColor(r.status))
                        }
                    }
                }
            }
        }
    }
    editing?.let { r ->
        // 药品已经删了的，只能删掉这条记录（没法再按药品扣库存）
        val medExists = meds.any { it.id == r.medId } || vm.privateMeds.value.any { it.id == r.medId }
        EditDoseDialog(
            r, canChange = medExists && !r.extra,
            onDismiss = { editing = null },
            onTaken = { vm.changeRecord(r, taken = true); editing = null },
            onSkipped = { vm.changeRecord(r, taken = false); editing = null },
            onClear = { r.log?.let { vm.undo(it) }; editing = null },
        )
    }
}

/** 改一次服药记录：已服用 ↔ 跳过，或者清除（变回没记过，过了时间就算漏服）。 */
@Composable
private fun EditDoseDialog(
    r: MedLogic.HistoryRow,
    canChange: Boolean,
    onDismiss: () -> Unit,
    onTaken: () -> Unit,
    onSkipped: () -> Unit,
    onClear: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("修改服药记录") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${r.date} ${r.time}　${r.medName} ${MedLogic.fmt(r.amount)}${r.unit}" + (if (r.extra) "（临时）" else ""))
                Text("现在是：" + MedLogic.statusText(r.status), color = statusColor(r.status))
                if (canChange && r.status != Status.TAKEN) {
                    OutlinedButton(onClick = onTaken, modifier = Modifier.fillMaxWidth()) {
                        Text(if (r.status == Status.MISSED) "补记为已服用" else "改为已服用")
                    }
                }
                if (canChange && r.status != Status.SKIPPED) {
                    OutlinedButton(onClick = onSkipped, modifier = Modifier.fillMaxWidth()) { Text("改为跳过") }
                }
                if (r.log != null) {
                    OutlinedButton(onClick = onClear, modifier = Modifier.fillMaxWidth()) {
                        Text(if (r.extra) "删除这条记录" else "清除记录（算作没吃）")
                    }
                }
                Text(
                    when {
                        r.extra -> "临时加的这次只能删除；删除后会把扣掉的库存加回来"
                        !canChange -> "这个药品已经删除，只能清除记录"
                        else -> "改成已服用会扣库存，改成跳过或清除会把扣掉的库存加回来"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}
