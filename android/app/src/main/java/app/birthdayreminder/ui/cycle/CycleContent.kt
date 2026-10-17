package app.birthdayreminder.ui.cycle

import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.SwipeTabs
import app.birthdayreminder.ui.common.AppTabs
import androidx.compose.animation.AnimatedContent
import app.birthdayreminder.ui.common.SoftCard
import android.app.TimePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.birthdayreminder.data.CycleDay
import app.birthdayreminder.data.CycleProfiles
import app.birthdayreminder.data.Medication
import app.birthdayreminder.domain.CycleLogic
import app.birthdayreminder.domain.CycleLogic.Mark
import app.birthdayreminder.domain.MedLogic
import app.birthdayreminder.domain.MedicalLogic
import app.birthdayreminder.domain.PillLogic
import app.birthdayreminder.domain.PillLogic.DayStatus
import app.birthdayreminder.notify.CycleScheduler
import app.birthdayreminder.notify.Prefs
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId

private val SUB = listOf("今天", "日历", "趋势", "避孕药")
private const val WEEK = "一二三四五六日"

private val PERIOD_RED = LiColors.period
private val FERTILE_GREEN = LiColors.fertile
private val PILL_BLUE = Color(0xFF2F8FBF)
private val MISS_ORANGE = Color(0xFFE08A1E)

/**
 * “生理期”（全部页里的一个标签）：今天 / 日历 / 趋势 / 避孕药。
 * 功能设计参考开源项目 Mooneva Cycle（GPL-3.0，只参考功能、没有复制代码），避孕药部分参照之前对 Mooneva 做的改进。
 */
/** 某一份生理期数据的 ViewModel（外面和隐私空间各一个，互不影响） */
@Composable
fun rememberCycleViewModel(profile: String): CycleViewModel {
    val app = LocalContext.current.applicationContext as android.app.Application
    return viewModel(key = "cycle_$profile", factory = viewModelFactory {
        initializer { CycleViewModel(app, profile) }
    })
}

/** selected：首页月历上选中的那天（从首页打开时传进来），“今天”页会多一个改这一天的按钮 */
@Composable
fun CycleContent(vm: CycleViewModel = viewModel(), selected: LocalDate? = null) {
    var sub by rememberSaveable { mutableIntStateOf(0) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) { while (true) { delay(60_000); now = LocalDateTime.now() } }
    var editDate by remember { mutableStateOf<LocalDate?>(null) }
    var settingsTick by remember { mutableIntStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize()) {
        // 子页可以左右滑动切换；滑到第一页 / 最后一页再滑，就切换到“全部”页的上一个 / 下一个项目
        SwipeTabs(SUB, sub, { sub = it }, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> TodayPage(vm, now, settingsTick, selected, onEdit = { editDate = it }, onSettings = { showSettings = true }, onPillTab = { sub = 3 })
                1 -> CalendarPage(vm, now.toLocalDate(), settingsTick, onEdit = { editDate = it })
                2 -> TrendsPage(vm, now.toLocalDate(), settingsTick)
                else -> PillPage(vm, now)
            }
        }
    }
    editDate?.let { d -> DayEditor(vm, d, onDismiss = { editDate = null }) }
    if (showSettings) CycleSettingsDialog(vm.profile, onDismiss = { showSettings = false; settingsTick++ })
}

/** 当前预测（用设置里的默认周期、避孕模式） */
@Composable
private fun rememberPrediction(profile: String, days: List<CycleDay>, today: LocalDate, tick: Int): Triple<CycleLogic.Stats, CycleLogic.Prediction?, Boolean> {
    val ctx = LocalContext.current
    return remember(days, today, tick) {
        val ps = CycleLogic.periods(days)
        val st = CycleLogic.stats(ps, Prefs.cycleDefaultLength(ctx, profile), Prefs.cycleDefaultPeriod(ctx, profile))
        Triple(st, CycleLogic.predict(ps, st, today), Prefs.cycleBirthControl(ctx, profile))
    }
}

// ---------------------------------------------------------------- 今天
@Composable
private fun TodayPage(vm: CycleViewModel, now: LocalDateTime, tick: Int, selected: LocalDate?, onEdit: (LocalDate) -> Unit, onSettings: () -> Unit, onPillTab: () -> Unit) {
    val ctx = LocalContext.current
    val profile = vm.profile
    val days by vm.days.collectAsState()
    val meds by vm.meds.collectAsState()
    val logs by vm.logs.collectAsState()
    val today = now.toLocalDate()
    val (st, pr, bc) = rememberPrediction(profile, days, today, tick)
    val todayRec = days.firstOrNull { it.date == today.toString() }
    val pill = meds.firstOrNull { it.id == Prefs.pillMedId(ctx, profile) }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(CycleLogic.todayText(pr, today, bc), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                        color = if (pr?.periodDay != null) PERIOD_RED else MaterialTheme.colorScheme.onSurface)
                    Text(if (st.learned) "平均周期 ${st.cycleLength} 天 · 经期 ${st.periodLength} 天（根据最近 ${st.cycles} 个周期）"
                        else "记录还不够，暂按 ${st.cycleLength} 天周期、${st.periodLength} 天经期估算", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (pr != null && !bc && pr.periodDay == null && pr.lateDays == 0)
                        Text("易孕期 ${pr.fertileStart.monthValue}/${pr.fertileStart.dayOfMonth}~${pr.fertileEnd.monthValue}/${pr.fertileEnd.dayOfMonth} · 排卵日约 ${pr.ovulation.monthValue}/${pr.ovulation.dayOfMonth}",
                            style = MaterialTheme.typography.bodySmall, color = FERTILE_GREEN)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (pr?.periodDay == null) Button(onClick = { vm.periodStartedToday() }) { Text("🩸 今天来了") }
                        OutlinedButton(onClick = { onEdit(today) }) { Text(if (todayRec == null) "记录今天" else "修改今天") }
                    }
                    todayRec?.let { Text("今天：" + CycleLogic.daySummary(it), style = MaterialTheme.typography.bodySmall) }
                    // 月历上选了别的日子：以前这里只有“修改今天”，选着 15 号点它改的却是今天（16 号），
                    // 看起来就像改 15 号、16 号跟着变。现在单独给选中的那天一个按钮，写清楚改的是哪天
                    if (selected != null && selected != today && !selected.isAfter(today)) {
                        val selRec = days.firstOrNull { it.date == selected.toString() }
                        val md = "${selected.monthValue}月${selected.dayOfMonth}日"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$md：" + (selRec?.let { CycleLogic.daySummary(it) }?.ifBlank { null } ?: "没有记录"),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onEdit(selected) }) { Text(if (selRec == null) "记录$md" else "修改$md") }
                        }
                    }
                    TextButton(onClick = onSettings) { Text(if (profile == CycleProfiles.MAIN) "⚙ 生理期设置（避孕模式、提醒、默认周期）" else "⚙ 生理期设置（避孕模式、默认周期）") }
                }
            }
        }
        item {
            if (pill != null) PillCard(vm, pill, logs, now, onMore = onPillTab)
            else TextButton(onClick = onPillTab, modifier = Modifier.padding(horizontal = 12.dp)) { Text("💊 设置避孕药打卡") }
        }
        item {
            Text("预测按平均周期估算，只供参考，不能作为避孕或诊断依据。功能设计参考开源项目 Mooneva Cycle。",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        }
    }
}

/** 避孕药卡片（参照 Mooneva 改进）：药板进度、一键打卡、连续天数、最近 7 天、昨天漏服提示 */
@Composable
private fun PillCard(vm: CycleViewModel, pill: Medication, logs: List<app.birthdayreminder.data.MedLog>, now: LocalDateTime, onMore: () -> Unit) {
    val today = now.toLocalDate()
    val plan = remember(pill.plan) { MedLogic.planFromJson(pill.plan) }
    val status = PillLogic.dayStatus(pill, logs, today, now)
    val streak = PillLogic.streak(pill, logs, today, now)
    val last7 = PillLogic.last7(pill, logs, today, now)
    val todayLog = logs.firstOrNull { it.id == MedLogic.doseKey(pill.id, today.toString(), plan.times.first().time) }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("💊 " + pill.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                PillLogic.packText(plan, today)?.let { Text(it, color = PILL_BLUE, fontWeight = FontWeight.SemiBold) }
            }
            when (status) {
                DayStatus.TAKEN -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("今天 " + (todayLog?.let { timeOf(it.actionAt) } ?: "") + " 已服用", color = PILL_BLUE, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.pillClear(pill, today) }) { Text("撤销") }
                }
                DayStatus.BREAK -> Text("停药期，今天不用吃", color = MaterialTheme.colorScheme.onSurfaceVariant)
                DayStatus.NONE -> Text("今天不用吃", color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (status == DayStatus.SKIPPED) "今天标记为没吃" else "今天还没吃（${plan.times.first().time}）", modifier = Modifier.weight(1f),
                        color = if (status == DayStatus.MISSED || status == DayStatus.SKIPPED) MISS_ORANGE else MaterialTheme.colorScheme.onSurface)
                    Button(onClick = { vm.pillTake(pill, today) }) { Text("已吃药") }
                }
            }
            Text(if (streak > 0) "已连续服药 $streak 天" else "还没有连续记录", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                last7.forEach { (d, s) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PillDot(s, 26)
                        Text(WEEK[d.dayOfWeek.value - 1].toString(), style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal)
                    }
                }
            }
            if (PillLogic.missedYesterday(pill, logs, today, now))
                Text("昨天漏服了？请按药品说明书处理，或咨询医生。", style = MaterialTheme.typography.bodySmall, color = MISS_ORANGE)
            TextButton(onClick = onMore) { Text("服药历史") }
        }
    }
}

private fun timeOf(ms: Long): String = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

/** 服药状态的小圆点：已服实心蓝、漏服/没吃橙色 ✕、停药虚线圈（这里用浅色描边圈）、未到空心 */
@Composable
private fun PillDot(s: DayStatus, size: Int) {
    val m = Modifier.size(size.dp).clip(CircleShape)
    when (s) {
        DayStatus.TAKEN -> Box(m.background(PILL_BLUE), contentAlignment = Alignment.Center) { Text("✓", color = Color.White, fontSize = (size / 2).sp) }
        DayStatus.MISSED, DayStatus.SKIPPED -> Box(m.background(MISS_ORANGE.copy(alpha = 0.2f)), contentAlignment = Alignment.Center) { Text("✕", color = MISS_ORANGE, fontSize = (size / 2).sp) }
        DayStatus.BREAK -> Box(m.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
        DayStatus.DUE, DayStatus.UPCOMING -> Box(m.border(1.5.dp, PILL_BLUE.copy(alpha = 0.6f), CircleShape))
        DayStatus.NONE -> Box(m)
    }
}

// ---------------------------------------------------------------- 月历（生理期 / 避孕药共用）
@Composable
private fun MonthNav(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) { Icon(Icons.Default.ChevronLeft, contentDescription = "上个月") }
        Text("${month.year}年${month.monthValue}月", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        IconButton(onClick = { onChange(month.plusMonths(1)) }) { Icon(Icons.Default.ChevronRight, contentDescription = "下个月") }
    }
}

@Composable
private fun MonthGrid(month: YearMonth, cell: @Composable (LocalDate) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        Row { WEEK.forEach { w -> Text(w.toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        val first = month.atDay(1)
        val lead = first.dayOfWeek.value - 1
        val total = lead + month.lengthOfMonth()
        for (r in 0 until (total + 6) / 7) {
            Row {
                for (c in 0 until 7) {
                    val idx = r * 7 + c - lead
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (idx in 0 until month.lengthOfMonth()) cell(first.plusDays(idx.toLong()))
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarPage(vm: CycleViewModel, today: LocalDate, tick: Int, onEdit: (LocalDate) -> Unit) {
    val ctx = LocalContext.current
    val profile = vm.profile
    val days by vm.days.collectAsState()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val marks = remember(days, month, tick) {
        CycleLogic.marks(days, month.atDay(1), month.atEndOfMonth(), today, Prefs.cycleBirthControl(ctx, profile), Prefs.cycleDefaultLength(ctx, profile), Prefs.cycleDefaultPeriod(ctx, profile))
    }
    val recorded = remember(days) { days.map { it.date }.toSet() }
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        MonthNav(month) { month = it }
        MonthGrid(month) { d ->
            val mk = marks[d] ?: Mark.NONE
            val base = Modifier.size(36.dp).clip(CircleShape).clickable { onEdit(d) }
            val m = when (mk) {
                Mark.PERIOD -> base.background(PERIOD_RED)
                Mark.PREDICTED -> base.border(1.5.dp, PERIOD_RED, CircleShape)
                Mark.OVULATION -> base.background(FERTILE_GREEN)
                Mark.FERTILE -> base.background(FERTILE_GREEN.copy(alpha = 0.18f))
                Mark.NONE -> base
            }
            Box(m, contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfMonth.toString(), fontWeight = if (d == today) FontWeight.Bold else FontWeight.Normal,
                        color = if (mk == Mark.PERIOD || mk == Mark.OVULATION) Color.White else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
                    if (d.toString() in recorded && mk != Mark.PERIOD) Box(Modifier.size(4.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(16.dp).horizontalScroll(rememberScrollState())) {
            Legend(Modifier.background(PERIOD_RED), "经期")
            Legend(Modifier.border(1.5.dp, PERIOD_RED, CircleShape), "预测经期")
            if (!Prefs.cycleBirthControl(ctx, profile)) {
                Legend(Modifier.background(FERTILE_GREEN.copy(alpha = 0.18f)), "易孕期")
                Legend(Modifier.background(FERTILE_GREEN), "排卵日")
            }
        }
        Text("点任意一天记录流量、症状、心情。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
    }
}

@Composable
private fun Legend(m: Modifier, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).clip(CircleShape).then(m))
        Text(" $text", style = MaterialTheme.typography.labelSmall)
    }
}

// ---------------------------------------------------------------- 趋势
@Composable
private fun TrendsPage(vm: CycleViewModel, today: LocalDate, tick: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val days by vm.days.collectAsState()
    val (st, _, _) = rememberPrediction(vm.profile, days, today, tick)
    val hist = remember(days) { CycleLogic.history(CycleLogic.periods(days)) }
    val syms = remember(days, today) { CycleLogic.symptomCounts(days, today.minusDays(89), today) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { Toast.makeText(ctx, vm.exportCsv(uri) ?: "已导出生理期记录", Toast.LENGTH_SHORT).show() }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("平均周期 ${st.cycleLength} 天 · 平均经期 ${st.periodLength} 天", fontWeight = FontWeight.SemiBold)
                    Text(if (st.learned) "根据最近 ${st.cycles} 个周期计算" else "记录还不够，用的是默认值", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { exporter.launch("生理期记录-$today.csv") }) { Text("导出 CSV（可给医生看）") }
                }
            }
        }
        if (syms.isNotEmpty()) item {
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("最近 90 天常见症状", fontWeight = FontWeight.SemiBold)
                    syms.take(6).forEach { (l, n) -> Text("$l  $n 天", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item { Text("周期历史", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 16.dp, top = 12.dp)) }
        if (hist.isEmpty()) item { Text("还没有经期记录", modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(hist) { h ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                Text("${h.start}", modifier = Modifier.weight(1f))
                Text("经期 ${h.periodLength} 天" + (h.cycleLength?.let { " · 周期 $it 天" } ?: " · 本次周期"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

// ---------------------------------------------------------------- 避孕药
@Composable
private fun PillPage(vm: CycleViewModel, now: LocalDateTime) {
    val ctx = LocalContext.current
    val profile = vm.profile
    val meds by vm.meds.collectAsState()
    val logs by vm.logs.collectAsState()
    var pillId by remember { mutableStateOf(Prefs.pillMedId(ctx, profile)) }
    val pill = meds.firstOrNull { it.id == pillId }
    var month by remember { mutableStateOf(YearMonth.from(now.toLocalDate())) }
    var editDay by remember { mutableStateOf<LocalDate?>(null) }
    Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(bottom = 100.dp)) {
        Text((if (profile == CycleProfiles.MAIN) "选哪种药作为避孕药（在“用药就医 → 药品”里添加" else "选哪种药作为避孕药（在隐私空间的“用药就医”里添加") +
            "，重复方式选“吃几天停几天”，比如吃 21 停 7）",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(horizontal = 16.dp).horizontalScroll(rememberScrollState())) {
            if (meds.isEmpty()) Text("还没有药品", color = MaterialTheme.colorScheme.onSurfaceVariant)
            meds.forEach { m -> FilterChip(selected = m.id == pillId, onClick = { pillId = m.id; Prefs.setPillMedId(ctx, m.id, profile) }, label = { Text(m.name) }) }
        }
        if (pill == null) return@Column
        Box(Modifier.padding(top = 8.dp)) { PillCard(vm, pill, logs, now, onMore = {}) }
        // 月历：每天的服药情况 + 本月服药率
        MonthNav(month) { month = it }
        val from = month.atDay(1); val to = minOf(month.atEndOfMonth(), now.toLocalDate())
        val ad = if (!to.isBefore(from)) MedLogic.adherence(listOf(pill), logs, from, to, now) else null
        Text("本月服药率：" + (ad?.rate?.let { "$it%（应服 ${ad.due} 次，漏服 ${ad.missed}、没吃 ${ad.skipped}）" } ?: "—"),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
        MonthGrid(month) { d ->
            Box(Modifier.clickable { editDay = d }, contentAlignment = Alignment.Center) {
                PillDot(PillLogic.dayStatus(pill, logs, d, now), 30)
                Text(d.dayOfMonth.toString(), fontSize = 11.sp,
                    color = if (PillLogic.dayStatus(pill, logs, d, now) == DayStatus.TAKEN) Color.Transparent else MaterialTheme.colorScheme.onSurface)
            }
        }
        Text("点某一天可以改成已服、没吃或清除，也能改服药时间。", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
    }
    val p = pill
    editDay?.let { d -> if (p != null) PillDayDialog(vm, p, logs, d, now, onChange = { editDay = it }, onDismiss = { editDay = null }) }
}

/** 某天的服药编辑：已服 / 没吃 / 清除、改时间、前一天 / 后一天 */
@Composable
private fun PillDayDialog(vm: CycleViewModel, pill: Medication, logs: List<app.birthdayreminder.data.MedLog>, d: LocalDate, now: LocalDateTime,
                          onChange: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val plan = MedLogic.planFromJson(pill.plan)
    val status = PillLogic.dayStatus(pill, logs, d, now)
    val log = logs.firstOrNull { it.id == MedLogic.doseKey(pill.id, d.toString(), plan.times.first().time) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${d.monthValue}月${d.dayOfMonth}日 周${WEEK[d.dayOfWeek.value - 1]}" + (PillLogic.packText(plan, d)?.let { " · $it" } ?: "")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(when (status) {
                    DayStatus.TAKEN -> "已服用" + (log?.let { "（${timeOf(it.actionAt)}）" } ?: "")
                    DayStatus.SKIPPED -> "标记为没吃"
                    DayStatus.MISSED -> "漏服"
                    DayStatus.BREAK -> "停药期"
                    DayStatus.NONE -> "这天不用吃"
                    else -> "还没记录"
                })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedButton(onClick = { vm.pillTake(pill, d) }) { Text("✓ 已服") }
                    OutlinedButton(onClick = { vm.pillMissed(pill, d) }) { Text("✕ 没吃") }
                    TextButton(onClick = { vm.pillClear(pill, d) }) { Text("清除") }
                }
                if (status == DayStatus.TAKEN) TextButton(onClick = {
                    val t = log?.actionAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() } ?: java.time.LocalTime.now()
                    TimePickerDialog(ctx, { _, h, m ->
                        vm.pillTakeAt(pill, d, d.atTime(h, m).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                    }, t.hour, t.minute, true).show()
                }) { Text("修改服药时间") }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        dismissButton = {
            Row {
                TextButton(onClick = { onChange(d.minusDays(1)) }) { Text("‹ 前一天") }
                TextButton(onClick = { onChange(d.plusDays(1)) }, enabled = d.isBefore(now.toLocalDate())) { Text("后一天 ›") }
            }
        }
    )
}

// ---------------------------------------------------------------- 记录某一天
@Composable
private fun DayEditor(vm: CycleViewModel, date: LocalDate, onDismiss: () -> Unit) {
    val days by vm.days.collectAsState()
    val old = remember(date) { days.firstOrNull { it.date == date.toString() } }
    var flow by remember(date) { mutableIntStateOf(old?.flow ?: 0) }
    val syms = remember(date) { mutableStateListOf<String>().apply { addAll(MedicalLogic.listFromJson(old?.symptoms)) } }
    var mood by remember(date) { mutableStateOf(old?.mood ?: "") }
    var discharge by remember(date) { mutableStateOf(old?.discharge ?: "") }
    var note by remember(date) { mutableStateOf(old?.note ?: "") }
    val ctx = LocalContext.current
    val period = CycleLogic.isPeriod(flow)
    val wasPeriod = old != null && CycleLogic.isPeriod(old.flow)
    // 新标成经期、又是这次经期第一天：后面会自动补几天
    val willFill = remember(days, date, period) {
        if (!period || wasPeriod) 0 else CycleLogic.autoFillDates(days, date, Prefs.cycleDefaultPeriod(ctx, vm.profile)).size
    }
    // 这天之后紧接着还有几天经期（可以一键在这天结束）
    val after = remember(days, date) { if (wasPeriod) CycleLogic.datesAfterEnd(days, date).size else 0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${date.monthValue}月${date.dayOfMonth}日 周${WEEK[date.dayOfWeek.value - 1]}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // 是不是经期：直接点，不用选流量
                FilterChip(selected = period, onClick = { flow = if (period) 0 else CycleLogic.PERIOD_ONLY }, label = { Text("🩸 是经期") })
                if (willFill > 0) Text("后面 $willFill 天会自动标成经期（按设置的经期天数），之后可以一天天改。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (after > 0) TextButton(onClick = { vm.endPeriodOn(date) }) { Text("经期到这天结束（取消后面 $after 天）") }
                Text("流量（选填；点滴不算经期）", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    // 再点一次已选的流量就取消：经期的退回“只标了经期”，点滴退回“无”
                    CycleLogic.FLOWS.withIndex().drop(1).forEach { (i, l) ->
                        FilterChip(selected = flow == i, onClick = {
                            flow = if (flow != i) i else if (CycleLogic.isPeriod(i)) CycleLogic.PERIOD_ONLY else 0
                        }, label = { Text(l) })
                    }
                }
                Text("症状", style = MaterialTheme.typography.labelLarge)
                ChipFlow(CycleLogic.SYMPTOMS, { it in syms }) { k -> if (k in syms) syms.remove(k) else syms.add(k) }
                Text("心情", style = MaterialTheme.typography.labelLarge)
                ChipFlow(CycleLogic.MOODS, { it == mood }) { k -> mood = if (mood == k) "" else k }
                Text("分泌物", style = MaterialTheme.typography.labelLarge)
                ChipFlow(CycleLogic.DISCHARGES, { it == discharge }) { k -> discharge = if (discharge == k) "" else k }
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("备注（选填）") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = {
                vm.saveDay(CycleDay(date.toString(), flow, MedicalLogic.listToJson(syms.toList()), mood, discharge, note.trim()))
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

/** 一排排可以换行的小选项（每行最多 4 个） */
@Composable
private fun ChipFlow(items: List<Pair<String, String>>, selected: (String) -> Boolean, onClick: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                row.forEach { (k, l) -> FilterChip(selected = selected(k), onClick = { onClick(k) }, label = { Text(l) }) }
            }
        }
    }
}

// ---------------------------------------------------------------- 设置
@Composable
private fun CycleSettingsDialog(profile: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    // 隐私空间那份不发通知（通知会在锁屏、通知栏露出来），所以没有提醒设置
    val canRemind = profile == CycleProfiles.MAIN
    var bc by remember { mutableStateOf(Prefs.cycleBirthControl(ctx, profile)) }
    var len by remember { mutableStateOf(Prefs.cycleDefaultLength(ctx, profile).toString()) }
    var per by remember { mutableStateOf(Prefs.cycleDefaultPeriod(ctx, profile).toString()) }
    var before by remember { mutableIntStateOf(Prefs.cycleRemindBefore(ctx)) }
    var pms by remember { mutableStateOf(Prefs.cycleRemindPms(ctx)) }
    var ov by remember { mutableStateOf(Prefs.cycleRemindOvulation(ctx)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("生理期设置") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SwitchLine("避孕模式", "不显示、不提醒易孕期和排卵日（在吃避孕药时用）", bc) { v -> bc = v }
                HorizontalDivider()
                Text("记录不够时的默认值（记了几次以后会自动按你的实际情况算）。" +
                    "经期天数也用来自动标记：标了一次经期的第一天，后面就按这个天数标成经期，可以再一天天改。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = len, onValueChange = { v -> len = v.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                        label = { Text("周期天数") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = per, onValueChange = { v -> per = v.filter { c -> c.isDigit() }.take(2) }, singleLine = true,
                        label = { Text("经期天数") }, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
                if (!canRemind) Text("隐私空间里的生理期不发提醒通知，免得在锁屏和通知栏露出来。", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else {
                Text("提醒（早上 9 点）", style = MaterialTheme.typography.labelLarge)
                Text("经期前几天提醒", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf(0, 1, 2, 3, 5).forEach { n -> FilterChip(selected = before == n, onClick = { before = n }, label = { Text(if (n == 0) "不提醒" else "$n 天") }) }
                }
                SwitchLine("经前期提醒", "经期前 5 天", pms) { v -> pms = v }
                SwitchLine("排卵日提醒", if (bc) "避孕模式下不提醒" else "按平均周期估算，仅供参考", ov) { v -> ov = v }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                Prefs.setCycleBirthControl(ctx, bc, profile)
                len.toIntOrNull()?.let { Prefs.setCycleDefaultLength(ctx, it, profile) }
                per.toIntOrNull()?.let { Prefs.setCycleDefaultPeriod(ctx, it, profile) }
                if (canRemind) {
                    Prefs.setCycleRemindBefore(ctx, before); Prefs.setCycleRemindPms(ctx, pms); Prefs.setCycleRemindOvulation(ctx, ov)
                    CycleScheduler.reschedule(ctx)
                }
                onDismiss()
            }) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun SwitchLine(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
