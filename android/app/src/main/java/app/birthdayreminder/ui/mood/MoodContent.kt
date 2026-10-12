package app.birthdayreminder.ui.mood

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.domain.MoodLogic
import app.birthdayreminder.ui.common.SoftCard
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

private fun hm(ms: Long) = Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalTime().let { "%02d:%02d".format(it.hour, it.minute) }

/**
 * 日记标签顶上的“此刻心情”：五个表情点一下就记（记在此刻），下面列出今天记过的，
 * 刚记的那条几秒内可以撤销；右边“心情统计”打开统计页。
 */
@Composable
fun MoodQuickBar(vm: MoodViewModel = viewModel()) {
    val all by vm.all.collectAsState()
    val today = LocalDate.now().toString()
    val todayLogs = remember(all, today) { all.filter { it.date == today }.sortedBy { it.at } }
    var lastId by remember { mutableStateOf<String?>(null) }
    var showStats by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<app.birthdayreminder.data.MoodLog?>(null) }
    LaunchedEffect(lastId) { if (lastId != null) { delay(5000); lastId = null } }
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("此刻心情", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                TextButton(onClick = { showStats = true }) { Text("心情统计 ›") }
            }
            // 从差到好排，像打分一样从左往右
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                MoodLogic.LEVELS.reversed().forEach { l ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { lastId = vm.record(l.value) }.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Text(l.emoji, fontSize = 30.sp)
                        Text(l.label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (lastId != null) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("已记录", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { lastId?.let { vm.delete(it) }; lastId = null }) { Text("撤销") }
            }
            if (todayLogs.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                Text("今天", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 点一下可以删掉记错的
                todayLogs.forEach { m -> Text(MoodLogic.level(m.level).emoji + " " + hm(m.at), fontSize = 13.sp, modifier = Modifier.clickable { deleting = m }) }
            }
        }
    }
    deleting?.let { m ->
        AlertDialog(onDismissRequest = { deleting = null },
            title = { Text("删除 ${hm(m.at)} 的心情记录？") },
            confirmButton = { TextButton(onClick = { vm.delete(m.id); deleting = null }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } })
    }
    if (showStats) MoodStatsDialog(vm, onDismiss = { showStats = false })
}

/** 心情统计（全屏）：时间范围 + 心情起伏 + 心情概览 + 每日分布 */
@Composable
fun MoodStatsDialog(vm: MoodViewModel, onDismiss: () -> Unit) {
    val all by vm.all.collectAsState()
    var range by remember { mutableStateOf(MoodLogic.Range.M6) }
    val today = remember { LocalDate.now() }
    val from = remember(all, range) { MoodLogic.from(range, today, all) }
    val series = remember(all, from) { MoodLogic.series(all, from, today) }
    val counts = remember(all, from) { MoodLogic.counts(all, from, today) }
    val weekday = remember(all, from) { MoodLogic.weekdayAvg(all, from, today) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                    Text("心情统计", fontSize = 22.sp, fontWeight = FontWeight.Medium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
                    MoodLogic.Range.entries.forEach { r -> FilterChip(selected = range == r, onClick = { range = r }, label = { Text(r.label) }) }
                }
                if (counts.sumOf { it.second } == 0) {
                    Text("这段时间还没有心情记录。在日记标签顶上点一下表情就能记。", modifier = Modifier.padding(24.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ChartCard("心情起伏") { MoodLine(series, from, today) }
                    ChartCard("心情概览") { MoodBars(counts) }
                    ChartCard("每日分布", hint = "周一到周日的平均心情") { WeekdayBars(weekday) }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChartCard(title: String, hint: String? = null, content: @Composable () -> Unit) {
    SoftCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            if (hint != null) Text(hint, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

/** 纵轴的五个表情：上面最好，下面最差，和图里的五条横线对齐 */
@Composable
private fun EmojiAxis(height: androidx.compose.ui.unit.Dp) {
    Column(modifier = Modifier.width(30.dp).height(height), verticalArrangement = Arrangement.SpaceBetween) {
        MoodLogic.LEVELS.forEach { Text(it.emoji, fontSize = 16.sp, lineHeight = 18.sp) }
    }
}

/** 心情起伏：平滑曲线 + 下方渐变填充；点一下看那天（那周 / 那月）的平均心情 */
@Composable
private fun MoodLine(points: List<MoodLogic.Point>, from: LocalDate, to: LocalDate) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    var picked by remember(points) { mutableStateOf<MoodLogic.Point?>(null) }
    val chartH = 170.dp
    val span = ChronoUnit.DAYS.between(from, to).coerceAtLeast(1)
    Text(picked?.let { p -> "${p.start.monthValue}月${p.start.dayOfMonth}日" + (if (span > 92) " 起" else "") + " · " +
            MoodLogic.level(Math.round(p.avg).toInt()).let { "${it.emoji} ${it.label}" } + "（${p.count} 次）" } ?: "点曲线看具体哪天",
        fontSize = 12.sp, color = ink, modifier = Modifier.padding(start = 30.dp, bottom = 4.dp))
    Row {
        EmojiAxis(chartH)
        Column(modifier = Modifier.weight(1f)) {
            val labelPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
            Canvas(modifier = Modifier.fillMaxWidth().height(chartH + 22.dp).pointerInput(points) {
                detectTapGestures { pos ->
                    if (points.isEmpty()) return@detectTapGestures
                    val w = size.width.toFloat()
                    picked = points.minByOrNull { p -> kotlin.math.abs(ChronoUnit.DAYS.between(from, p.start) / span.toFloat() * w - pos.x) }
                }
            }) {
                val h = chartH.toPx(); val w = size.width
                val pad = 9.dp.toPx()          // 上下各留一点，让最高 / 最低的线和表情对齐
                fun y(v: Double) = pad + ((5 - v) / 4.0 * (h - 2 * pad)).toFloat()
                fun x(d: LocalDate) = ChronoUnit.DAYS.between(from, d) / span.toFloat() * w
                for (v in 1..5) drawLine(grid, Offset(0f, y(v.toDouble())), Offset(w, y(v.toDouble())), strokeWidth = 1f)
                // 月份刻度：时间跨度很长时隔几个月标一次
                labelPaint.color = ink.toArgb(); labelPaint.textSize = 11.sp.toPx(); labelPaint.textAlign = Paint.Align.CENTER
                val months = generateSequence(from.withDayOfMonth(1).plusMonths(1)) { it.plusMonths(1) }.takeWhile { !it.isAfter(to) }.toList()
                val step = ((months.size + 5) / 6).coerceAtLeast(1)
                months.forEachIndexed { i, m ->
                    if (i % step != 0) return@forEachIndexed
                    drawLine(grid, Offset(x(m), pad), Offset(x(m), h - pad), strokeWidth = 1f)
                    drawContext.canvas.nativeCanvas.drawText(if (m.monthValue == 1) "${m.year}年" else "${m.monthValue}月", x(m), h + 16.dp.toPx(), labelPaint)
                }
                if (points.isEmpty()) return@Canvas
                val pts = points.map { Offset(x(it.start), y(it.avg)) }
                // 平滑曲线（单调的三次插值，不会冲出 1~5 的范围太多）
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val p0 = pts[i - 1]; val p1 = pts[i]; val mx = (p0.x + p1.x) / 2
                        cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
                    }
                }
                val fill = Path().apply { addPath(path); lineTo(pts.last().x, h - pad); lineTo(pts.first().x, h - pad); close() }
                drawPath(fill, Brush.verticalGradient(listOf(primary.copy(alpha = 0.30f), primary.copy(alpha = 0.02f)), startY = pad, endY = h - pad))
                drawPath(path, primary, style = Stroke(width = 2.dp.toPx()))
                if (pts.size == 1) drawCircle(primary, 4.dp.toPx(), pts[0])
                picked?.let { p -> val o = Offset(x(p.start), y(p.avg)); drawCircle(primary, 5.dp.toPx(), o) }
            }
        }
    }
}

/** 心情概览：每档一行，左边表情，横条长短表示次数，次数写在条的右边 */
@Composable
private fun MoodBars(counts: List<Pair<MoodLogic.Level, Int>>) {
    val primary = MaterialTheme.colorScheme.primary
    val max = counts.maxOf { it.second }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        counts.forEach { (l, n) ->
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(34.dp)) {
                val barMax = maxWidth - 44.dp
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.width(34.dp + (barMax - 34.dp) * (n.toFloat() / max)).fillMaxHeight().clip(RoundedCornerShape(17.dp))
                        .background(primary.copy(alpha = if (n == 0) 0.15f else 0.85f)), contentAlignment = Alignment.CenterStart) {
                        Box(modifier = Modifier.padding(start = 3.dp).size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                            contentAlignment = Alignment.Center) { Text(l.emoji, fontSize = 16.sp) }
                    }
                    Text(" $n", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

/** 每日分布：周一到周日各一根柱子，高度是平均心情；没记录的那天不画 */
@Composable
private fun WeekdayBars(avgs: List<Double?>) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
    val chartH = 150.dp
    Row {
        EmojiAxis(chartH)
        Column(modifier = Modifier.weight(1f)) {
            Canvas(modifier = Modifier.fillMaxWidth().height(chartH)) {
                val h = size.height; val w = size.width
                val pad = 9.dp.toPx()
                fun y(v: Double) = pad + ((5 - v) / 4.0 * (h - 2 * pad)).toFloat()
                for (v in 1..5) drawLine(grid, Offset(0f, y(v.toDouble())), Offset(w, y(v.toDouble())), strokeWidth = 1f)
                val slot = w / 7f; val bw = slot * 0.62f
                avgs.forEachIndexed { i, a ->
                    if (a == null) return@forEachIndexed
                    val top = y(a); val left = i * slot + (slot - bw) / 2
                    // 柱子从最低那条线往上长，顶端圆角
                    drawRoundRect(primary.copy(alpha = 0.85f), Offset(left, top), Size(bw, (h - pad) - top + 0.01f), CornerRadius(bw / 2, bw / 2))
                }
            }
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                    Text(it, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
            }
        }
    }
}
