package app.birthdayreminder.health

import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.health.connect.client.PermissionController
import kotlinx.coroutines.launch

/**
 * “从 Health Connect 同步”按钮：自动处理“没装 Health Connect / 没授权”，授权后执行 job，job 返回要提示的话。
 * Android 7.x 不支持 Health Connect，这个按钮不显示（调用方也不用判断）。
 */
@Composable
fun HealthSyncButton(label: String, job: suspend () -> String) {
    if (Build.VERSION.SDK_INT < 26) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    fun run() {
        busy = true
        scope.launch {
            val msg = try { job() } catch (e: Exception) { "同步失败：" + (e.message ?: "未知错误") }
            busy = false
            Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show()
        }
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.isEmpty()) Toast.makeText(ctx, "没有授权读取健康数据，可以在系统设置 → Health Connect 里打开", Toast.LENGTH_LONG).show()
        else run()
    }
    OutlinedButton(enabled = !busy, onClick = {
        when (HealthConnectSource.state(ctx)) {
            HealthConnectSource.State.UNSUPPORTED -> Toast.makeText(ctx, "这台手机不支持 Health Connect", Toast.LENGTH_LONG).show()
            HealthConnectSource.State.NEED_INSTALL -> {
                Toast.makeText(ctx, "需要先安装或更新 Health Connect", Toast.LENGTH_LONG).show()
                try { ctx.startActivity(HealthConnectSource.installIntent()) } catch (e: Exception) { }
            }
            HealthConnectSource.State.OK -> scope.launch {
                // 已经授权了任意一种就直接同步（没授权的那几种会跳过）；一种都没授权就弹出授权界面
                if (HealthConnectSource.granted(ctx).any { it in HealthConnectSource.PERMISSIONS }) run()
                else launcher.launch(HealthConnectSource.PERMISSIONS)
            }
        }
    }) { Text(if (busy) "同步中…" else label) }
}

/**
 * “自动同步”开关 + 上次同步的说明。打开时申请读取权限（顺带申请后台读取，系统支持才会有），
 * 授权后马上同步一次；有后台权限就每 6 小时在后台同步，没有就在每次打开 App 时同步。
 */
@Composable
fun HealthAutoSyncRow() {
    if (Build.VERSION.SDK_INT < 26) return
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val ver by HealthAutoSync.version
    val on = remember(ver) { HealthAutoSync.enabled(ctx) }
    val status = remember(ver) { HealthAutoSync.status(ctx) }
    var bg by remember { mutableStateOf<Boolean?>(null) }
    androidx.compose.runtime.LaunchedEffect(ver) { bg = HealthAutoSync.backgroundGranted(ctx) }

    fun enableAndRun() {
        HealthAutoSync.setEnabled(ctx, true)
        scope.launch {
            val msg = HealthAutoSync.runNow(ctx, background = false)
            val hasBg = HealthAutoSync.backgroundGranted(ctx)
            Toast.makeText(ctx, msg + if (hasBg) "。之后每 6 小时在后台自动同步" else "。之后每次打开日迹时自动同步", Toast.LENGTH_LONG).show()
        }
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
        if (granted.any { it in HealthConnectSource.PERMISSIONS }) enableAndRun()
        else Toast.makeText(ctx, "没有授权读取健康数据，自动同步没有打开", Toast.LENGTH_LONG).show()
    }
    androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.weight(1f)) {
            Text("自动同步")
            Text(
                if (!on) "打开后自动从 Health Connect 补上最近 7 天的数据"
                else status + "\n" + (if (bg == true) "后台每 6 小时同步一次，打开日迹时也会同步" else "每次打开日迹时同步（这台手机没有给后台读取权限）"),
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(checked = on, onCheckedChange = { want ->
            if (!want) { HealthAutoSync.setEnabled(ctx, false); return@Switch }
            when (HealthConnectSource.state(ctx)) {
                HealthConnectSource.State.UNSUPPORTED -> Toast.makeText(ctx, "这台手机不支持 Health Connect", Toast.LENGTH_LONG).show()
                HealthConnectSource.State.NEED_INSTALL -> {
                    Toast.makeText(ctx, "需要先安装或更新 Health Connect", Toast.LENGTH_LONG).show()
                    try { ctx.startActivity(HealthConnectSource.installIntent()) } catch (e: Exception) { }
                }
                HealthConnectSource.State.OK -> scope.launch {
                    val want2 = HealthConnectSource.PERMISSIONS + HealthAutoSync.BACKGROUND_PERMISSION
                    if (HealthConnectSource.granted(ctx).containsAll(want2)) enableAndRun() else launcher.launch(want2)
                }
            }
        })
    }
}
