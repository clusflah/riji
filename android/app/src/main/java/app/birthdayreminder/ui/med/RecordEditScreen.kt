package app.birthdayreminder.ui.med

import app.birthdayreminder.privacy.SecureWindow
import app.birthdayreminder.ui.common.TgTopBar
import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.birthdayreminder.data.ImageStorage
import app.birthdayreminder.data.MedicalRecord
import app.birthdayreminder.domain.MedicalLogic
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

/** 添加 / 编辑一次就医：病情、症状、医院科室医生、医嘱、复诊日期、这次开的药、病历和检查单照片、备注 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordEditScreen(id: String?, onBack: () -> Unit, initialPrivate: Boolean = false, onNewMed: () -> Unit, vm: MedViewModel = viewModel()) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val meds by vm.meds.collectAsState()
    var existing by remember { mutableStateOf<MedicalRecord?>(null) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var illness by remember { mutableStateOf("") }
    var symptoms by remember { mutableStateOf("") }
    var hospital by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }
    var doctor by remember { mutableStateOf("") }
    var advice by remember { mutableStateOf("") }
    var followUp by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var isPrivate by remember { mutableStateOf(initialPrivate) }      // 在隐私空间里新建的就是私密的；编辑已有的保持原样
    SecureWindow(isPrivate)                                    // 私密内容不让截屏
    val medIds = remember { mutableStateListOf<String>() }
    val images = remember { mutableStateListOf<String>() }
    val originalImages = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(id) {
        if (id == null) return@LaunchedEffect
        val r = vm.getRecord(id) ?: return@LaunchedEffect
        existing = r
        date = r.date; illness = r.illness; symptoms = r.symptoms; hospital = r.hospital; department = r.department
        doctor = r.doctor; advice = r.advice; followUp = r.followUp; note = r.note; isPrivate = r.isPrivate
        medIds.clear(); medIds.addAll(MedicalLogic.listFromJson(r.medIds))
        images.clear(); images.addAll(MedicalLogic.listFromJson(r.images))
        originalImages.clear(); originalImages.addAll(images)
    }

    /** 不保存就离开：这次新加的照片删掉，原来的照片不动 */
    fun leave() { vm.discardImages(images.filter { it !in originalImages }); onBack() }
    BackHandler { leave() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(9)) { uris ->
        scope.launch { for (u in uris) vm.addImage(u)?.let { images.add(it) } }
    }

    fun pickDate(initial: String?, onPick: (String) -> Unit) {
        val d = initial?.let { try { LocalDate.parse(it) } catch (e: Exception) { null } } ?: LocalDate.now()
        DatePickerDialog(ctx, { _, y, mo, day -> onPick(LocalDate.of(y, mo + 1, day).toString()) }, d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    fun save() {
        val r = MedicalRecord(existing?.id ?: UUID.randomUUID().toString(), date, illness.trim().take(40), symptoms.trim().take(300),
            hospital.trim().take(40), department.trim().take(20), doctor.trim().take(20), advice.trim().take(500), followUp,
            MedicalLogic.listToJson(medIds.filter { mid -> meds.any { it.id == mid } }), MedicalLogic.listToJson(images.toList()), note.trim().take(300), isPrivate = isPrivate)
        MedicalLogic.validate(r)?.let { error = it; return }
        error = null
        vm.saveRecord(r, removedImages = originalImages.filter { it !in images }, onDone = onBack)
    }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(if (existing != null) "就医记录" else "添加就医记录") },
            navigationIcon = { IconButton(onClick = { leave() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") } },
            actions = { TextButton(onClick = { save() }) { Text("保存") } }
        )
    }) { padding ->
        Column(modifier = Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = { pickDate(date) { date = it } }) { Text("就诊日期：$date") }
            OutlinedTextField(value = illness, onValueChange = { illness = it.take(40) }, label = { Text("病情 / 诊断（比如：急性咽炎）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = symptoms, onValueChange = { symptoms = it.take(300) }, label = { Text("症状（选填，比如：发烧 38.5℃、咽痛 3 天）") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = hospital, onValueChange = { hospital = it.take(40) }, label = { Text("医院") }, singleLine = true, modifier = Modifier.weight(1.4f))
                OutlinedTextField(value = department, onValueChange = { department = it.take(20) }, label = { Text("科室") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = doctor, onValueChange = { doctor = it.take(20) }, label = { Text("医生（选填）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = advice, onValueChange = { advice = it.take(500) }, label = { Text("医嘱 / 处理（比如：多喝水，3 天后复查血常规）") }, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { pickDate(followUp ?: date) { followUp = it } }) { Text("复诊：" + (followUp ?: "不用复诊")) }
                if (followUp != null) TextButton(onClick = { followUp = null }) { Text("不用复诊") }
            }
            if (followUp != null) Text("复诊前一天晚上 8 点、当天早上 9 点会提醒你", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            HorizontalDivider()
            Text("这次开的药", style = MaterialTheme.typography.labelLarge)
            if (meds.isEmpty()) Text("药品里还没有药，可以先去添加，再回来勾选", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                meds.forEach { m ->
                    FilterChip(selected = m.id in medIds, onClick = { if (m.id in medIds) medIds.remove(m.id) else medIds.add(m.id) }, label = { Text(m.name) })
                }
            }
            TextButton(onClick = onNewMed) { Text("＋ 新建一种药（设置服药时间和提醒）") }

            HorizontalDivider()
            Text("病历 / 检查单照片", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                images.forEach { path ->
                    Box {
                        AsyncImage(model = ImageStorage.resolve(ctx, path), contentDescription = "照片", contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(8.dp)))
                        Row(modifier = Modifier.padding(2.dp)) {
                            TextButton(onClick = { viewing = path }, modifier = Modifier.size(44.dp, 32.dp)) { Text("看") }
                            TextButton(onClick = { images.remove(path) }, modifier = Modifier.size(44.dp, 32.dp)) { Text("✕", color = MaterialTheme.colorScheme.error) }
                        }
                    }
                }
            }
            TextButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text("＋ 添加照片（一次最多 9 张）") }

            OutlinedTextField(value = note, onValueChange = { note = it.take(300) }, label = { Text("备注（选填，比如花费、医保报销）") }, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (existing != null) {
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) { Text("删除这条就医记录", color = MaterialTheme.colorScheme.error) }
            }
        }
    }

    viewing?.let { path ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            text = { AsyncImage(model = ImageStorage.resolve(ctx, path), contentDescription = "照片", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { viewing = null }) { Text("关闭") } }
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("删除就医记录") },
            text = { Text("这条记录和它的照片都会删除，复诊提醒也会取消。开的药不受影响。") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; existing?.let { vm.deleteRecord(it, onBack) } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}
