package app.birthdayreminder.ui.diary

import androidx.compose.ui.unit.sp
import app.birthdayreminder.ui.common.Tg
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Notes
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Title
import app.birthdayreminder.ui.common.TgTopBar
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.ModuleStore
import app.birthdayreminder.domain.TableOps
import app.birthdayreminder.domain.RichText
import app.birthdayreminder.domain.PasteImport
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.border
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import app.birthdayreminder.data.FileStorage
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import android.widget.Toast
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.AssistChip
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import app.birthdayreminder.data.ContentBlock
import app.birthdayreminder.data.ImageStorage
import app.birthdayreminder.privacy.SecureWindow
import coil.compose.AsyncImage
import java.io.File
import java.time.LocalDate

/**
 * 日记编辑页：正文是一串“块”（文本/图片），图片可以插在光标所在位置，
 * 顶部工具栏支持基本 Markdown（加粗/斜体/标题/列表/引用）+ 插入图片 + 添加位置 + 预览切换。
 *
 * vm 由调用方（导航图）用父路由作用域创建并传入，这样跳去“添加位置”页面再返回，
 * 正文和已选状态都还在，不会被重置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiaryEditScreen(
    id: String?,
    initialDate: LocalDate,
    vm: DiaryEditViewModel,
    onBack: () -> Unit,
    onPickLocation: () -> Unit,
    initialPrivate: Boolean = false
) {
    SecureWindow(vm.isPrivate)
    val context = LocalContext.current
    // 点空白处定位：要定位到哪一段（最后一段文字），每点一次 endTick +1 触发一次
    var endTarget by remember { mutableStateOf<String?>(null) }
    val richVisual = RichVisualTransformation(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onSurfaceVariant)
    var endTick by remember { mutableStateOf(0) }
    val keyboard = LocalSoftwareKeyboardController.current
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, initialDate) { vm.ensureLoaded(id, initialDate, initialPrivate) }

    // 附件：任意文件（PDF、文档、表格、压缩包…），复制进 App 后插在光标处
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.insertFile(uri)?.let { msg -> Toast.makeText(context, msg, Toast.LENGTH_SHORT).show() }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris: List<Uri> ->
        uris.forEach { uri ->
            ImageStorage.saveFromUri(context, uri, context.contentResolver.getType(uri))?.let { vm.insertImage(it) }
        }
    }

    // 剪贴板里有图片（截图、在相册或网页里点了“复制图片”）时多一个“粘贴图片”。
    // 文本框自己的“粘贴”只认文字，键盘直接发图片它也收不了，所以从这里放进日记；
    // 只看剪贴板的类型说明，不读内容，不会弹“已读取剪贴板”的提示。
    // 回到这一页（窗口重新拿到焦点）时也查一次：在别的 App 里复制的图片，回来就能粘
    val clipboard = remember { context.getSystemService(ClipboardManager::class.java) }
    var clipHasImage by remember { mutableStateOf(false) }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    DisposableEffect(clipboard) {
        val check = ClipboardManager.OnPrimaryClipChangedListener { clipHasImage = clipboardHasImage(clipboard) }
        clipboard?.addPrimaryClipChangedListener(check)
        onDispose { clipboard?.removePrimaryClipChangedListener(check) }
    }
    LaunchedEffect(windowFocused) { if (windowFocused) clipHasImage = clipboardHasImage(clipboard) }

    Scaffold(
        topBar = {
            TgTopBar(
                title = { Text(if (id != null) "编辑日记" else "写日记") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    if (id != null) {
                        TextButton(onClick = { confirmDelete = true }) {
                            Text("删除", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    TextButton(onClick = { vm.save(onBack) }) { Text("保存") }
                }
            )
        }
    ) { padding ->
        if (!vm.loaded) return@Scaffold
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // 日期弱化：日记真正重要的是正文
            Text(
                "${vm.date.monthValue}月${vm.date.dayOfMonth}日 · " + listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")[vm.date.dayOfWeek.value - 1] +
                    (if (vm.date.year != java.time.LocalDate.now().year) " · ${vm.date.year}年" else ""),
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // 格式（所见即所得，不显示任何符号）：作用在光标所在行 / 选中的文字
            // 格式（所见即所得）：一排图标按钮，一屏放得下；长按能看到名字
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FormatIcon(Icons.Default.Title, "大标题") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.H1) } }
                FormatIcon(Icons.Default.TextFields, "小标题") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.H2) } }
                FormatIcon(Icons.Default.Notes, "正文") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, null) } }
                FormatDivider()
                FormatIcon(Icons.Default.FormatListNumbered, "数字列表") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.NUM) } }
                FormatIcon(Icons.Default.FormatListBulleted, "圆点列表") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.DOT) } }
                FormatIcon(Icons.Default.HorizontalRule, "横线列表") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.DASH) } }
                FormatIcon(Icons.Default.CheckBox, "待办") { vm.mutateRich { t, a, b -> RichText.setLineMark(t, a, b, RichText.TODO) } }
                FormatDivider()
                FormatIcon(Icons.Default.FormatStrikethrough, "划线") {
                    if (vm.focusedCursor == vm.focusedSelEnd) Toast.makeText(context, "先选中要划线的文字", Toast.LENGTH_SHORT).show()
                    else vm.mutateRich { t, a, b -> RichText.toggleStrike(t, a, b) }
                }
                FormatIcon(Icons.Default.TableChart, "表格") { vm.insertTable() }
            }
            // 插入：图片 / 附件；AI 润色
            // 插入：图片 / 附件（浅灰底、无边框）；AI 润色（淡紫底 —— 紫色专门表示 AI）
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolPill(Icons.Outlined.Image, "图片") { imagePicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                if (clipHasImage) ToolPill(Icons.Outlined.ContentPaste, "粘贴图片") {
                    val clip = runCatching { clipboard?.primaryClip }.getOrNull()
                    val uris = clip?.let { c -> (0 until c.itemCount).mapNotNull { c.getItemAt(it).uri } }.orEmpty()
                    val saved = uris.mapNotNull { uri -> ImageStorage.saveFromUri(context, uri, context.contentResolver.getType(uri)) }
                    saved.forEach { vm.insertImage(it) }
                    if (saved.isEmpty()) Toast.makeText(context, "剪贴板里的图片读不到了，换“图片”从相册里选", Toast.LENGTH_SHORT).show()
                }
                ToolPill(Icons.Outlined.AttachFile, "附件") { filePicker.launch(arrayOf("*/*")) }
                if (ModuleStore.isOn(context, Modules.AI)) ToolPill(Icons.Outlined.AutoAwesome, if (vm.polishing) "润色中…" else "AI 润色", ai = true) {      // AI 助手关掉了就不显示润色
                    if (!vm.polishing) vm.startPolish()
                }
            }


            if (vm.importing) {
                Text(
                    "正在整理粘贴的内容（下载图片）…",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
                )
            }

            if (vm.location != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(onClick = onPickLocation, label = { Text("📍 " + (vm.location?.address ?: "")) })
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.location = null }) { Text("移除") }
                }
            } else {
                TextButton(onClick = onPickLocation, modifier = Modifier.padding(start = 8.dp)) { Text("📍 添加位置") }
            }

            run {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(vm.blocks, key = { _, b -> b.id }) { index, block ->
                        when (block) {
                            is ContentBlock.TextBlock -> {
                                var tfv by remember(block.id) { mutableStateOf(TextFieldValue(block.text)) }
                                val fr = remember(block.id) { FocusRequester() }
                                // 点了下方空白处：如果要定位到这一段，光标放到末尾并获得焦点
                                LaunchedEffect(endTick) {
                                    if (endTick > 0 && endTarget == block.id) {
                                        tfv = tfv.copy(selection = TextRange(tfv.text.length))
                                        vm.updateBlockText(index, block, tfv.text, tfv.text.length)
                                        try { fr.requestFocus() } catch (e: Exception) { }
                                        keyboard?.show()
                                    }
                                }
                                LaunchedEffect(block.text, vm.richTick) {
                                    if (tfv.text != block.text) {
                                        // 工具栏改了格式：文字同步过来，选区放到操作给出的位置
                                        val len = block.text.length
                                        val a = if (vm.focusedId == block.id) vm.focusedCursor.coerceIn(0, len) else len
                                        val b = if (vm.focusedId == block.id) vm.focusedSelEnd.coerceIn(0, len) else len
                                        tfv = TextFieldValue(block.text, TextRange(a, b))
                                    }
                                }
                                var layout by remember(block.id) { mutableStateOf<TextLayoutResult?>(null) }
                                val boxes = remember(tfv.text) { RichText.render(tfv.text).checkboxes }
                                // 手势里要读到最新的值
                                val boxesState = rememberUpdatedState(boxes)
                                val tfvState = rememberUpdatedState(tfv)
                                BasicTextField(
                                    value = tfv,
                                    onValueChange = { nv ->
                                        val tfv0 = tfv
                                        // 列表行里回车：下一行自动续上格式（空的列表行再回车就结束列表）
                                        val cont = if (nv.composition == null) RichText.continueOnEnter(tfv.text, nv.text, nv.selection.start) else null
                                        val r = when {
                                            cont != null -> cont
                                            nv.composition == null -> RichText.normalizeKeeping(nv.text, nv.selection.start, nv.selection.end)
                                            else -> RichText.EditResult(nv.text, nv.selection.start, nv.selection.end)
                                        }
                                        tfv = if (r.text == nv.text && r.selStart == nv.selection.start && r.selEnd == nv.selection.end) nv
                                        else TextFieldValue(r.text, TextRange(r.selStart, r.selEnd))
                                        vm.updateBlockText(index, block, r.text, r.selStart, r.selEnd)
                                        // 一次进来一大段带格式 / 图片的文字（粘贴、输入法助手“输入”的 AI 回答）：
                                        // 先照原样显示，后台转成日记格式、下载图片，好了再换上
                                        if (nv.composition == null && !vm.importing) {
                                            val old = tfv0.text
                                            val new = r.text
                                            if (new.length - old.length >= 4) {
                                                val p = old.commonPrefixWith(new).length
                                                val sfx = old.substring(p).commonSuffixWith(new.substring(p)).length
                                                val inserted = new.substring(p, new.length - sfx)
                                                if (PasteImport.worthConverting(inserted)) {
                                                    vm.importPasted(block.id, new, new.substring(0, p), inserted, new.substring(new.length - sfx))
                                                }
                                            }
                                        }
                                    },
                                    visualTransformation = richVisual,
                                    onTextLayout = { layout = it },
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).focusRequester(fr)
                                        // 点待办前面的方框：勾选 / 取消勾选（不影响正常点文字定位光标）
                                        .pointerInput(block.id) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                                val up = waitForUpOrCancellation(PointerEventPass.Initial) ?: return@awaitEachGesture
                                                if ((up.position - down.position).getDistance() > 24f) return@awaitEachGesture
                                                val lay = layout ?: return@awaitEachGesture
                                                val off = lay.getOffsetForPosition(up.position)
                                                val hit = boxesState.value.firstOrNull { (range, _) -> off >= range.first && off <= range.last + 1 }
                                                    ?: return@awaitEachGesture
                                                val nt = RichText.toggleCheck(tfvState.value.text, hit.second)
                                                val sel = tfvState.value.selection
                                                tfv = TextFieldValue(nt, TextRange(sel.start.coerceAtMost(nt.length), sel.end.coerceAtMost(nt.length)))
                                                vm.updateTextById(block.id, nt, tfv.selection.start, tfv.selection.end)
                                            }
                                        },
                                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                                )
                            }
                            is ContentBlock.TableBlock -> Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                                TableEditor(
                                    rows = block.rows,
                                    onChange = { vm.updateTable(block.id, it) },
                                    onDelete = { vm.removeTable(block.id) }
                                )
                            }
                            is ContentBlock.FileBlock -> Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                                AttachmentCard(block, onDelete = { vm.removeFile(block.id, block.path) })
                            }
                            is ContentBlock.ImageBlock -> {
                                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                    DiaryImage(block.imagePath)
                                    TextButton(
                                        onClick = { vm.removeImage(block.id, block.imagePath) },
                                        modifier = Modifier.align(Alignment.TopEnd)
                                    ) { Text("删除", color = Color.White) }
                                }
                            }
                        }
                    }
                    // 内容下方的空白：点任何地方都把光标放到最后一段文字的末尾（和普通记事本一样）
                    item(key = "tail_space") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.7f)
                                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                                    val last = vm.blocks.lastOrNull { it is ContentBlock.TextBlock }
                                    if (last != null) { endTarget = last.id; endTick++ }
                                }
                        )
                    }
                }
            }
        }
    }

    if (vm.polishResult != null) {
        AlertDialog(
            onDismissRequest = { vm.dismissPolish() },
            title = { Text("AI 润色结果") },
            text = { Column(modifier = Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) { Text(vm.polishResult ?: "") } },
            confirmButton = { TextButton(onClick = { vm.applyPolish() }) { Text("使用这段替换") } },
            dismissButton = { TextButton(onClick = { vm.dismissPolish() }) { Text("取消") } }
        )
    }
    vm.polishError?.let { msg ->
        AlertDialog(
            onDismissRequest = { vm.dismissPolish() },
            title = { Text("AI 润色") },
            text = { Text(msg) },
            confirmButton = { TextButton(onClick = { vm.dismissPolish() }) { Text("知道了") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除日记") },
            text = { Text("确定删除这篇日记吗？") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.delete(onBack) }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DiaryImage(path: String) {
    val context = LocalContext.current
    AsyncImage(
        model = File(context.filesDir, path),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(10.dp))
    )
}

/** 格式工具栏的一个图标按钮（灰色图标，长按 / 无障碍读出名字） */
@Composable
private fun FormatIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
        Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(22.dp))
    }
}

/** 插入类按钮：浅灰底、无边框、圆角；AI 用淡紫底 */
@Composable
private fun ToolPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, ai: Boolean = false, onClick: () -> Unit) {
    val purple = Color(0xFF8B5CF6)
    Row(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (ai) purple.copy(alpha = 0.10f) else Tg.card)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = if (ai) purple else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
        Text(" $label", fontSize = 14.sp, color = if (ai) purple else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun FormatButton(label: String, strike: Boolean = false, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.height(40.dp).widthIn(min = 40.dp), contentPadding = PaddingValues(horizontal = 8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, textDecoration = if (strike) TextDecoration.LineThrough else null)
    }
}

@Composable
private fun FormatDivider() {
    Box(modifier = Modifier.padding(horizontal = 2.dp).width(1.dp).height(20.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** 表格编辑器：每个格子都能直接改；第一行是表头（加粗、浅色底）；下面可以加减行列、删除表格 */
@Composable
private fun TableEditor(rows: List<List<String>>, onChange: (List<List<String>>) -> Unit, onDelete: () -> Unit) {
    val line = MaterialTheme.colorScheme.outlineVariant
    val headBg = MaterialTheme.colorScheme.surfaceVariant
    val rowsState = rememberUpdatedState(rows)
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().border(1.dp, line)) {
            rows.forEachIndexed { r, row ->
                Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).then(if (r == 0) Modifier.background(headBg) else Modifier)) {
                    row.forEachIndexed { c, cell ->
                        BasicTextField(
                            value = cell,
                            onValueChange = { v -> onChange(TableOps.setCell(rowsState.value, r, c, v)) },
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = if (r == 0) FontWeight.Bold else FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f).fillMaxHeight().border(0.5.dp, line).padding(horizontal = 6.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            TextButton(onClick = { onChange(TableOps.addRow(rowsState.value)) }) { Text("＋行") }
            TextButton(onClick = { onChange(TableOps.removeRow(rowsState.value)) }) { Text("－行") }
            TextButton(onClick = { onChange(TableOps.addCol(rowsState.value)) }) { Text("＋列") }
            TextButton(onClick = { onChange(TableOps.removeCol(rowsState.value)) }) { Text("－列") }
            TextButton(onClick = onDelete) { Text("删除表格", color = MaterialTheme.colorScheme.error) }
        }
    }
}

/** 附件卡片：文件名 + 大小，点一下用手机上的 App 打开；onDelete 不为空时右边有“删除” */
@Composable
private fun AttachmentCard(b: ContentBlock.FileBlock, onDelete: (() -> Unit)?) {
    val ctx = LocalContext.current
    val size = remember(b.path) { FileStorage.sizeText(ctx, b.path) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
            .clickable { FileStorage.open(ctx, b.path, b.name)?.let { Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show() } }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("📎", modifier = Modifier.padding(end = 10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(b.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
            Text(size ?: "文件已经不在了", style = MaterialTheme.typography.bodySmall,
                color = if (size == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onDelete != null) TextButton(onClick = onDelete) { Text("删除", color = MaterialTheme.colorScheme.error) }
    }
}

/** 剪贴板里是不是图片：只看类型说明（image 开头），不读内容 */
private fun clipboardHasImage(clipboard: ClipboardManager?): Boolean = runCatching {
    val d = clipboard?.primaryClipDescription ?: return@runCatching false
    (0 until d.mimeTypeCount).any { d.getMimeType(it).startsWith("image/") }
}.getOrDefault(false)
