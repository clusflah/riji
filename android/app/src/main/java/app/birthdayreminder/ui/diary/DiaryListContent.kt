package app.birthdayreminder.ui.diary

import app.birthdayreminder.ui.common.DateBadge
import app.birthdayreminder.ui.common.TgAvatar
import app.birthdayreminder.ui.common.CardListItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.BlockConverter
import app.birthdayreminder.domain.DiaryText

/** 全部日记（放在“全部”页的标签里）：日期新的在前，点一篇进去编辑 */
@Composable
fun DiaryListContent(onEdit: (String) -> Unit, vm: DiaryListViewModel = viewModel()) {
    val all by vm.all.collectAsState()
    val rows = remember(all) {
        all.sortedWith(compareByDescending<app.birthdayreminder.data.DiaryEntity> { it.date }.thenByDescending { it.updatedAt })
            .map { e ->
                val blocks = BlockConverter.fromJson(e.content)
                val tags = buildString {
                    if (DiaryText.imageCount(blocks) > 0) append("🖼 ")
                    if (DiaryText.fileCount(blocks) > 0) append("📎 ")
                    if (e.locationAddress != null) append("📍 ")
                }
                Triple(e, DiaryText.snippet(blocks, 60).ifBlank { "（这篇日记只有图片）" }, tags.trim())
            }
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
        if (rows.isEmpty()) {
            item { Text("还没有日记，点右下角“+”写一篇", modifier = Modifier.padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(rows, key = { it.first.id }) { (e, snippet, tags) ->
            CardListItem(
                leadingContent = { DateBadge(e.date.takeLast(2).trimStart('0')) },      // 日期数字：统一浅底，不再随机彩色
                overlineContent = { Text(e.date + if (tags.isNotEmpty()) "  $tags" else "") },
                headlineContent = { Text(snippet, maxLines = 2) },
                modifier = Modifier.clickable { onEdit(e.id) }
            )
        }
    }
}
