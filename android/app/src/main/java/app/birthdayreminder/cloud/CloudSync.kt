package app.birthdayreminder.cloud

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableIntStateOf
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.birthdayreminder.data.BackupRunner
import app.birthdayreminder.domain.CloudFile
import app.birthdayreminder.domain.CloudPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.time.ZoneId
import java.util.concurrent.TimeUnit

/**
 * 云盘备份：按设置定时（每天 / 每周）在后台生成一份完整备份（数据、设置、Key、图片、附件、铃声）上传，
 * 云盘上只保留最新几份；也可以立即上传、从云盘恢复。
 */
object CloudSync {
    private const val PERIODIC = "cloud_backup_periodic"
    private const val NOW = "cloud_backup_now"
    /** 上传状态变了就 +1，界面刷新用 */
    val version = mutableIntStateOf(0)

    fun storage(ctx: Context): CloudStorage? = when (CloudConfig.provider(ctx)) {
        "onedrive" -> if (OneDriveAuth.signedIn(ctx)) OneDriveStorage(ctx.applicationContext) else null
        "webdav" -> CloudConfig.davUrl(ctx).takeIf { it.isNotBlank() }?.let {
            WebDavStorage(it, CloudConfig.davFolder(ctx), CloudConfig.davUser(ctx), CloudConfig.davPass(ctx))
        }
        else -> null
    }

    private fun constraints(ctx: Context, forceAnyNetwork: Boolean) = Constraints.Builder()
        .setRequiredNetworkType(if (!forceAnyNetwork && CloudConfig.wifiOnly(ctx)) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(!forceAnyNetwork && CloudConfig.chargingOnly(ctx))
        .build()

    /** 按设置排好定时上传（关掉就取消） */
    fun schedule(ctx: Context) {
        val wm = WorkManager.getInstance(ctx.applicationContext)
        val freq = CloudConfig.freq(ctx)
        if (CloudConfig.provider(ctx) == "off" || freq == "off") { wm.cancelUniqueWork(PERIODIC); return }
        val days = if (freq == "weekly") 7L else 1L
        wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<CloudBackupWorker>(days, TimeUnit.DAYS).setConstraints(constraints(ctx, false)).build())
    }

    /** 立即上传：有网就传（不管仅 Wi-Fi / 仅充电） */
    fun uploadNow(ctx: Context) {
        WorkManager.getInstance(ctx.applicationContext).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<CloudBackupWorker>().setConstraints(constraints(ctx, true)).build())
    }

    /** 真正干活：生成备份 → 上传 → 删掉云盘上多余的旧备份。返回失败原因，成功返回 null */
    suspend fun runUpload(ctx: Context): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val st = storage(app) ?: return@withContext "云盘还没有设置好"
        val dir = File(app.cacheDir, "cloud_upload").apply { deleteRecursively(); mkdirs() }
        val name = CloudPlan.fileName(System.currentTimeMillis(), ZoneId.systemDefault())
        val f = File(dir, name)
        try {
            f.outputStream().use { BackupRunner.writeArchive(app, it) }
            st.upload(f, name)
            try { CloudPlan.toDelete(st.list(), CloudConfig.keep(app)).forEach { st.delete(it) } } catch (e: Exception) { }
            CloudConfig.setResult(app, null)
            null
        } catch (e: Exception) {
            val msg = e.message ?: "上传失败"
            CloudConfig.setResult(app, msg)
            msg
        } finally {
            dir.deleteRecursively()
            version.intValue++
        }
    }

    suspend fun listBackups(ctx: Context): List<CloudFile> =
        CloudPlan.restorable((storage(ctx) ?: throw CloudException("云盘还没有设置好")).list())

    /** 下载一份备份到临时文件，之后交给和本地恢复一样的流程 */
    suspend fun download(ctx: Context, name: String): File = withContext(Dispatchers.IO) {
        val st = storage(ctx) ?: throw CloudException("云盘还没有设置好")
        val f = File(File(ctx.cacheDir, "cloud_download").apply { deleteRecursively(); mkdirs() }, name)
        st.download(name, f)
        f
    }

    fun notifyFailure(ctx: Context, msg: String) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26 && nm.getNotificationChannel("cloud") == null)
            nm.createNotificationChannel(NotificationChannel("cloud", "云盘备份", NotificationManager.IMPORTANCE_DEFAULT))
        val n = NotificationCompat.Builder(ctx, "cloud").setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("云盘备份没有成功").setContentText(msg).setStyle(NotificationCompat.BigTextStyle().bigText(msg)).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(ctx).notify(7301, n) } catch (e: SecurityException) { }
    }
}

/** 后台上传：失败会自动重试（最多 3 次），还不行就发通知 */
class CloudBackupWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val err = CloudSync.runUpload(applicationContext) ?: return Result.success()
        if (err == "云盘还没有设置好") return Result.success()
        return if (runAttemptCount < 2) Result.retry() else { CloudSync.notifyFailure(applicationContext, err); Result.success() }
    }
}
