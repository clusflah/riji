package app.birthdayreminder.ui.alarm

import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.background
import app.birthdayreminder.ui.common.Tg
import app.birthdayreminder.ui.common.CardListItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.domain.AlarmTriggers

/** 闹钟列表（放在“全部”页的第三个标签里）。点一行编辑，右边开关直接启用/停用。 */
@Composable
fun AlarmListContent(
    onEdit: (String) -> Unit,
    vm: AlarmViewModel = viewModel()
) {
    val rows by vm.rows.collectAsState()
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (rows.isEmpty()) {
            item {
                Text(
                    "还没有闹钟，点右下角“+”添加",
                    modifier = Modifier.padding(24.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(rows, key = { it.alarm.id }) { row ->
            val a = row.alarm
            // 每个闹钟一张浅底卡片：时间大字，下面是重复方式和下次响铃
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                    .background(Tg.card).clickable { onEdit(a.id) }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(a.time, fontSize = 32.sp, fontWeight = FontWeight.Medium,
                        color = if (a.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text((if (a.label.isNotBlank()) a.label + " · " else "") + AlarmTriggers.repeatText(a), fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(row.nextText?.let { "下次 $it" } ?: if (a.enabled) "" else "已关闭", fontSize = 13.sp,
                        color = if (a.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Switch(checked = a.enabled, onCheckedChange = { vm.toggle(a) })
            }
        }
    }
}
