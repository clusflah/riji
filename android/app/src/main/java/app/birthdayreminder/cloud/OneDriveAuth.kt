package app.birthdayreminder.cloud

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.mutableIntStateOf
import app.birthdayreminder.MainActivityCompose
import app.birthdayreminder.domain.OneDriveApi
import app.birthdayreminder.domain.Pkce
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import java.security.SecureRandom

/**
 * OneDrive 登录（OAuth 2.0 + PKCE）：在浏览器里登录微软账号，日迹不接触密码；
 * 登录完浏览器跳回 rijii://onedrive-auth，日迹用一次性的授权码换登录令牌。
 */
object OneDriveAuth {
    /** 登录状态变了就 +1，界面刷新用 */
    val version = mutableIntStateOf(0)
    private val lock = Mutex()

    fun start(activity: Activity): String? {
        val clientId = CloudConfig.clientId(activity)
        if (clientId.isBlank()) return "请先填写 OneDrive 应用的客户端 ID"
        val rnd = SecureRandom()
        val verifier = Pkce.verifier(ByteArray(32).also { rnd.nextBytes(it) })
        val state = Pkce.verifier(ByteArray(16).also { rnd.nextBytes(it) })
        CloudConfig.savePending(activity, verifier, state)
        return try {
            activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(OneDriveApi.authorizeUrl(clientId, Pkce.challenge(verifier), state))))
            null
        } catch (e: Exception) { "没有找到浏览器" }
    }

    /** 浏览器跳回来：核对 state（防伪造），用授权码换令牌，再读一下账号名 */
    suspend fun complete(ctx: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        val app = ctx.applicationContext
        uri.getQueryParameter("error")?.let { return@withContext "登录取消或失败：" + (uri.getQueryParameter("error_description") ?: it) }
        val code = uri.getQueryParameter("code") ?: return@withContext "登录失败：没有拿到授权码"
        if (uri.getQueryParameter("state") != CloudConfig.pendingState(app)) return@withContext "登录失败：请重新登录一次"
        val verifier = CloudConfig.pendingVerifier(app) ?: return@withContext "登录失败：请重新登录一次"
        CloudConfig.clearPending(app)
        val err = tokenRequest(app, OneDriveApi.codeForm(CloudConfig.clientId(app), code, verifier))
        if (err != null) return@withContext err
        try {
            Http.client.newCall(Request.Builder().url(OneDriveApi.ME_URL).header("Authorization", "Bearer " + accessToken(app)).build()).execute().use { r ->
                if (r.isSuccessful) OneDriveApi.parseAccount(r.body?.string() ?: "")?.let { CloudConfig.setOdAccount(app, it) }
            }
        } catch (e: Exception) { }
        version.intValue++
        null
    }

    private fun tokenRequest(ctx: Context, form: Map<String, String>): String? {
        val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
        Http.client.newCall(Request.Builder().url(OneDriveApi.AUTH_BASE + "/token").post(body).build()).execute().use { r ->
            val text = r.body?.string() ?: ""
            if (!r.isSuccessful) return "登录失败：" + (OneDriveApi.parseError(text) ?: "错误 ${r.code}")
            val t = OneDriveApi.parseToken(text, System.currentTimeMillis()) ?: return "登录失败：返回内容看不懂"
            CloudConfig.saveOdTokens(ctx, t.access, t.refresh, t.expiresAtMs)
            return null
        }
    }

    /** 拿可用的令牌：快过期了就用刷新令牌换新的（多个任务同时要时只换一次） */
    suspend fun accessToken(ctx: Context): String = lock.withLock {
        val app = ctx.applicationContext
        val cur = CloudConfig.odAccess(app)
        if (cur != null && System.currentTimeMillis() < CloudConfig.odExpires(app)) return@withLock cur
        val refresh = CloudConfig.odRefresh(app) ?: throw CloudException("OneDrive 还没有登录")
        val err = withContext(Dispatchers.IO) { tokenRequest(app, OneDriveApi.refreshForm(CloudConfig.clientId(app), refresh)) }
        if (err != null) throw CloudException("OneDrive 登录已失效，请重新登录（$err）")
        CloudConfig.odAccess(app) ?: throw CloudException("OneDrive 登录已失效，请重新登录")
    }

    fun signOut(ctx: Context) { CloudConfig.clearOneDrive(ctx); version.intValue++ }

    fun signedIn(ctx: Context) = CloudConfig.odRefresh(ctx) != null
}

/** 接住浏览器跳回来的 rijii://onedrive-auth?code=…，换完令牌回到日迹 */
class OAuthRedirectActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uri = intent?.data
        val app = applicationContext
        if (uri != null) CoroutineScope(Dispatchers.Main).launch {
            val err = OneDriveAuth.complete(app, uri)
            Toast.makeText(app, err ?: "OneDrive 已登录", Toast.LENGTH_LONG).show()
        }
        startActivity(Intent(this, MainActivityCompose::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
        finish()
    }
}
