package app.birthdayreminder.ui.health

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.birthdayreminder.domain.FitnessInsights
import java.time.temporal.ChronoUnit

/**
 * 体重变化折线图（最近 90 天）。横轴按真实日期间隔排（中间没记的日子会空出来），
 * 纵轴按这段时间的最低、最高体重自动缩放，上下各留一点空。
 */
@Composable
fun WeightChart(points: List<FitnessInsights.Point>, modifier: Modifier = Modifier) {
    if (points.size < 2) {
        Text("记两次以上体重，这里就会画出变化曲线", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
        return
    }
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val minV = points.minOf { it.value }
    val maxV = points.maxOf { it.value }
    val span = (maxV - minV).coerceAtLeast(1.0)          // 体重几乎不变时也不要画成一条贴边的直线
    val lo = minV - span * 0.15
    val hi = maxV + span * 0.15
    val first = points.first().date
    val totalDays = ChronoUnit.DAYS.between(first, points.last().date).coerceAtLeast(1).toFloat()

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("体重（kg）", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            Text("最高 ${fmt(maxV)} · 最低 ${fmt(minV)}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(modifier = Modifier.fillMaxWidth().height(110.dp)) {
            val w = size.width
            val h = size.height
            fun x(i: Int) = ChronoUnit.DAYS.between(first, points[i].date) / totalDays * w
            fun y(v: Double) = (h - (v - lo) / (hi - lo) * h).toFloat()
            // 三条浅色横线：最高、中间、最低
            for (v in listOf(maxV, (maxV + minV) / 2, minV)) {
                drawLine(grid, Offset(0f, y(v)), Offset(w, y(v)), strokeWidth = 1f)
            }
            val path = Path()
            points.forEachIndexed { i, p -> if (i == 0) path.moveTo(x(i), y(p.value)) else path.lineTo(x(i), y(p.value)) }
            drawPath(path, line, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            points.forEachIndexed { i, p -> drawCircle(line, radius = 6f, center = Offset(x(i), y(p.value))) }
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            Text("${first.monthValue}月${first.dayOfMonth}日", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            val last = points.last()
            Text("${last.date.monthValue}月${last.date.dayOfMonth}日 ${fmt(last.value)}kg", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
        }
    }
}

private fun fmt(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else "%.1f".format(v)
