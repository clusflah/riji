package app.birthdayreminder.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import app.birthdayreminder.domain.AutoBackupPlan
import app.birthdayreminder.notify.SettingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 自动备份：按设置的频率（每天 / 每周），把完整备份写进你选的文件夹（比如网盘同步文件夹），
 * 只保留最新 7 份自动备份（手动导出的、别的文件一律不动）。
 * 在每次打开 App 时检查是否到时间——数据只会在用 App 的时候变化，所以这样既简单又不会漏。
 */
object AutoBackup {

    /** 用户在设置里选了文件夹之后调用：保存授权，App 重启后也能继续写 */
    fun rememberFolder(ctx: Context, tree: Uri) {
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try { ctx.contentResolver.takePersistableUriPermission(tree, flags) } catch (e: Exception) { }
        SettingsStore.update(ctx) { it.copy(autoBackupDir = tree.toString(), autoBackupError = "") }
    }

    /** 文件夹显示名，比如 “Download/日迹备份” */
    fun folderLabel(dir: String): String {
        if (dir.isBlank()) return "还没有选择"
        return try {
            val id = DocumentsContract.getTreeDocumentId(Uri.parse(dir))
            val path = id.substringAfter(':', id)
            if (path.isBlank()) "手机存储根目录" else path
        } catch (e: Exception) { "已选择" }
    }

    /** 到时间了就备份一次；force = true 时不管时间（“立即备份一次”按钮）。返回 null 表示成功或不需要，否则是失败原因 */
    suspend fun runIfDue(ctx: Context, force: Boolean = false): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        val s = SettingsStore.get(app)
        if (s.autoBackupDir.isBlank()) return@withContext if (force) "请先选择备份文件夹" else null
        if (!force && !AutoBackupPlan.isDue(s.autoBackup, s.lastAutoBackup, System.currentTimeMillis())) return@withContext null
        val err = try {
            write(app, Uri.parse(s.autoBackupDir))
            null
        } catch (e: SecurityException) {
            "没有权限写入备份文件夹（可能被移动或删除了），请重新选择"
        } catch (e: Exception) {
            "自动备份失败：" + (e.message ?: "未知错误")
        }
        SettingsStore.update(app) {
            if (err == null) it.copy(lastAutoBackup = System.currentTimeMillis(), autoBackupError = "")
            else it.copy(autoBackupError = err)
        }
        err
    }

    private suspend fun write(ctx: Context, tree: Uri) {
        val cr = ctx.contentResolver
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val doc = DocumentsContract.createDocument(cr, parent, "application/zip", AutoBackupPlan.fileName(System.currentTimeMillis()))
            ?: throw IllegalStateException("无法在备份文件夹里新建文件")
        // 压缩包：数据、设置和 Key + 图片、附件、铃声
        cr.openOutputStream(doc, "w")?.use { BackupRunner.writeArchive(ctx, it) }
            ?: throw IllegalStateException("无法写入备份文件")

        // 清理：只留最新 7 份自动备份
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val byName = HashMap<String, String>()
        cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                byName[name] = id
            }
        }
        for (name in AutoBackupPlan.toDelete(byName.keys.toList())) {
            try { DocumentsContract.deleteDocument(cr, DocumentsContract.buildDocumentUriUsingTree(tree, byName.getValue(name))) } catch (e: Exception) { }
        }
    }
}
