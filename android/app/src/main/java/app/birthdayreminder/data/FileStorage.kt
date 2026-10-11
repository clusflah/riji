package app.birthdayreminder.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.UUID

/**
 * 日记附件：复制到 App 私有目录 diary_files/，数据库里只存相对路径和原来的文件名。
 * 打开时通过 FileProvider 临时授权给别的 App（比如 WPS、PDF 阅读器）读取。
 */
object FileStorage {
    private const val DIR = "diary_files"
    const val MAX_BYTES = 50L * 1024 * 1024      // 单个附件最大 50MB
    private fun authority(ctx: Context) = ctx.packageName + ".files"

    sealed class Saved {
        data class Ok(val path: String, val name: String) : Saved()
        data class Fail(val message: String) : Saved()
    }

    /** 复制用户选的文件；保留原扩展名（打开时系统靠它判断用什么 App） */
    fun saveFromUri(ctx: Context, uri: Uri): Saved {
        var name = "附件"
        var size = -1L
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0 && !c.isNull(ni)) name = c.getString(ni)
                    if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
                }
            }
        } catch (e: Exception) { }
        if (size > MAX_BYTES) return Saved.Fail("文件太大了，附件最大 50MB")
        val ext = name.substringAfterLast('.', "").lowercase().filter { it.isLetterOrDigit() }.take(8)
            .ifEmpty { MimeTypeMap.getSingleton().getExtensionFromMimeType(ctx.contentResolver.getType(uri)) ?: "bin" }
        val dir = File(ctx.filesDir, DIR).apply { mkdirs() }
        val f = File(dir, "${UUID.randomUUID()}.$ext")
        return try {
            val input = ctx.contentResolver.openInputStream(uri) ?: return Saved.Fail("打不开这个文件")
            var total = 0L
            input.use { ins ->
                f.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        val n = ins.read(buf)
                        if (n <= 0) break
                        total += n
                        if (total > MAX_BYTES) { out.close(); f.delete(); return Saved.Fail("文件太大了，附件最大 50MB") }
                        out.write(buf, 0, n)
                    }
                }
            }
            Saved.Ok("$DIR/${f.name}", name)
        } catch (e: Exception) {
            f.delete()
            Saved.Fail("复制文件失败")
        }
    }

    fun file(ctx: Context, path: String): File = File(ctx.filesDir, path)

    fun delete(ctx: Context, path: String) {
        file(ctx, path).let { if (it.exists()) it.delete() }
    }

    /** 文件大小的显示：850 B / 12.3 KB / 4.5 MB；文件已经不在返回 null */
    fun sizeText(ctx: Context, path: String): String? {
        val f = file(ctx, path)
        if (!f.exists()) return null
        val b = f.length()
        return when {
            b < 1024 -> "$b B"
            b < 1024 * 1024 -> "%.1f KB".format(b / 1024.0)
            else -> "%.1f MB".format(b / 1024.0 / 1024.0)
        }
    }

    /** 用手机上能打开这种文件的 App 打开；返回 null 表示成功，否则是原因 */
    fun open(ctx: Context, path: String, name: String): String? {
        val f = file(ctx, path)
        if (!f.exists()) return "这个附件的文件已经不在了"
        val uri = try { FileProvider.getUriForFile(ctx, authority(ctx), f) } catch (e: Exception) { return "没法打开这个附件" }
        val ext = name.substringAfterLast('.', f.extension).lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            ctx.startActivity(Intent.createChooser(i, "打开 $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); null
        } catch (e: ActivityNotFoundException) {
            "手机上没有能打开这种文件的 App"
        }
    }
}
