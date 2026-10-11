package app.birthdayreminder.ui.health

import app.birthdayreminder.privacy.SecureWindow
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.size
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.outlined.EmojiEvents
import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.TgSectionTitle
import app.birthdayreminder.ui.common.SummaryCard
import app.birthdayreminder.ui.common.DateBadge
import app.birthdayreminder.ui.common.TgAvatar
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardListItem
import app.birthdayreminder.ui.common.SoftCard
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birthdayreminder.health.HealthAutoSyncRow
import app.birthdayreminder.health.HealthConnectSource
import app.birthdayreminder.health.HealthSyncButton
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.FitnessLog
import app.birthdayreminder.domain.FitnessFields
import app.birthdayreminder.domain.FitnessInsights
import app.birthdayreminder.domain.FitnessTemplate
import app.birthdayreminder.domain.FitnessTemplates
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.domain.weekText
import kotlinx.coroutines.launch
import java.time.LocalDate

/** 健身数据列表（“全部”页的标签）：上面是最近体重和 7 天合计，下面是每天的记录 */
@Composable
fun FitnessListContent(onEdit: (String) -> Unit, vm: FitnessViewModel = viewModel()) {
    val all by vm.all.collectAsState()
    val today = remember { LocalDate.now() }
    // 项目设置改了（顺序、隐藏、自定义）就重新算
    val fieldsVer by FitnessFieldStore.version
    var showFieldsSetting by remember { mutableStateOf(false) }
    val stats = remember(all, fieldsVer) { FitnessFields.stats(all, today) }
    val series = remember(all) { FitnessInsights.weightSeries(all, today) }
    val bests = remember(all, fieldsVer) { FitnessInsights.bests(all) }
    val bestSets = remember(all, fieldsVer) { FitnessInsights.bestSets(all).associateBy { it.field.key } }
    val streak = remember(all, fieldsVer) { FitnessInsights.streak(all, today) }
    if (showFieldsSetting) FitnessFieldsDialog(onDismiss = { showFieldsSetting = false })
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        // ---------------- 身体数据卡片（折叠）：每项最近一次的数值、BMI、从 Health Connect 同步最近 7 天 ----------------
        item(key = "bodyCard") {
            val listCtx = androidx.compose.ui.platform.LocalContext.current
            var open by remember { mutableStateOf(false) }
            val latest = remember(all, fieldsVer) {
                FitnessFields.BODY_EXTRA.mapNotNull { f -> all.filter { it.metrics[f.key] != null }.maxByOrNull { it.date }?.let { Triple(f, it.metrics.getValue(f.key), it.date) } }
            }
            val bmi = remember(all) { FitnessFields.bmi(stats.latestWeight, FitnessFields.latestHeight(all)) }
            SoftCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { open = !open }) {
                        Text((if (open) "▾ " else "▸ ") + "身体数据", fontWeight = FontWeight.SemiBold)
                        Text("  " + (if (latest.isEmpty()) "身高、各种围度、心率、睡眠、血压、血氧" else "已记 ${latest.size} 项") + (bmi?.let { " · BMI $it" } ?: ""),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (open) {
                        if (latest.isEmpty()) Text("还没有记录。在每天的健身记录里展开“身体数据”填写，或从 Health Connect 同步。",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        latest.forEach { (f, v, d) ->
                            Row {
                                Text(f.label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                Text(FitnessFields.format(f, v) + " · " + shortDate(d, today), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HealthAutoSyncRow()
                        HealthSyncButton("立即同步最近 7 天") {
                            val r = HealthConnectSource.import(listCtx, today.minusDays(6), today)
                            when {
                                r.empty -> "Health Connect 里最近 7 天没有数据（要先让手环 / 手表的 App 把数据写进 Health Connect）"
                                r.filled == 0 -> "最近 7 天的数据都已经有了"
                                else -> "已同步 ${r.days} 天、${r.filled} 项（只填空着的，不覆盖手动填的）"
                            }
                        }
                    }
                }
            }
        }
        item(key = "fieldsSetting") {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showFieldsSetting = true }) { Text("⚙ 项目设置（顺序 / 显示 / 自定义）") }
            }
        }
        if (all.isEmpty()) {
            item {
                Text("还没有健身记录，点右下角“+”记今天的数据", modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            // ---------------- 仪表盘：最近体重、连续打卡（摘要卡片）→ 最近 7 天 → 体重曲线 → 个人最佳 ----------------
            item(key = "summary") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    val wField = FitnessFields.BY_KEY.getValue("weight")
                    SummaryCard(
                        label = "最近体重",
                        value = stats.latestWeight?.let { FitnessFields.inputText(wField, it) } ?: "—",
                        unit = if (stats.latestWeight != null) "kg" else "",
                        hint = stats.latestWeightDate?.let { d -> shortDate(d, today) + (stats.weightChange?.let { c ->
                            when { c > 0 -> " · +$c"; c < 0 -> " · $c"; else -> " · 持平" } } ?: "") } ?: "还没有记录",
                        modifier = Modifier.weight(1.3f)
                    )
                    SummaryCard(
                        label = "连续打卡",
                        value = streak.current.toString(), unit = "天",
                        hint = when {
                            streak.current > 0 && !streak.trainedToday -> "今天还没练"
                            streak.longest > streak.current -> "最长 ${streak.longest} 天"
                            streak.current == 0 -> "记录训练后开始计算"
                            else -> "保持住"
                        },
                        accent = if (streak.current > 0) LiColors.done else null,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            item(key = "week") {
                TgSectionTitle("最近 7 天")
                if (stats.weekTotals.isEmpty()) {
                    Text("还没有训练记录", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
                } else {
                    // 每个项目一块小卡片，两列排
                    Column(modifier = Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        stats.weekTotals.chunked(2).forEach { pair ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                pair.forEach { (f, v) ->
                                    SummaryCard(label = f.label, value = FitnessFields.inputText(f, v), unit = f.unit,
                                        hint = stats.weekSets[f.key]?.let { "${it.toLong()} 组" }, modifier = Modifier.weight(1f))
                                }
                                if (pair.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
            // 体重曲线
            item(key = "chart") {
                TgSectionTitle("体重")
                SoftCard(modifier = Modifier.fillMaxWidth()) {
                    WeightChart(series, modifier = Modifier.padding(16.dp))
                }
            }
            // 个人最佳：各项目单日最多（有分组记录的再加单组最多）
            if (bests.isNotEmpty()) {
                item(key = "bests") {
                    TgSectionTitle("个人最佳")
                    SoftCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            bests.forEach { b ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Outlined.EmojiEvents, contentDescription = null, tint = LiColors.holiday, modifier = Modifier.size(20.dp))
                                    Text(b.field.label, fontSize = 15.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(FitnessFields.format(b.field, b.value) + (if (b.field.kind == FitnessFields.Kind.COUNT) " ${b.field.unit}" else ""),
                                            fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                                        Text("单日 · " + shortDate(b.date, today) + (bestSets[b.field.key]?.let { bs -> " · 单组 ${bs.value.toLong()}" } ?: ""),
                                            fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item(key = "logsTitle") { TgSectionTitle("每天的记录") }
        }
        items(all, key = { it.date }) { log ->
            CardListItem(
                leadingContent = { DateBadge(log.date.takeLast(2).trimStart('0')) },
                overlineContent = { Text(shortDate(log.date, today)) },
                headlineContent = { Text(FitnessFields.summary(log)) },
                supportingContent = if (log.note.isNotBlank()) { { Text(log.note, maxLines = 1) } } else null,
                modifier = Modifier.clickable { onEdit(log.date) }
            )
        }
    }
}

/** 记某一天的健身数据：一天一条，已经记过的会自动填上，改完再保存 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessEditScreen(initialDate: LocalDate, onBack: () -> Unit, initialPrivate: Boolean = false, vm: FitnessViewModel = viewModel()) {
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(initialDate) }
    var originalDate by remember { mutableStateOf<String?>(null) }    // 编辑的是哪一天原有的记录
    val inputs = remember { mutableStateMapOf<String, String>() }
    var bodyOpen by remember { mutableStateOf(false) }       // 身体数据默认折叠
    // 只显示常用的训练项目：这天填了的、最近 30 天练过的（新用户先显示前 3 个）；其余点“添加训练项目”再加
    val commonKeys = remember { mutableStateListOf<String>() }
    val addedKeys = remember { mutableStateListOf<String>() }
    var showAddField by remember { mutableStateOf(false) }
    var latestHeight by remember { mutableStateOf<Double?>(null) }
    // 分组记录：哪些次数项目打开了“分组”，每项现在显示几行
    val perSet = remember { mutableStateListOf<String>() }
    val setRows = remember { mutableStateMapOf<String, Int>() }
    var note by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(initialPrivate) }      // 在隐私空间里新建的就是私密的；编辑已有的保持原样
    SecureWindow(isPrivate)                                    // 私密内容不让截屏
    var error by remember { mutableStateOf<String?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    // 常用训练组合
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var templates by remember { mutableStateOf(FitnessTemplates.fromJson(Prefs.fitnessTemplatesJson(ctx))) }
    fun saveTemplates(l: List<FitnessTemplate>) { templates = l; Prefs.setFitnessTemplatesJson(ctx, FitnessTemplates.toJson(l)) }
    var editTemplates by remember { mutableStateOf(false) }      // 删除模式
    var showSaveTemplate by remember { mutableStateOf(false) }
    var templateName by remember { mutableStateOf("") }

    // 项目设置改了就刷新（顺序、显示/隐藏、自定义项目）
    val fieldsVer by FitnessFieldStore.version
    var showFieldsSetting by remember { mutableStateOf(false) }
    // 这天原来的数据（隐藏的项目也要保留）
    var originalMetrics by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    /** 记录页显示的项目：没隐藏的 + 这天已经有数据的隐藏项目（免得看不到、保存时丢掉） */
    val shownFields = remember(fieldsVer, originalMetrics) {
        FitnessFields.ALL.filter { f -> f.visible || originalMetrics.containsKey(f.key) || originalMetrics.containsKey(FitnessFields.setsKey(f)) }
    }

    /** 分组模式下这项填的各组次数（按行顺序，空的为 null）和极限组；填错的返回提示 */
    fun readParts(f: FitnessFields.Field): Triple<List<Double?>, Double?, String?> {
        val rows = setRows[f.key] ?: 1
        val sets = ArrayList<Double?>()
        for (i in 1..rows) {
            when (val p = FitnessFields.parse(f, inputs[FitnessFields.groupKey(f, i)] ?: "")) {
                is FitnessFields.Parsed.Bad -> return Triple(emptyList(), null, "${f.label}第 $i 组：" + p.message.removePrefix(f.label))
                is FitnessFields.Parsed.Ok -> sets.add(p.value)
            }
        }
        val mx = when (val p = FitnessFields.parse(f, inputs[FitnessFields.maxKey(f)] ?: "")) {
            is FitnessFields.Parsed.Bad -> return Triple(emptyList(), null, "${f.label}极限组：" + p.message.removePrefix(f.label))
            is FitnessFields.Parsed.Ok -> p.value
        }
        return Triple(sets, mx, null)
    }

    /** 打开分组：已经填的总次数当作第 1 组（如果填了组数，就平均分到各组，方便改） */
    fun openParts(f: FitnessFields.Field) {
        val total = inputs[f.key]?.toDoubleOrNull()?.toLong() ?: 0L
        val n = inputs[FitnessFields.setsKey(f)]?.toIntOrNull()?.coerceIn(1, FitnessFields.MAX_SET_ROWS) ?: 1
        if (total > 0) {
            val each = total / n; val rest = total % n
            for (i in 1..n) inputs[FitnessFields.groupKey(f, i)] = (each + if (i <= rest) 1 else 0).toString()
        }
        setRows[f.key] = if (total > 0) n else 1
        if (f.key !in perSet) perSet.add(f.key)
    }

    /** 收起分组：把算好的总次数、组数填回普通输入框 */
    fun closeParts(f: FitnessFields.Field) {
        val (sets, mx, err) = readParts(f)
        if (err == null) {
            val m = FitnessFields.compose(f, sets, mx)
            inputs[f.key] = m[f.key]?.toLong()?.toString() ?: ""
            inputs[FitnessFields.setsKey(f)] = m[FitnessFields.setsKey(f)]?.toLong()?.toString() ?: ""
        }
        perSet.remove(f.key)
    }

    /** 当前输入框里能正确解析的训练数据（存组合用；体重不算，填错的项跳过） */
    fun currentCounts(): Map<String, Double> {
        val m = LinkedHashMap<String, Double>()
        for (f in shownFields) {
            if (f.kind == FitnessFields.Kind.WEIGHT) continue
            if (f.key in perSet) {
                val (sets, mx, err) = readParts(f)
                if (err == null) m.putAll(FitnessFields.compose(f, sets, mx))
                continue
            }
            (FitnessFields.parse(f, inputs[f.key] ?: "") as? FitnessFields.Parsed.Ok)?.value?.let { if (it > 0) m[f.key] = it }
            if (f.hasSets) (FitnessFields.parseSets(f, inputs[FitnessFields.setsKey(f)] ?: "") as? FitnessFields.Parsed.Ok)?.value?.let { m[FitnessFields.setsKey(f)] = it }
        }
        return m
    }

    /** 换到某一天：那天有记录就把记录填进来，没有就清空 */
    suspend fun load(d: LocalDate) {
        val log = vm.get(d.toString())
        latestHeight = FitnessFields.latestHeight(vm.all.value)
        run {
            val from = d.minusDays(30).toString(); val to = d.toString()
            val recent = vm.all.value.filter { it.date in from..to }
            val training = FitnessFields.ALL.filter { !it.isBody }
            val used = training.filter { f -> recent.any { l -> l.metrics.keys.any { k -> k == f.key || k.startsWith(f.key + "#") || k == FitnessFields.setsKey(f) } } }.map { it.key }
            commonKeys.clear()
            commonKeys.addAll(if (used.isNotEmpty()) used else training.filter { it.visible }.take(3).map { it.key })
            addedKeys.clear(); showAddField = false
        }
        inputs.clear()
        originalMetrics = log?.metrics ?: emptyMap()
        perSet.clear(); setRows.clear()
        FitnessFields.ALL.forEach { f ->
            inputs[f.key] = FitnessFields.inputText(f, log?.metrics?.get(f.key))
            if (f.hasSets) inputs[FitnessFields.setsKey(f)] = log?.metrics?.get(FitnessFields.setsKey(f))?.toLong()?.toString() ?: ""
            val mt = log?.metrics ?: emptyMap()
            if (f.hasSets && FitnessFields.hasParts(f, mt)) {
                val sets = FitnessFields.setsOf(f, mt)
                sets.forEachIndexed { i, v -> inputs[FitnessFields.groupKey(f, i + 1)] = v.toLong().toString() }
                FitnessFields.maxSetOf(f, mt)?.let { inputs[FitnessFields.maxKey(f)] = it.toLong().toString() }
                setRows[f.key] = maxOf(1, sets.size)
                perSet.add(f.key)
            }
        }
        note = log?.note ?: ""
        isPrivate = log?.isPrivate ?: initialPrivate      // 这天还没有记录：按入口决定（隐私空间里新建的是私密的）
        originalDate = log?.date
        error = null
    }
    LaunchedEffect(Unit) { load(initialDate) }

    fun save() {
        // 先放进原来的数据里“这页没显示的项目”（比如删掉的自定义项目），保证不会因为没显示就被清掉
        val shownKeys = shownFields.flatMap { FitnessFields.keysOf(it) }.toSet()
        val metrics = LinkedHashMap<String, Double>(originalMetrics.filterKeys { it !in shownKeys })
        for (f in shownFields) {
            // 分组模式：各组 + 极限组，总次数和组数自动算
            if (f.key in perSet) {
                val (sets, mx, err) = readParts(f)
                if (err != null) { error = err; return }
                metrics.putAll(FitnessFields.compose(f, sets, mx))
                continue
            }
            when (val p = FitnessFields.parse(f, inputs[f.key] ?: "")) {
                is FitnessFields.Parsed.Bad -> { error = p.message; return }
                is FitnessFields.Parsed.Ok -> p.value?.let { metrics[f.key] = it }
            }
            if (f.hasSets) {
                when (val p = FitnessFields.parseSets(f, inputs[FitnessFields.setsKey(f)] ?: "")) {
                    is FitnessFields.Parsed.Bad -> { error = p.message; return }
                    is FitnessFields.Parsed.Ok -> p.value?.let { metrics[FitnessFields.setsKey(f)] = it }
                }
            }
        }
        if (metrics.isEmpty() && note.isBlank()) { error = "至少填一项"; return }
        error = null
        vm.save(FitnessLog(date.toString(), metrics, note.trim().take(200), isPrivate = isPrivate), originalDate, onBack)
    }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(if (originalDate != null) "编辑健身数据" else "记健身数据") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            actions = { TextButton(onClick = { save() }) { Text("保存") } }
        )
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(onClick = { showDate = true }, modifier = Modifier.fillMaxWidth()) { Text(dateLabel(date)) }
            if (originalDate != null) {
                Text("这天已经记过，改完点保存会更新这条记录", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 常用组合：点一下把组合里的项目填进下面的输入框（体重不动）
            run {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("常用组合", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    if (templates.isNotEmpty()) TextButton(onClick = { editTemplates = !editTemplates }) { Text(if (editTemplates) "完成" else "删除组合") }
                    TextButton(onClick = {
                        if (currentCounts().isEmpty()) error = "先在下面填好训练次数，再存为组合"
                        else { templateName = ""; showSaveTemplate = true }
                    }) { Text("存为组合") }
                }
                if (templates.isEmpty()) {
                    Text("常做的训练可以存成组合，下次一键填入", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        templates.forEach { t ->
                            AssistChip(
                                onClick = {
                                    if (editTemplates) saveTemplates(FitnessTemplates.remove(templates, t.name))
                                    else {
                                        val filled = FitnessTemplates.applyTo(inputs.toMap(), t)
                                        filled.forEach { (k, v) -> inputs[k] = v }
                                        for (f in FitnessFields.COUNT_FIELDS) if (FitnessFields.hasParts(f, t.metrics)) {
                                            setRows[f.key] = maxOf(1, FitnessFields.setsOf(f, t.metrics).size)
                                            if (f.key !in perSet) perSet.add(f.key)
                                        }
                                        error = null
                                    }
                                },
                                label = { Text(if (editTemplates) "✕ ${t.name}" else t.name) }
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("训练", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showFieldsSetting = true }) { Text("项目设置") }
            }
            shownFields.forEach { f ->
                if (f.kind == FitnessFields.Kind.BODY) return@forEach      // 身体数据收在下面的折叠区
                // 训练项目：常用的、这次加的、已经填了的才显示（体重一直显示）
                val active = f.kind == FitnessFields.Kind.WEIGHT || f.key in commonKeys || f.key in addedKeys || f.key in perSet ||
                    (inputs[f.key] ?: "").isNotBlank() || (inputs[FitnessFields.setsKey(f)] ?: "").isNotBlank()
                if (!active) return@forEach
                if (f.hasSets && f.key in perSet) {
                    PartsEditor(f, inputs, setRows, onClose = { closeParts(f) }, readParts = { readParts(f) })
                    return@forEach
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(f.label + (if (!f.visible) "（已隐藏）" else ""), modifier = Modifier.weight(1f))
                    OutlinedTextField(
                        value = inputs[f.key] ?: "",
                        onValueChange = { v -> inputs[f.key] = v.filter { it.isDigit() || (f.decimal && (it == '.' || it == '，' || it == '。')) }.take(9) },
                        singleLine = true,
                        suffix = { Text(f.unit) },
                        placeholder = { Text(when (f.kind) {
                            FitnessFields.Kind.WEIGHT -> "如 70.5"
                            FitnessFields.Kind.DISTANCE -> "如 5.2"
                            FitnessFields.Kind.TIME -> "分钟"
                            else -> "0"
                        }) },
                        keyboardOptions = KeyboardOptions(keyboardType = if (f.decimal) KeyboardType.Decimal else KeyboardType.Number),
                        modifier = Modifier.weight(if (f.hasSets) 1f else 1.7f)
                    )
                    if (f.hasSets) {
                        val sk = FitnessFields.setsKey(f)
                        OutlinedTextField(
                            value = inputs[sk] ?: "",
                            onValueChange = { v -> inputs[sk] = v.filter { it.isDigit() }.take(3) },
                            singleLine = true,
                            suffix = { Text("组") },
                            placeholder = { Text("-") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(0.7f)
                        )
                        TextButton(onClick = { openParts(f) }, contentPadding = PaddingValues(horizontal = 4.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)) { Text("分组", fontSize = 13.sp) }
                    }
                }
            }
            // 没显示的训练项目：点“添加训练项目”展开，点一下就加到上面
            val hiddenFields = shownFields.filter { f ->
                !f.isBody && f.key !in commonKeys && f.key !in addedKeys && f.key !in perSet &&
                    (inputs[f.key] ?: "").isBlank() && (inputs[FitnessFields.setsKey(f)] ?: "").isBlank()
            }
            if (hiddenFields.isNotEmpty()) {
                TextButton(onClick = { showAddField = !showAddField }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(if (showAddField) "收起" else "＋ 添加训练项目（还有 ${hiddenFields.size} 项）")
                }
                if (showAddField) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        hiddenFields.forEach { f -> FilterChip(selected = false, onClick = { addedKeys.add(f.key) }, label = { Text(f.label) }) }
                    }
                }
            }
            OutlinedTextField(value = note, onValueChange = { note = it.take(200) }, label = { Text("备注（选填）") },
                modifier = Modifier.fillMaxWidth())
            // ---------------- 身体数据（折叠）：身高、三围、体脂、心率、睡眠、血压、血氧 ----------------
            val bodyFields = shownFields.filter { it.kind == FitnessFields.Kind.BODY }
            if (bodyFields.isNotEmpty()) {
                val filledBody = bodyFields.count { (inputs[it.key] ?: "").isNotBlank() }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { bodyOpen = !bodyOpen }.padding(vertical = 6.dp)) {
                    Text((if (bodyOpen) "▾ " else "▸ ") + "身体数据", fontWeight = FontWeight.SemiBold)
                    Text("  身高、围度、心率、睡眠…" + (if (filledBody > 0) "（已填 $filledBody 项）" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (bodyOpen) {
                    bodyFields.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(f.label + (if (!f.visible) "（已隐藏）" else ""), modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = inputs[f.key] ?: "",
                                onValueChange = { v -> inputs[f.key] = v.filter { it.isDigit() || (f.decimal && (it == '.' || it == '，' || it == '。')) }.take(7) },
                                singleLine = true, suffix = { Text(f.unit) },
                                keyboardOptions = KeyboardOptions(keyboardType = if (f.decimal) KeyboardType.Decimal else KeyboardType.Number),
                                modifier = Modifier.weight(1.7f)
                            )
                        }
                    }
                    // BMI：用这页填的体重和身高（没填身高就用最近一次记的）
                    val w = inputs["weight"]?.toDoubleOrNull()
                    val h = inputs["height"]?.toDoubleOrNull() ?: latestHeight
                    FitnessFields.bmi(w, h)?.let { Text("BMI $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    // 从 Health Connect 同步这一天：只填空着的输入框，保存后才生效
                    HealthSyncButton("从 Health Connect 同步这一天") {
                        val got = HealthConnectSource.readDay(ctx, date)
                        var n = 0
                        for ((k, v) in got) {
                            val f = FitnessFields.BY_KEY[k] ?: continue
                            if ((inputs[k] ?: "").isBlank()) { inputs[k] = FitnessFields.inputText(f, v); n++ }
                        }
                        if (got.isEmpty()) "Health Connect 里这一天没有数据" else if (n == 0) "这一天的数据都已经填了" else "填入了 $n 项，确认后点保存"
                    }
                    Text("心率、睡眠、血压、血氧等可以由手环 / 手表的 App 写进 Health Connect，再同步到这里。只填空着的项，不覆盖你手动填的。",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (originalDate != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("删除这天的记录", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    if (showDate) RecordDatePicker(date, onPick = { d -> date = d; scope.launch { load(d) } }, onDismiss = { showDate = false })
    if (showFieldsSetting) FitnessFieldsDialog(onDismiss = { showFieldsSetting = false })

    if (showSaveTemplate) {
        val counts = currentCounts()
        AlertDialog(
            onDismissRequest = { showSaveTemplate = false },
            title = { Text("存为常用组合") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(FitnessTemplates.describe(FitnessTemplate("", counts)), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = templateName, onValueChange = { templateName = it.take(12) }, singleLine = true,
                        label = { Text("组合名字，比如：上肢日") })
                    Text("体重每天不同，不会存进组合；同名的组合会被覆盖", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val t = FitnessTemplates.fromMetrics(templateName, counts)
                    if (t != null) { saveTemplates(FitnessTemplates.upsert(templates, t)); showSaveTemplate = false }
                }, enabled = templateName.isNotBlank()) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showSaveTemplate = false }) { Text("取消") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除记录") }, text = { Text("确定删除 ${dateLabel(date)} 的健身数据吗？") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; originalDate?.let { vm.delete(it, onBack) } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

/**
 * 一个次数项目的分组记录：每组一个小输入框（“＋组”加一组），下面单独一栏“极限组”。
 * 上面显示自动算出的总次数和组数；“收起”回到只填总数的样子。
 */
@Composable
private fun PartsEditor(
    f: FitnessFields.Field,
    inputs: androidx.compose.runtime.snapshots.SnapshotStateMap<String, String>,
    setRows: androidx.compose.runtime.snapshots.SnapshotStateMap<String, Int>,
    onClose: () -> Unit,
    readParts: () -> Triple<List<Double?>, Double?, String?>
) {
    val rows = setRows[f.key] ?: 1
    val (sets, mx, err) = readParts()
    val composed = if (err == null) FitnessFields.compose(f, sets, mx) else emptyMap()
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(f.label + (if (!f.visible) "（已隐藏）" else ""), fontWeight = FontWeight.SemiBold)
                Text(
                    if (composed.isEmpty()) "  分组记录" else "  共 ${composed[f.key]?.toLong() ?: 0} ${f.unit}" +
                        (composed[FitnessFields.setsKey(f)]?.let { " · ${it.toLong()} 组" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f)
                )
                TextButton(onClick = onClose, contentPadding = PaddingValues(horizontal = 6.dp)) { Text("收起") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.horizontalScroll(rememberScrollState())) {
                for (i in 1..rows) {
                    val k = FitnessFields.groupKey(f, i)
                    OutlinedTextField(
                        value = inputs[k] ?: "", onValueChange = { v -> inputs[k] = v.filter { it.isDigit() }.take(5) },
                        singleLine = true, label = { Text("第${i}组") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(76.dp)
                    )
                }
                if (rows < FitnessFields.MAX_SET_ROWS) TextButton(onClick = { setRows[f.key] = rows + 1 }) { Text("＋组") }
                if (rows > 1) TextButton(onClick = { inputs.remove(FitnessFields.groupKey(f, rows)); setRows[f.key] = rows - 1 }) { Text("－组") }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val k = FitnessFields.maxKey(f)
                OutlinedTextField(
                    value = inputs[k] ?: "", onValueChange = { v -> inputs[k] = v.filter { it.isDigit() }.take(5) },
                    singleLine = true, label = { Text("极限组") }, suffix = { Text(f.unit) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(130.dp)
                )
                Text("冲极限的那一组单独记，算进总次数，不算组数", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

