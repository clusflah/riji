package app.birthdayreminder.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.input.PasswordVisualTransformation
import android.app.Activity
import app.birthdayreminder.domain.CloudPlan
import app.birthdayreminder.domain.CloudFile
import app.birthdayreminder.cloud.WebDavStorage
import app.birthdayreminder.cloud.OneDriveAuth
import app.birthdayreminder.cloud.CloudSync
import app.birthdayreminder.cloud.CloudConfig
import app.birthdayreminder.ui.common.applyRefreshRate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import app.birthdayreminder.ui.theme.LiColors
import app.birthdayreminder.ui.common.NavRow
import app.birthdayreminder.ui.common.NavGroup
import androidx.compose.foundation.background
import app.birthdayreminder.ui.common.Tg
import app.birthdayreminder.ui.common.TgTopBar
import app.birthdayreminder.ui.common.CardGroup
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.birthdayreminder.Features
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.ModuleStore
import app.birthdayreminder.domain.AutoBackupPlan
import app.birthdayreminder.data.AutoBackup
import app.birthdayreminder.notify.Reliability
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.birthdayreminder.AlarmEngine
import app.birthdayreminder.privacy.PrivacyAuth
import androidx.fragment.app.FragmentActivity
import app.birthdayreminder.data.BackupRunner
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.domain.AppSettings
import app.birthdayreminder.domain.BackupCodec
import app.birthdayreminder.domain.BackupData
import app.birthdayreminder.notify.Delivery
import app.birthdayreminder.notify.NotificationHelper
import app.birthdayreminder.notify.Prefs
import app.birthdayreminder.notify.ReminderScheduler
import app.birthdayreminder.notify.SettingsStore
import app.birthdayreminder.notify.Upkeep
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val s by SettingsStore.flow.collectAsState()

    var showTime by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }
    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }
    var holBusy by remember { mutableStateOf(false) }
    var amapKey by remember { mutableStateOf(Prefs.amapKey(ctx)) }
    var amapSec by remember { mutableStateOf(Prefs.amapSecCode(ctx)) }
    // 通知权限状态：进页面读一次，授权弹窗回来后再读
    var permTick by remember { mutableStateOf(0) }
    val permGranted = remember(permTick) { NotificationHelper.hasPermission(ctx) }

    /** 改设置后，凡是会影响通知的都要重新排一遍 */
    fun change(reschedule: Boolean = false, f: (AppSettings) -> AppSettings) {
        SettingsStore.update(ctx, f)
        if (reschedule) scope.launch { ReminderScheduler.reschedule(ctx) }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permTick++ }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch { msg = BackupRunner.export(ctx, uri) ?: "备份文件已保存" }
    }
    // 提醒检查：每次回到这个页面（比如从系统设置改完回来）都重新读一次真实状态
    var checkTick by remember { mutableStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) checkTick++ }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }
    val checks = remember(checkTick, s.notify) { Reliability.check(ctx) }
    fun openFix(key: String) {
        val i = Reliability.fixIntent(ctx, key) ?: return
        try { ctx.startActivity(i) } catch (e: Exception) {
            try { Reliability.fallbackIntent(ctx, key)?.let { ctx.startActivity(it) } } catch (e2: Exception) { msg = "这台手机打不开对应的设置页，请在系统设置里找到“日迹”手动设置" }
        }
    }

    // 自动备份：选文件夹
    var backupBusy by remember { mutableStateOf(false) }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree != null) {
            AutoBackup.rememberFolder(ctx, tree)
            if (s.autoBackup == "off") change { it.copy(autoBackup = "weekly") }   // 选了文件夹默认每周一次
        }
    }
    /** 打开自动备份前：如果有私密内容，先验证身份（备份文件里是明文） */
    fun setFreq(f: String) {
        if (f == "off") { change { it.copy(autoBackup = "off") }; return }
        if (s.autoBackupDir.isBlank()) { msg = "请先选择备份文件夹"; return }
        scope.launch {
            val act = ctx as? FragmentActivity
            if (s.autoBackup == "off" && act != null && PrivacyAuth.available(act)) {
                PrivacyAuth.authenticate(act, "验证身份", "备份里包含你的数据和 Key", onSuccess = { change { it.copy(autoBackup = f) } }, onFail = { msg = it })
            } else change { it.copy(autoBackup = f) }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            when (val r = BackupRunner.read(ctx, uri)) {
                is BackupRunner.ReadResult.Ok -> pendingRestore = r.data
                is BackupRunner.ReadResult.Fail -> msg = r.message
            }
        }
    }

    // 设置分两层：总览（分组卡片、摘要、常用开关）→ 点进去看这一组的详细设置
    var sub by rememberSaveable { mutableStateOf<String?>(null) }
    var cloudList by remember { mutableStateOf<List<CloudFile>?>(null) }
    BackHandler(enabled = sub != null) { sub = null }
    val subTitle = when (sub) { null -> "设置"; "高德地图（日记里“添加位置”用）" -> "高德地图"; else -> sub!! }

    Scaffold(topBar = {
        TgTopBar(
            title = { Text(subTitle, fontSize = 22.sp, fontWeight = FontWeight.Medium) },
            navigationIcon = {
                IconButton(onClick = { if (sub != null) sub = null else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }) { padding ->
        Column(
            modifier = Modifier.padding(padding).background(Tg.gap).verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // ---------------- 总览 ----------------
            if (sub == null) {
                val moduleVer0 by ModuleStore.version
                val offCount = remember(moduleVer0) { ModuleStore.disabled(ctx).size }
                val onCount = Modules.available(Features.allergy).size - offCount
                val bad = checks.count { !it.ok }
                NavGroup("日历") {
                    NavRow("lunar", "显示农历", trailing = { Switch(checked = s.showLunar, onCheckedChange = { on -> change { it.copy(showLunar = on) } }) })
                    NavRow("calendar", "显示节日和节气", trailing = { Switch(checked = s.showFestival, onCheckedChange = { on -> change { it.copy(showFestival = on) } }) })
                    NavRow("week", "每周开始", last = true, trailing = {
                        Text(if (s.weekStart == 1) "周一" else "周日", fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                    }, onClick = { change { it.copy(weekStart = 1 - it.weekStart) } })
                }
                NavGroup("通用") {
                    NavRow("appearance", "外观", when (s.theme) { "light" -> "浅色"; "dark" -> "深色"; else -> "跟随系统" } + (if (Prefs.highRefresh(ctx)) " · 高刷新率" else "")) { sub = "外观" }
                    NavRow("notify", "通知", if (s.notify) "已开启 · 每天 ${s.dailyTime}" else "已关闭") { sub = "通知" }
                    NavRow("check", "提醒检查", if (bad == 0) "一切正常" else "有 $bad 项需要处理",
                        summaryColor = if (bad == 0) LiColors.done else MaterialTheme.colorScheme.error, last = true) { sub = "提醒检查" }
                }
                NavGroup("功能") {
                    NavRow("modules", "功能开关", "已开启 $onCount 项", last = true) { sub = "功能开关" }
                }
                NavGroup("数据") {
                    NavRow("backup", "数据备份",
                        if (s.lastBackup > 0) "上次 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(s.lastBackup)) else "还没有备份过") { sub = "数据备份" }
                    val cloudVer by CloudSync.version
                    val odVer by OneDriveAuth.version
                    val bdVer by app.birthdayreminder.cloud.BaiduAuth.version
                    val cloudSummary = remember(cloudVer, odVer, bdVer, sub) {
                        when (CloudConfig.provider(ctx)) {
                            "onedrive" -> "OneDrive" + (if (OneDriveAuth.signedIn(ctx)) "" else " · 未登录")
                            "baidu" -> "百度网盘" + (if (app.birthdayreminder.cloud.BaiduAuth.signedIn(ctx)) "" else " · 未登录")
                            "webdav" -> "WebDAV"
                            else -> "关闭"
                        } + (if (CloudConfig.provider(ctx) != "off" && CloudConfig.lastError(ctx).isNotBlank()) " · 上次失败"
                             else if (CloudConfig.provider(ctx) != "off" && CloudConfig.lastAt(ctx) > 0) " · 上次 " + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(CloudConfig.lastAt(ctx))) else "")
                    }
                    NavRow("backup", "云盘备份", cloudSummary,
                        summaryColor = if (CloudConfig.provider(ctx) != "off" && CloudConfig.lastError(ctx).isNotBlank()) MaterialTheme.colorScheme.error else null) { sub = "云盘备份" }
                    NavRow("holiday", "节假日数据", if (s.autoHoliday) "自动更新" else "手动更新", last = true) { sub = "节假日数据" }
                }
                NavGroup("高级设置") {
                    NavRow("map", "高德地图 Key", if (amapKey.isNotBlank()) "已填写" else "日记“添加位置”用，未填写", last = true) { sub = "高德地图（日记里“添加位置”用）" }
                }
            }

            // ---------------- 功能开关 ----------------
            if (sub == "功能开关") CardGroup("功能开关") {
                Text("关掉后隐藏并停止提醒，数据保留。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val moduleVer by ModuleStore.version
                val offModules = remember(moduleVer) { ModuleStore.disabled(ctx) }
                Modules.available(Features.allergy).forEach { m ->
                    SwitchRow(m.label, m.desc, m.key !in offModules) { on -> ModuleStore.set(ctx, m.key, on) }
                }
            }

            if (sub == "通知") CardGroup("通知") {
                SwitchRow("系统通知", if (permGranted) "已授权" else "尚未授权：点右边开关去授权", s.notify) { on ->
                    if (on && !permGranted) {
                        if (Build.VERSION.SDK_INT >= 33) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName))
                    }
                    change(true) { it.copy(notify = on) }
                }
                ActionRow("每日提醒时间", "生日和“全天”提醒在这个时间触发", s.dailyTime) { showTime = true }
                Text("新建生日的默认提前提醒", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AppSettings.ADVANCE_CHOICES.forEach { a ->
                        FilterChip(
                            selected = a in s.defAdvance,
                            onClick = { change { it.copy(defAdvance = if (a in it.defAdvance) it.defAdvance - a else (it.defAdvance + a).sorted()) } },
                            label = { Text(if (a == 0) "当天" else "提前${a}天") }
                        )
                    }
                }
                SwitchRow("节假日与补班提醒", "放假和补班的前一天提醒", s.holidayAlert) { on -> change(true) { it.copy(holidayAlert = on) } }
            }
            if (sub == "提醒检查") CardGroup("提醒检查") {
                val bad = checks.count { !it.ok }
                Text(
                    if (bad == 0) "✅ 系统这边的设置都没问题" else "⚠️ 有 $bad 项会让提醒不准时，建议处理",
                    color = if (bad == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error
                )
                checks.forEach { c ->
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text((if (c.ok) "✅ " else "⚠️ ") + c.title)
                            Text(if (c.ok) c.okText else c.badText, style = MaterialTheme.typography.bodySmall,
                                color = if (c.ok) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                        }
                        if (!c.ok && c.fixLabel.isNotEmpty()) TextButton(onClick = { openFix(c.key) }) { Text(c.fixLabel) }
                    }
                }
                SwitchRow("闹钟级提醒", "提醒和吃药按闹钟的方式定时，国产手机上也能准时弹出；状态栏会有闹钟图标，锁屏的“下一个闹钟”可能显示提醒的时间", s.strongAlarm) { on ->
                    change { it.copy(strongAlarm = on) }
                    if (on) { Delivery.clearLate(ctx); checkTick++ }      // 旧的晚弹记录作废，上面的检查列表跟着刷新
                    Upkeep.refresh(ctx)      // 所有提醒按新方式重新定时
                }
                Text(Reliability.VENDOR_TIP, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    OutlinedButton(onClick = { openFix("vendor") }) { Text("打开应用信息") }
                    OutlinedButton(onClick = {
                        Reliability.scheduleTest(ctx)
                        msg = "${Reliability.TEST_DELAY_SEC} 秒后弹出测试通知，可以锁屏等一下看看。"
                    }) { Text("发送测试通知") }
                }
            }
            // 农历、节日节气、每周起始日只在总览的“日历”组里设置，这里不再重复
            if (sub == "外观") CardGroup("外观") {
                var highRefresh by remember { mutableStateOf(Prefs.highRefresh(ctx)) }
                SwitchRow("高刷新率", "滑动更流畅，会稍微多耗一点电", highRefresh) { on ->
                    highRefresh = on; Prefs.setHighRefresh(ctx, on)
                    (ctx as? android.app.Activity)?.let { applyRefreshRate(it) }
                }
                Text("主题", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("auto" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (v, l) ->
                        FilterChip(selected = s.theme == v, onClick = { change { it.copy(theme = v) } }, label = { Text(l) })
                    }
                }
            }
            // ---------------- 云盘备份（OneDrive / WebDAV）----------------
            if (sub == "云盘备份") CardGroup("云盘备份") {
                val cloudVer by CloudSync.version
                val odVer by OneDriveAuth.version
                var provider by remember { mutableStateOf(CloudConfig.provider(ctx)) }
                var clientId by remember { mutableStateOf(CloudConfig.clientId(ctx)) }
                var davUrl by remember { mutableStateOf(CloudConfig.davUrl(ctx)) }
                var davUser by remember { mutableStateOf(CloudConfig.davUser(ctx)) }
                var davPass by remember { mutableStateOf(CloudConfig.davPass(ctx)) }
                var davFolder by remember { mutableStateOf(CloudConfig.davFolder(ctx)) }
                var cloudFreq by remember { mutableStateOf(CloudConfig.freq(ctx)) }
                var wifi by remember { mutableStateOf(CloudConfig.wifiOnly(ctx)) }
                var charging by remember { mutableStateOf(CloudConfig.chargingOnly(ctx)) }
                var keep by remember { mutableStateOf(CloudConfig.keep(ctx)) }
                var busy by remember { mutableStateOf(false) }
                val signedIn = remember(odVer) { OneDriveAuth.signedIn(ctx) }
                Text("每次在后台生成一份完整备份（数据、设置、Key、图片、附件、铃声）上传到云盘，云盘上只保留最新几份。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    listOf("off" to "关闭", "onedrive" to "OneDrive", "baidu" to "百度网盘", "webdav" to "WebDAV").forEach { (v, l) ->
                        FilterChip(selected = provider == v, onClick = {
                            val go = { provider = v; CloudConfig.setProvider(ctx, v); CloudSync.schedule(ctx) }
                            // 备份里有数据和 Key：开启云盘备份前先验证身份（手机支持的话）
                            val act = ctx as? FragmentActivity
                            if (provider == "off" && v != "off" && act != null && PrivacyAuth.available(act))
                                PrivacyAuth.authenticate(act, "验证身份", "云盘备份里包含你的数据和 Key", onSuccess = go, onFail = { msg = it })
                            else go()
                        }, label = { Text(l) })
                    }
                }
                if (provider == "onedrive") {
                    OutlinedTextField(value = clientId, onValueChange = { clientId = it.trim(); CloudConfig.setClientId(ctx, it) }, singleLine = true,
                        label = { Text("应用（客户端）ID") }, modifier = Modifier.fillMaxWidth())
                    Text("在微软 Azure 门户免费注册一个应用后得到，步骤见项目里的 CLOUD_BACKUP.md。备份放在 OneDrive 的“应用 / 日迹”文件夹里，日迹碰不到你的其它文件。",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (signedIn) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("已登录：" + CloudConfig.odAccount(ctx).ifBlank { "微软账号" }, modifier = Modifier.weight(1f))
                            TextButton(onClick = { OneDriveAuth.signOut(ctx); CloudSync.schedule(ctx) }) { Text("退出登录") }
                        }
                    } else {
                        OutlinedButton(onClick = { (ctx as? Activity)?.let { a -> OneDriveAuth.start(a)?.let { msg = it } } }) { Text("登录 OneDrive") }
                    }
                }
                if (provider == "baidu") BaiduPanSection(onMessage = { msg = it })
                if (provider == "webdav") {
                    OutlinedTextField(value = davUrl, onValueChange = { davUrl = it }, singleLine = true, label = { Text("服务器地址（如 https://dav.jianguoyun.com/dav/）") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = davUser, onValueChange = { davUser = it }, singleLine = true, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = davPass, onValueChange = { davPass = it }, singleLine = true, label = { Text("密码（坚果云请用“应用密码”）") },
                        visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = davFolder, onValueChange = { davFolder = it }, singleLine = true, label = { Text("云盘里的文件夹") }, modifier = Modifier.fillMaxWidth())
                    OutlinedButton(enabled = !busy, onClick = {
                        CloudConfig.saveDav(ctx, davUrl, davUser, davPass, davFolder); CloudSync.schedule(ctx)
                        busy = true
                        scope.launch {
                            val err = WebDavStorage(davUrl.trim(), davFolder.trim().ifEmpty { "日迹备份" }, davUser.trim(), davPass).test()
                            busy = false
                            msg = err?.let { "连接失败：$it" } ?: "连接成功，已保存"
                        }
                    }) { Text(if (busy) "正在连接…" else "保存并测试连接") }
                }
                if (provider != "off") {
                    HorizontalDivider()
                    Text("自动上传", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("off" to "关闭", "daily" to "每天", "weekly" to "每周").forEach { (v, l) ->
                            FilterChip(selected = cloudFreq == v, onClick = { cloudFreq = v; CloudConfig.setFreq(ctx, v); CloudSync.schedule(ctx) }, label = { Text(l) })
                        }
                    }
                    SwitchRow("仅 Wi-Fi", "不用手机流量上传", wifi) { wifi = it; CloudConfig.setWifiOnly(ctx, it); CloudSync.schedule(ctx) }
                    SwitchRow("仅充电时", "插着充电器时才上传", charging) { charging = it; CloudConfig.setChargingOnly(ctx, it); CloudSync.schedule(ctx) }
                    Text("云盘上保留", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CloudPlan.KEEP_OPTIONS.forEach { n -> FilterChip(selected = keep == n, onClick = { keep = n; CloudConfig.setKeep(ctx, n) }, label = { Text("最新 $n 份") }) }
                    }
                    val status = remember(cloudVer) {
                        val err = CloudConfig.lastError(ctx); val at = CloudConfig.lastAt(ctx)
                        when {
                            err.isNotBlank() -> "上次上传失败：$err"
                            at > 0 -> "上次上传：" + SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(at))
                            else -> "还没有上传过"
                        }
                    }
                    Text(status, style = MaterialTheme.typography.bodySmall,
                        color = if (CloudConfig.lastError(ctx).isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { CloudSync.uploadNow(ctx); msg = "已开始上传，完成后这里会显示结果" }) { Text("立即上传") }
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                try { cloudList = CloudSync.listBackups(ctx); if (cloudList!!.isEmpty()) { cloudList = null; msg = "云盘上还没有备份" } }
                                catch (e: Exception) { msg = e.message ?: "读取云盘失败" }
                                busy = false
                            }
                        }) { Text("从云盘恢复") }
                    }
                }
            }

            if (sub == "节假日数据") CardGroup("节假日数据") {
                SwitchRow("自动获取节假日更新", "联网时自动补全内置数据没有的年份（来自 holiday-cn）", s.autoHoliday) { on -> change { it.copy(autoHoliday = on) } }
                OutlinedButton(enabled = !holBusy, onClick = {
                    holBusy = true
                    scope.launch {
                        val y = java.time.LocalDate.now().year
                        val results = listOf(y, y + 1).map { HolidayRepository.refreshYear(ctx, it, force = true) }
                        holBusy = false
                        msg = when {
                            results.any { it == "ok" } -> { ReminderScheduler.reschedule(ctx); AlarmEngine.sync(ctx); "节假日数据已更新" }
                            results.all { it == "error" } -> "更新失败，请检查网络"
                            else -> "线上暂时没有更新的数据，继续使用内置数据"
                        }
                    }
                }) { Text(if (holBusy) "更新中…" else "立即更新节假日数据") }
            }
            if (sub == "高德地图（日记里“添加位置”用）") CardGroup("高德地图（日记里“添加位置”用）") {
                OutlinedTextField(value = amapKey, onValueChange = { amapKey = it; Prefs.setAmapKey(ctx, it) }, singleLine = true,
                    label = { Text("高德地图 Key（Web端 JS API）") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = amapSec, onValueChange = { amapSec = it; Prefs.setAmapSecCode(ctx, it) }, singleLine = true,
                    label = { Text("安全密钥 JSCode（新申请的 Key 通常需要）") }, modifier = Modifier.fillMaxWidth())
            }
            if (sub == "数据备份") CardGroup("数据备份") {
                Text(
                    if (s.lastBackup > 0) "上次备份：" + SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(s.lastBackup)) else "还没有备份过",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        scope.launch {
                            val go = { exportLauncher.launch(BackupCodec.fileName(LocalDateTime.now())) }
                            // 备份里有数据和 Key（明文）：只要手机支持就先验证身份
                            val act = ctx as? FragmentActivity
                            if (act != null && PrivacyAuth.available(act)) {
                                PrivacyAuth.authenticate(act, "验证身份", "备份里包含你的数据和 Key", onSuccess = go, onFail = { msg = it })
                            } else go()
                        }
                    }) { Text("导出备份") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/zip", "application/json", "text/plain", "application/octet-stream", "*/*")) }) { Text("从备份恢复") }
                }
                Text("自动备份", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 10.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("备份文件夹")
                        Text(AutoBackup.folderLabel(s.autoBackupDir), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { folderLauncher.launch(null) }) { Text(if (s.autoBackupDir.isBlank()) "选择文件夹" else "更换") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    AutoBackupPlan.FREQS.forEach { (v, l) -> FilterChip(selected = s.autoBackup == v, onClick = { setFreq(v) }, label = { Text(l) }) }
                }
                Text(
                    when {
                        s.autoBackupError.isNotBlank() -> "⚠️ " + s.autoBackupError
                        s.lastAutoBackup > 0 -> "上次自动备份：" + SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(s.lastAutoBackup))
                        s.autoBackup != "off" -> "下次打开 App 时会自动备份"
                        else -> "选一个文件夹（比如网盘的同步文件夹），之后每次打开 App 时，到时间就自动备份一份"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (s.autoBackupError.isNotBlank()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (s.autoBackupDir.isNotBlank()) {
                    OutlinedButton(enabled = !backupBusy, onClick = {
                        backupBusy = true
                        scope.launch {
                            val err = AutoBackup.runIfDue(ctx, force = true)
                            backupBusy = false
                            msg = err ?: "已备份到“${AutoBackup.folderLabel(s.autoBackupDir)}”"
                        }
                    }) { Text(if (backupBusy) "备份中…" else "立即备份一次") }
                }
                Text("只保留最新 ${AutoBackupPlan.KEEP} 份自动备份。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Text(
                    "备份包含全部记录、设置、铃声、图片、附件和 Key；以前的 .json 备份也能恢复。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    cloudList?.let { list ->
        AlertDialog(
            onDismissRequest = { cloudList = null },
            title = { Text("从云盘恢复") },
            text = {
                Column(modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    list.forEach { f ->
                        Column(modifier = Modifier.fillMaxWidth().clickable {
                            cloudList = null
                            msg = "正在下载…"
                            scope.launch {
                                try {
                                    val file = CloudSync.download(ctx, f.name)
                                    when (val r = BackupRunner.read(ctx, Uri.fromFile(file))) {
                                        is BackupRunner.ReadResult.Ok -> { msg = null; pendingRestore = r.data }
                                        is BackupRunner.ReadResult.Fail -> msg = r.message
                                    }
                                } catch (e: Exception) { msg = e.message ?: "下载失败" }
                            }
                        }.padding(vertical = 10.dp)) {
                            Text(if (f.modifiedMs > 0) SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.CHINA).format(Date(f.modifiedMs)) else f.name)
                            Text("%.1f MB · %s".format(f.size / 1048576.0, f.name), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { cloudList = null }) { Text("取消") } }
        )
    }

    if (showTime) {
        val init = try { java.time.LocalTime.parse(s.dailyTime) } catch (e: Exception) { java.time.LocalTime.of(9, 0) }
        val state = rememberTimePickerState(initialHour = init.hour, initialMinute = init.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { showTime = false },
            confirmButton = {
                TextButton(onClick = { change(true) { it.copy(dailyTime = "%02d:%02d".format(state.hour, state.minute)) }; showTime = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("取消") } },
            text = { TimePicker(state = state) }
        )
    }

    pendingRestore?.let { data ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("恢复备份") },
            text = {
                Text(BackupCodec.summary(data) +
                    "。\n\n恢复后会替换现在对应的数据，无法撤销，建议先导出一份当前的备份。")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        try { BackupRunner.apply(ctx, data); msg = "已恢复" } catch (e: Exception) { msg = "恢复失败：" + (e.message ?: "未知错误") }
                    }
                }) { Text("恢复", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { pendingRestore = null }) { Text("取消") } }
        )
    }

    msg?.let { m ->
        AlertDialog(
            onDismissRequest = { msg = null },
            text = { Text(m) },
            confirmButton = { TextButton(onClick = { msg = null }) { Text("好的") } }
        )
    }
}

@Composable
private fun SwitchRow(title: String, sub: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionRow(title: String, sub: String, value: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = onClick) { Text(value) }
    }
}
