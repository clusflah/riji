package app.birthdayreminder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.birthdayreminder.notify.SettingsStore
import java.time.LocalDate
import java.time.YearMonth

private val WEEK = "日一二三四五六"

/**
 * 选日期的弹窗。不用 Material3 自带的 DatePicker：它的星期表头取系统的“窄格式”星期名，
 * 中文下会变成一排“星”；这里自己画，表头是“一 二 … 日”，并且跟随设置里的“每周开始”。
 * 点年月标题可以切到年份列表。maxDate 不为空时，之后的日期不能选。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiDatePickerDialog(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit, maxDate: LocalDate? = null) {
    val ctx = LocalContext.current
    val weekStart = remember { SettingsStore.get(ctx).weekStart }
    val today = remember { LocalDate.now() }
    var selected by rememberSaveable { mutableStateOf(initial.toString()) }
    var month by rememberSaveable { mutableStateOf(YearMonth.from(initial).toString()) }
    var pickYear by rememberSaveable { mutableStateOf(false) }
    val sel = LocalDate.parse(selected)
    val ym = YearMonth.parse(month)
    val maxYm = maxDate?.let { YearMonth.from(it) }

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = { onPick(sel); onDismiss() }) { Text("确定") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp)) {
            Text("选择日期", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 16.dp))
            Text("${sel.year}年${sel.monthValue}月${sel.dayOfMonth}日 周${WEEK[sel.dayOfWeek.value % 7]}",
                fontSize = 26.sp, color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { pickYear = !pickYear }
                    .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${ym.year}年${ym.monthValue}月", fontSize = 15.sp, fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(if (pickYear) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown, contentDescription = "选年份",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                if (!pickYear) {
                    IconButton(onClick = { month = ym.minusMonths(1).toString() }, enabled = ym.year > 1900 || ym.monthValue > 1) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    IconButton(onClick = { month = ym.plusMonths(1).toString() },
                        enabled = (maxYm == null || ym < maxYm) && (ym.year < 2100 || ym.monthValue < 12)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
            }

            if (pickYear) {
                val lastYear = maxDate?.year ?: 2100
                val years = (1900..lastYear).toList()
                val state = rememberLazyGridState(initialFirstVisibleItemIndex = (years.indexOf(ym.year) - 6).coerceAtLeast(0))
                LazyVerticalGrid(columns = GridCells.Fixed(3), state = state, modifier = Modifier.fillMaxWidth().height(300.dp)) {
                    items(years) { y ->
                        val on = y == ym.year
                        Box(modifier = Modifier.padding(4.dp).clip(RoundedCornerShape(18.dp))
                            .then(if (on) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                            .clickable {
                                var m = YearMonth.of(y, ym.monthValue)
                                if (maxYm != null && m > maxYm) m = maxYm
                                month = m.toString(); pickYear = false
                            }
                            .padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                            Text("$y", fontSize = 15.sp, color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    repeat(7) { i ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(WEEK[(i + weekStart) % 7].toString(), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                val lead = (ym.atDay(1).dayOfWeek.value % 7 - weekStart + 7) % 7
                val cells = List(42) { i -> (i - lead + 1).takeIf { it in 1..ym.lengthOfMonth() } }
                // 只画到这个月最后一天所在的那一行，但固定高度 6 行，切换月份时按钮不跳动
                Column(modifier = Modifier.height(6 * 44.dp), verticalArrangement = Arrangement.Top) {
                    cells.chunked(7).filter { row -> row.any { it != null } }.forEach { row ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            row.forEach { d ->
                                Box(modifier = Modifier.weight(1f).height(44.dp), contentAlignment = Alignment.Center) {
                                    if (d != null) {
                                        val day = ym.atDay(d)
                                        val enabled = maxDate == null || !day.isAfter(maxDate)
                                        val isSel = day == sel
                                        val isToday = day == today
                                        Box(
                                            modifier = Modifier.size(40.dp).clip(CircleShape)
                                                .then(if (isSel) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier)
                                                .then(if (isToday && !isSel) Modifier.border(1.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)
                                                .clickable(enabled = enabled) { selected = day.toString() },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("$d", fontSize = 15.sp, color = when {
                                                isSel -> MaterialTheme.colorScheme.onPrimary
                                                !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                                isToday -> MaterialTheme.colorScheme.primary
                                                else -> MaterialTheme.colorScheme.onSurface
                                            })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
