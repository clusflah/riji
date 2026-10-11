package app.birthdayreminder.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID

/** 日记图片存到 App 私有目录，数据库里只存相对路径，换设备/重装不会留一堆断链。 */
object ImageStorage {
    private const val DIR = "diary_images"

    fun saveFromUri(context: Context, uri: Uri, mimeType: String?): String? {
        return try {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val ext = when (mimeType) {
                "image/png" -> "png"
                "image/webp" -> "webp"
                "image/gif" -> "gif"
                else -> "jpg"
            }
            val file = File(dir, "${UUID.randomUUID()}.$ext")
            val input = context.contentResolver.openInputStream(uri) ?: return null
            input.use { ins ->
                file.outputStream().use { out -> ins.copyTo(out) }
            }
            "$DIR/${file.name}"
        } catch (e: Exception) {
            null
        }
    }

    fun resolve(context: Context, relativePath: String): File = File(context.filesDir, relativePath)

    fun delete(context: Context, relativePath: String) {
        resolve(context, relativePath).let { if (it.exists()) it.delete() }
    }
}
