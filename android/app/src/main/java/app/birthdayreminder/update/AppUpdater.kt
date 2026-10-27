package app.birthdayreminder.update

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** 只给更新安装包用的 FileProvider（自己的类名，免得和别的库的 FileProvider 在清单里撞名） */
class UpdateFileProvider : FileProvider()

/**
 * 应用内更新：查 GitHub Releases 上最新的版本，比当前版本新就提示，下载安装包后交给系统安装。
 *  - 自动检查（[autoCheck]）：打开 App 时最多一天查一次，失败不打扰；用户点了“跳过此版本”的不再提示
 *  - 手动检查（[checkNow]）：设置里的“检查更新”，结果都会告诉用户
 * 下载完先核对安装包的包名，和自己不一样（比如下错了版本）就不装。
 * 用的是系统自带的对话框和网络库，不依赖界面框架。
 */
class AppUpdater(
    /** 例如 "clusflah/riji" */
    private val repo: String,
    /** 哪些发布算数（比如同一个仓库里还有别的版本线） */
    private val acceptTag: (String) -> Boolean = { true },
    /** 从这次发布的安装包文件名里挑出适合这台手机、这个版本的那个；没有返回 null */
    private val pickAsset: (List<String>) -> String?,
) {
    class Release(val tag: String, val notes: String, val url: String, val fileName: String, val size: Long, val page: String)

    private val main = Handler(Looper.getMainLooper())

    /** 装在手机上的版本号（versionName） */
    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "0"

    /** 最新的可用发布；网络错误会抛出异常（调用方决定要不要告诉用户） */
    fun fetchLatest(): Release? {
        val conn = URL("https://api.github.com/repos/$repo/releases?per_page=20").openConnection() as HttpURLConnection
        val text = try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.setRequestProperty("User-Agent", repo)
            if (conn.responseCode !in 200..299) throw java.io.IOException("GitHub 返回 ${conn.responseCode}")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
        val list = JSONArray(text)
        for (i in 0 until list.length()) {
            val r = list.getJSONObject(i)
            if (r.optBoolean("draft") || r.optBoolean("prerelease")) continue
            val tag = r.optString("tag_name")
            if (!acceptTag(tag)) continue
            val assets = r.optJSONArray("assets") ?: continue
            val names = (0 until assets.length()).map { assets.getJSONObject(it).optString("name") }
            val pick = pickAsset(names) ?: continue
            val a = assets.getJSONObject(names.indexOf(pick))
            return Release(tag, r.optString("body"), a.optString("browser_download_url"), pick, a.optLong("size"), r.optString("html_url"))
        }
        return null
    }

    /** 打开 App 时调用：一天最多查一次，有新版本才弹窗 */
    fun autoCheck(activity: Activity) {
        val prefs = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("last_check", 0) < 20 * 60 * 60 * 1000L) return
        prefs.edit().putLong("last_check", now).apply()
        Thread {
            val rel = runCatching { fetchLatest() }.getOrNull() ?: return@Thread
            if (!isNewer(rel.tag, currentVersion(activity)) || rel.tag == prefs.getString("skipped", null)) return@Thread
            main.post { if (!activity.isFinishing && !activity.isDestroyed) showRelease(activity, rel, auto = true) }
        }.start()
    }

    /** 设置里的“检查更新” */
    fun checkNow(activity: Activity) {
        Toast.makeText(activity, "正在检查更新…", Toast.LENGTH_SHORT).show()
        Thread {
            val result = runCatching { fetchLatest() }
            main.post {
                if (activity.isFinishing || activity.isDestroyed) return@post
                val rel = result.getOrElse {
                    Toast.makeText(activity, "检查更新失败：${it.message ?: it.javaClass.simpleName}（访问 GitHub 可能需要代理）", Toast.LENGTH_LONG).show()
                    return@post
                }
                if (rel == null || !isNewer(rel.tag, currentVersion(activity))) {
                    Toast.makeText(activity, "已是最新版本（${currentVersion(activity)}）", Toast.LENGTH_SHORT).show()
                } else showRelease(activity, rel, auto = false)
            }
        }.start()
    }

    private fun showRelease(activity: Activity, rel: Release, auto: Boolean) {
        val notes = rel.notes.trim().let { if (it.length > 1500) it.take(1500) + "…" else it }
        val size = if (rel.size > 0) "安装包 %.1f MB\n\n".format(rel.size / 1048576.0) else ""
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${rel.tag}")
            .setMessage("当前版本 ${currentVersion(activity)}\n$size$notes")
            .setPositiveButton("下载并安装") { _, _ -> download(activity, rel) }
            .setNegativeButton("以后再说", null)
            .apply {
                if (auto) setNeutralButton("跳过此版本") { _, _ ->
                    activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("skipped", rel.tag).apply()
                }
            }
            .show()
    }

    private fun download(activity: Activity, rel: Release) {
        val bar = ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; isIndeterminate = rel.size <= 0 }
        val label = TextView(activity).apply { text = "正在下载 ${rel.fileName}" }
        val box = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(label); addView(bar)
        }
        val cancelled = java.util.concurrent.atomic.AtomicBoolean(false)
        val dialog = AlertDialog.Builder(activity)
            .setTitle("下载更新")
            .setView(box)
            .setCancelable(false)
            .setNegativeButton("取消") { _, _ -> cancelled.set(true) }
            .show()
        Thread {
            val dir = File(activity.cacheDir, DIR).apply { deleteRecursively(); mkdirs() }
            val file = File(dir, rel.fileName)
            val result = runCatching {
                val conn = URL(rel.url).openConnection() as HttpURLConnection
                try {
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.instanceFollowRedirects = true
                    if (conn.responseCode !in 200..299) throw java.io.IOException("下载失败 ${conn.responseCode}")
                    val total = conn.contentLengthLong.takeIf { it > 0 } ?: rel.size
                    var done = 0L
                    var shown = -1
                    conn.inputStream.use { ins ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                if (cancelled.get()) throw InterruptedException()
                                val n = ins.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                val pct = if (total > 0) (done * 100 / total).toInt() else -1
                                if (pct != shown) {
                                    shown = pct
                                    main.post { if (pct >= 0) { bar.isIndeterminate = false; bar.progress = pct }; label.text = "正在下载 ${rel.fileName}  ${if (pct >= 0) "$pct%" else ""}" }
                                }
                            }
                        }
                    }
                } finally {
                    conn.disconnect()
                }
                // 核对包名：下错了（别的 App、别的版本线）就不装
                val info = activity.packageManager.getPackageArchiveInfo(file.path, 0)
                if (info?.packageName != activity.packageName) throw IllegalStateException("安装包不是这个 App 的（${info?.packageName ?: "无法读取"}）")
                file
            }
            main.post {
                runCatching { dialog.dismiss() }
                if (activity.isFinishing || activity.isDestroyed) return@post
                result.onSuccess { install(activity, it) }.onFailure { e ->
                    if (e is InterruptedException) return@onFailure
                    AlertDialog.Builder(activity)
                        .setTitle("下载失败")
                        .setMessage((e.message ?: e.javaClass.simpleName) + "\n\n访问 GitHub 可能需要代理；也可以到网页上手动下载。")
                        .setPositiveButton("去网页下载") { _, _ -> openPage(activity, rel.page) }
                        .setNegativeButton("关闭", null)
                        .show()
                }
            }
        }.start()
    }

    private fun install(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !activity.packageManager.canRequestPackageInstalls()) {
            AlertDialog.Builder(activity)
                .setTitle("需要允许安装应用")
                .setMessage("第一次更新要允许这个 App“安装未知应用”。打开设置开启后返回，再点“安装”。")
                .setPositiveButton("去设置") { _, _ ->
                    runCatching {
                        activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                    }
                    // 回来后接着装
                    AlertDialog.Builder(activity)
                        .setMessage("开启后点“安装”。")
                        .setPositiveButton("安装") { _, _ -> install(activity, file) }
                        .setNegativeButton("取消", null)
                        .show()
                }
                .setNegativeButton("取消", null)
                .show()
            return
        }
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.update", file)
        runCatching {
            activity.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }.onFailure { Toast.makeText(activity, "无法打开安装程序：${it.message}", Toast.LENGTH_LONG).show() }
    }

    private fun openPage(activity: Activity, url: String) {
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    companion object {
        private const val PREFS = "app_update"
        private const val DIR = "updates"

        /** 版本号里的数字依次比较：v1.10.0 > 1.9.2，v0.1.3-mod.91 > 0.1.3-mod.89，0.1.3-mod.91 > 0.1.3-mod */
        fun isNewer(remote: String, current: String): Boolean {
            val a = Regex("\\d+").findAll(remote).map { it.value.toLong() }.toList()
            val b = Regex("\\d+").findAll(current).map { it.value.toLong() }.toList()
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }

        /** 这台手机能用的 ABI，按系统推荐的顺序 */
        val abis: List<String> get() = Build.SUPPORTED_ABIS.toList()
    }
}

/** 日迹的更新来源：GitHub 上 riji 的 Release；完整版装 riji-x.y.z-full.apk，无过敏记录版装 riji-x.y.z-noAllergy.apk */
val appUpdater = AppUpdater(
    repo = "clusflah/riji",
    pickAsset = { names ->
        val suffix = if (app.birthdayreminder.Features.allergy) "-full.apk" else "-noAllergy.apk"
        names.firstOrNull { it.startsWith("riji-") && it.endsWith(suffix) }
    },
)
