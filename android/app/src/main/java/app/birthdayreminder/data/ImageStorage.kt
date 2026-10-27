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

    /**
     * 粘贴进来的图片地址（网址或 data:）-> 存进 App 的相对路径；不是图片、太大或下载失败返回 null。
     * 会联网，要在后台线程调用。
     */
    fun saveFromAddress(context: Context, src: String): String? = try {
        val bytes: ByteArray?
        var mime: String? = null
        if (src.startsWith("data:", ignoreCase = true)) {
            val comma = src.indexOf(',')
            mime = src.substring(5, src.indexOf(';').takeIf { it in 5 until comma } ?: comma)
            bytes = android.util.Base64.decode(src.substring(comma + 1), android.util.Base64.DEFAULT)
        } else if (src.startsWith("https://", ignoreCase = true) || src.startsWith("http://", ignoreCase = true)) {
            val conn = java.net.URL(src).openConnection() as java.net.HttpURLConnection
            bytes = try {
                conn.connectTimeout = 10_000
                conn.readTimeout = 20_000
                mime = conn.contentType?.substringBefore(';')?.trim()
                // 网页等其它内容不用下载
                if (conn.responseCode !in 200..299 || (mime != null && !mime.startsWith("image/"))) null
                else {
                    conn.inputStream.use { ins ->
                        val out = java.io.ByteArrayOutputStream()
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = ins.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            if (out.size() > MAX_PASTE_BYTES) return@use null
                        }
                        out.toByteArray()
                    }
                }
            } finally {
                conn.disconnect()
            }
        } else bytes = null
        // 确认真的是图片（网址可能是网页）
        val ok = bytes != null && android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            .also { android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, it) }.outWidth > 0
        if (!ok) null
        else {
            val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg" }
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.$ext")
            file.writeBytes(bytes!!)
            "$DIR/${file.name}"
        }
    } catch (e: Exception) {
        null
    }

    private const val MAX_PASTE_BYTES = 20 * 1024 * 1024

    fun resolve(context: Context, relativePath: String): File = File(context.filesDir, relativePath)

    fun delete(context: Context, relativePath: String) {
        resolve(context, relativePath).let { if (it.exists()) it.delete() }
    }
}
