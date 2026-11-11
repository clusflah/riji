package app.birthdayreminder.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 面板里的一格 */
class SheetItem(val kind: String, val title: String, val action: () -> Unit)

/**
 * “+”添加面板和“数据”面板共用的布局：按用途分组，每行 4 个小格子（图标 + 名字），没有标题；
 * 开启液体玻璃时联动底层页面大半径高斯模糊，配合 86% 清透纯白底、15% 轻透遮罩和顶部 1px 纯白高光弧线，彻底杜绝透字残影。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TileSheet(groups: List<Pair<String, List<SheetItem>>>, container: Color, tile: Color, onDismiss: () -> Unit) {
    RegisterLiquidGlassSheet()
    val shown = groups.filter { it.second.isNotEmpty() }
    val glass = LocalLiquidGlass.current
    val sheetBg = if (glass) liquidGlassSheetContainerColor() else container
    val scrim = if (glass) liquidGlassScrimColor() else BottomSheetDefaults.ScrimColor
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = sheetBg,
        scrimColor = scrim,
        tonalElevation = 0.dp,
        dragHandle = if (glass) null else ({ BottomSheetDefaults.DragHandle() })
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidSheetTopHighlight(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (glass) LiquidSheetDragHandle()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = 20.dp, end = 20.dp, top = if (glass) 4.dp else 0.dp, bottom = 32.dp)
            ) {
                if (shown.isEmpty()) {
                    Text(
                        "所有功能都关掉了，可以在设置 → 功能开关里打开",
                        modifier = Modifier.padding(8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                shown.forEachIndexed { gi, (name, items) ->
                    // 分类标题：灰色小字，不抢格子；分类之间留 26
                    Text(
                        name,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = if (gi == 0) 4.dp else 26.dp, bottom = 12.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items.chunked(4).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                row.forEach { it2 ->
                                    AddTile(
                                        it2.kind,
                                        it2.title,
                                        modifier = Modifier.weight(1f),
                                        color = tile,
                                        onClick = it2.action
                                    )
                                }
                                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}
