package app.birthdayreminder

import app.birthdayreminder.cloud.CloudSync
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.Icons
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.activity.compose.BackHandler
import app.birthdayreminder.ui.common.ModuleStore
import app.birthdayreminder.domain.Modules
import app.birthdayreminder.ui.common.applyRefreshRate
import androidx.compose.runtime.SideEffect
import androidx.core.view.WindowCompat
import app.birthdayreminder.health.HealthAutoSync
import app.birthdayreminder.notify.CycleScheduler
import app.birthdayreminder.notify.MedScheduler
import app.birthdayreminder.ui.health.FitnessFieldStore
import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import android.widget.Toast
import app.birthdayreminder.privacy.PrivacyAuth
import app.birthdayreminder.ui.search.SearchScreen
import app.birthdayreminder.ui.med.MedEditScreen
import app.birthdayreminder.ui.med.RecordEditScreen
import app.birthdayreminder.privacy.PrivacyScreen
import app.birthdayreminder.privacy.PrivacySession
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.birthdayreminder.data.HolidayRepository
import app.birthdayreminder.data.AutoBackup
import app.birthdayreminder.notify.NotificationHelper
import app.birthdayreminder.notify.SettingsStore
import app.birthdayreminder.ui.phrase.PhraseEditScreen
import app.birthdayreminder.ui.settings.SettingsScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.birthdayreminder.domain.OpenTarget
import app.birthdayreminder.notify.LegacyNotifications
import kotlinx.coroutines.Dispatchers
import app.birthdayreminder.notify.ReminderScheduler
import app.birthdayreminder.ui.alarm.AlarmEditScreen
import app.birthdayreminder.ui.assistant.AssistantScreen
import app.birthdayreminder.ui.calendar.CalendarScreen
import app.birthdayreminder.ui.diary.DiaryEditScreen
import app.birthdayreminder.ui.diary.DiaryEditViewModel
import app.birthdayreminder.ui.diary.DiaryLocationPickerScreen
import app.birthdayreminder.ui.edit.BirthdayEditScreen
import app.birthdayreminder.ui.edit.ReminderEditScreen
import app.birthdayreminder.ui.list.ListScreen
import app.birthdayreminder.ui.health.AllergyEditScreen
import app.birthdayreminder.ui.health.FitnessEditScreen
import app.birthdayreminder.ui.theme.LiTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * 主界面：桌面图标、桌面小组件、系统闹钟图标、闹钟错过通知，点开的都是它。
 * 小组件带来的“想看哪一页”（Intent 里的 li_open）会在这里跳到对应页面。
 */
private const val DIARY_ROUTE = "diary/{id}?date={date}&private={private}"

class MainActivityCompose : FragmentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* 用户点了允许/拒绝，都不需要额外处理 */ }

    /** 小组件等入口带来的“想看哪一页”，界面读到后跳转并清空 */
    private var pendingOpen by mutableStateOf<String?>(null)

    private fun readOpen(intent: Intent?): String? =
        intent?.getStringExtra(OpenTarget.EXTRA)?.takeIf { it.isNotBlank() }

    /** 因为离开太久被重新上锁时置 true，界面据此退回日历首页（编辑中的私密内容不会继续显示） */
    private var privacyKicked by mutableStateOf(false)

    override fun onStop() {
        super.onStop()
        PrivacySession.onBackground()
    }

    override fun onStart() {
        super.onStart()
        // 打开 App 时：Health Connect 自动同步（开着、隔了 15 分钟以上才会真的去读）
        HealthAutoSync.onForeground(this)
        // 有些系统切到别的应用再回来会重置刷新率，回来时再设一次
        applyRefreshRate(this)
        if (PrivacySession.onForeground()) privacyKicked = true
        // 用药提醒：每次回到 App 都重新排一次（接下来 48 小时），也顺便检查过期药品。
        // 放在 onStart 而不是 onCreate：App 一直留在后台时 onCreate 不会再走
        MedScheduler.reschedule(this)
        // 自动备份：到时间了就在后台备份一次（没打开自动备份、或者还没到时间，什么都不做）
        lifecycleScope.launch(Dispatchers.IO) { try { AutoBackup.runIfDue(this@MainActivityCompose) } catch (e: Exception) { } }
    }

    /** 长按年月标题之后：验证身份，通过就进隐私空间 */
    private fun enterPrivacy(openSpace: () -> Unit) {
        if (PrivacySession.isUnlocked) { openSpace(); return }
        if (!PrivacyAuth.available(this)) {
            Toast.makeText(this, "请先在手机设置里设置锁屏密码或指纹，才能使用这个功能", Toast.LENGTH_LONG).show()
            return
        }
        PrivacyAuth.authenticate(this, "验证身份", "验证通过后进入",
            onSuccess = { PrivacySession.unlock(); openSpace() },
            onFail = { msg -> Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() })
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingOpen = readOpen(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 健身项目设置（顺序、隐藏、自定义项目）：其它地方用到项目列表之前先载入
        FitnessFieldStore.load(this)
        CycleScheduler.reschedule(this)
        // Health Connect 自动同步：按开关排好后台任务
        HealthAutoSync.schedule(this)
        // 云盘备份：按设置排好定时上传
        CloudSync.schedule(this)
        pendingOpen = readOpen(intent)
        enableEdgeToEdge()
        // 高刷新率：用屏幕支持的最高刷新率（至少 90Hz），滑动更流畅
        applyRefreshRate(this)

        SettingsStore.load(this)
        HolidayRepository.reloadFromCache(this)   // 先读本机缓存，日历一打开就能显示休/班
        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && !NotificationHelper.hasPermission(this)) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        // 每次打开 App 都按最新数据重排一遍通知，防止系统清掉定时
        // 数据一有变化就自动刷新桌面小组件（首次收集时也会刷新一次）
        WidgetUpdater.keepInSync(lifecycleScope, this)
        lifecycleScope.launch(Dispatchers.IO) {
            // 从网页版升级上来的：它之前排好的定时通知会按旧数据弹、还会和现在的重复，清掉（清完之后这一步就什么都不做）
            LegacyNotifications.cancelAll(this@MainActivityCompose)
        }
        lifecycleScope.launch {
            ReminderScheduler.reschedule(this@MainActivityCompose)
            AlarmEngine.sync(this@MainActivityCompose)
            // 顺手联网刷新今年和明年的节假日；有更新的话，工作日闹钟也要跟着重新算
            if (SettingsStore.get(this@MainActivityCompose).autoHoliday && HolidayRepository.refreshOpportunistic(this@MainActivityCompose)) {
                AlarmEngine.sync(this@MainActivityCompose)
            }
        }

        setContent {
            val settings by SettingsStore.flow.collectAsState()
            val dark = when (settings.theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            LiTheme(darkTheme = dark) {
                // 顶栏是浅色：状态栏图标跟着 App 的深浅色走（App 里单独选了深色 / 浅色时也对）
                val view = androidx.compose.ui.platform.LocalView.current
                SideEffect {
                    val c = WindowCompat.getInsetsController(window, view)
                    c.isAppearanceLightStatusBars = !dark
                    c.isAppearanceLightNavigationBars = !dark
                }
                val nav = rememberNavController()

                // 小组件点进来：跳到对应页面；日历类小组件（没有目标页）就回首页
                val open = pendingOpen
                LaunchedEffect(open) {
                    if (open != null) {
                        val route = OpenTarget.route(open, Features.allergy)
                        if (route != null) nav.navigate(route) { launchSingleTop = true }
                        else nav.popBackStack("calendar", false)
                        pendingOpen = null
                    }
                }

                // 离开 App 超过 1 分钟被重新上锁：退回日历首页
                LaunchedEffect(privacyKicked) {
                    if (privacyKicked) { nav.popBackStack("calendar", false); privacyKicked = false }
                }

                NavHost(navController = nav, startDestination = "calendar") {
                    composable("calendar") {
                        // 首页就是日历；左下角“数据”打开数据面板，右下角“+”打开添加面板
                        CalendarScreen(
                                onNewBirthday = { nav.navigate("birthday/new") },
                                onNewReminder = { date -> nav.navigate("reminder/new?date=$date") },
                                onNewDiary = { date -> nav.navigate("diary/new?date=$date") },
                                onNewAlarm = { nav.navigate("alarm/new") },
                                onNewPhrase = { nav.navigate("phrase/new") },
                                onNewFitness = { date -> nav.navigate("fitness?date=$date") },
                                onNewAllergy = { date -> nav.navigate("allergy?date=$date") },
                                onNewMed = { nav.navigate("med/new") },
                                onNewVisit = { nav.navigate("visit/new") },
                                onOpenCycle = { nav.navigate("list?tab=${OpenTarget.TAB_CYCLE}") },
                                onEditBirthday = { id -> nav.navigate("birthday/$id") },
                                onEditReminder = { id -> nav.navigate("reminder/$id") },
                                onEditDiary = { id -> nav.navigate("diary/$id") },
                                onOpenList = { nav.navigate("list") },
                                onOpenData = { tab -> nav.navigate("list?tab=$tab") },
                                onEditAlarm = { id -> nav.navigate("alarm/$id") },
                                onEditMed = { id -> nav.navigate("med/$id") },
                                onEditRecord = { id -> nav.navigate("visit/$id") },
                                onEditPhrase = { id -> nav.navigate("phrase/$id") },
                                onOpenFitnessDate = { date -> nav.navigate("fitness?date=$date") },
                                onOpenAllergyDate = { date -> nav.navigate("allergy?date=$date") },
                                onOpenSettings = { nav.navigate("settings") },
                                onAiClick = { nav.navigate("assistant") },
                                onPrivacyEntry = { enterPrivacy { nav.navigate("privacy") { launchSingleTop = true } } },
                                onSearch = { nav.navigate("search") }
                        )
                    }
                    composable(
                        route = "med/{id}?private={private}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        MedEditScreen(id = id, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = "visit/{id}?private={private}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        RecordEditScreen(id = id, onBack = { nav.popBackStack() }, onNewMed = { nav.navigate("med/new") }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable("search") {
                        SearchScreen(
                            onBack = { nav.popBackStack() },
                            onOpen = { kind, id ->
                                when (kind) {
                                    "diary" -> nav.navigate("diary/$id")
                                    "reminder" -> nav.navigate("reminder/$id")
                                    "birthday" -> nav.navigate("birthday/$id")
                                    else -> nav.navigate("phrase/$id")
                                }
                            }
                        )
                    }
                    composable("privacy") {
                        PrivacyScreen(
                            onExit = { nav.popBackStack("calendar", false) },
                            onNew = { tab ->
                                val d = LocalDate.now()
                                when (tab) {
                                    0 -> nav.navigate("diary/new?date=$d&private=1")
                                    1 -> nav.navigate("reminder/new?date=$d&private=1")
                                    2 -> nav.navigate("birthday/new?private=1")
                                    3 -> nav.navigate("phrase/new?private=1")
                                    4 -> nav.navigate("fitness?date=$d&private=1")
                                    5 -> nav.navigate("allergy?date=$d&private=1")
                                    6 -> nav.navigate("alarm/new?private=1")
                                    7 -> nav.navigate("med/new?private=1")
                                    else -> nav.navigate("visit/new?private=1")      // PRIVACY_NEW_VISIT（就医记录）
                                }
                            },
                            onEditDiary = { id -> nav.navigate("diary/$id") },
                            onEditReminder = { id -> nav.navigate("reminder/$id") },
                            onEditBirthday = { id -> nav.navigate("birthday/$id") },
                            onEditPhrase = { id -> nav.navigate("phrase/$id") },
                            onEditFitness = { date -> nav.navigate("fitness?date=$date") },
                            onEditAllergy = { date -> nav.navigate("allergy?date=$date") },
                            onEditAlarm = { id -> nav.navigate("alarm/$id") },
                            onEditMed = { id -> nav.navigate("med/$id") },
                            onEditRecord = { id -> nav.navigate("visit/$id") }
                        )
                    }
                    composable("settings") { SettingsScreen(onBack = { nav.popBackStack() }) }
                    composable(
                        route = "phrase/{id}?private={private}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        PhraseEditScreen(id = id, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable("assistant") { AssistantScreen(onBack = { nav.popBackStack() }) }
                    composable(
                        route = "list?tab={tab}",
                        arguments = listOf(navArgument("tab") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        ListScreen(
                            title = "数据",
                            initialTab = OpenTarget.tabOf(entry.arguments?.getString("tab"), Features.allergy),
                            onBack = { nav.popBackStack() },
                            onNewDiary = { nav.navigate("diary/new?date=${LocalDate.now()}") },
                            onNewReminder = { nav.navigate("reminder/new?date=${LocalDate.now()}") },
                            onNewFitness = { nav.navigate("fitness?date=${LocalDate.now()}") },
                            onNewAlarm = { nav.navigate("alarm/new") },
                            onNewAllergy = { nav.navigate("allergy?date=${LocalDate.now()}") },
                            onNewMed = { nav.navigate("med/new") },
                            onNewPhrase = { nav.navigate("phrase/new") },
                            onNewBirthday = { nav.navigate("birthday/new") },
                            onEditDiary = { id -> nav.navigate("diary/$id") },
                            onEditReminder = { id -> nav.navigate("reminder/$id") },
                            onEditFitness = { date -> nav.navigate("fitness?date=$date") },
                            onEditAlarm = { id -> nav.navigate("alarm/$id") },
                            onEditAllergy = { date -> nav.navigate("allergy?date=$date") },
                            onEditMed = { id -> nav.navigate("med/$id") },
                            onEditRecord = { id -> nav.navigate("visit/$id") },
                            onEditPhrase = { id -> nav.navigate("phrase/$id") },
                            onEditBirthday = { id -> nav.navigate("birthday/$id") }
                        )
                    }
                    composable(
                        route = "fitness?date={date}&private={private}",
                        arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "" }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val date = entry.arguments?.getString("date").orEmpty()
                            .let { s -> try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.now() } }
                            .let { if (it.isAfter(LocalDate.now())) LocalDate.now() else it }   // 记录不能记在将来
                        FitnessEditScreen(initialDate = date, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = "allergy?date={date}&private={private}",
                        arguments = listOf(navArgument("date") { type = NavType.StringType; defaultValue = "" }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val date = entry.arguments?.getString("date").orEmpty()
                            .let { s -> try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.now() } }
                            .let { if (it.isAfter(LocalDate.now())) LocalDate.now() else it }
                        AllergyEditScreen(initialDate = date, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = "birthday/{id}?private={private}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        BirthdayEditScreen(id = id, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = "reminder/{id}?date={date}&private={private}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("date") { type = NavType.StringType; defaultValue = "" },
                            navArgument("private") { type = NavType.StringType; defaultValue = "0" }
                        )
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        val date = entry.arguments?.getString("date").orEmpty()
                            .let { s -> try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.now() } }
                        ReminderEditScreen(id = id, initialDate = date, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = "alarm/{id}?private={private}",
                        arguments = listOf(navArgument("id") { type = NavType.StringType }, navArgument("private") { type = NavType.StringType; defaultValue = "0" })
                    ) { entry ->
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        AlarmEditScreen(id = id, onBack = { nav.popBackStack() }, initialPrivate = entry.arguments?.getString("private") == "1")
                    }
                    composable(
                        route = DIARY_ROUTE,
                        arguments = listOf(
                            navArgument("id") { type = NavType.StringType },
                            navArgument("date") { type = NavType.StringType; defaultValue = "" },
                            navArgument("private") { type = NavType.StringType; defaultValue = "0" }
                        )
                    ) { entry ->
                        val diaryVm: DiaryEditViewModel = viewModel(entry)
                        val id = entry.arguments?.getString("id")?.takeIf { it != "new" }
                        val date = entry.arguments?.getString("date").orEmpty()
                            .let { s -> try { LocalDate.parse(s) } catch (e: Exception) { LocalDate.now() } }
                        DiaryEditScreen(
                            id = id,
                            initialDate = date,
                            vm = diaryVm,
                            onBack = { nav.popBackStack() },
                            onPickLocation = { nav.navigate("diaryLocation") },
                            initialPrivate = entry.arguments?.getString("private") == "1"
                        )
                    }
                    composable("diaryLocation") { entry ->
                        val parentEntry = remember(entry) { nav.getBackStackEntry(DIARY_ROUTE) }
                        val diaryVm: DiaryEditViewModel = viewModel(parentEntry)
                        DiaryLocationPickerScreen(vm = diaryVm, onBack = { nav.popBackStack() })
                    }
                }
            }
        }
    }
}
