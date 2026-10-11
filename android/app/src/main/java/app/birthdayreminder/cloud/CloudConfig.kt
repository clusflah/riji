package app.birthdayreminder.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * 云盘备份的设置。单独一个设置文件（不放进备份：换手机后重新登录一次即可）；
 * OneDrive 的登录令牌、WebDAV 密码用安卓系统密钥库加密后保存。
 */
object CloudConfig {
    private const val FILE = "cloud_sync"
    private fun sp(ctx: Context) = ctx.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    /** off / onedrive / webdav */
    fun provider(ctx: Context) = sp(ctx).getString("provider", "off") ?: "off"
    fun setProvider(ctx: Context, v: String) = sp(ctx).edit().putString("provider", v).apply()
    /** off / daily / weekly */
    fun freq(ctx: Context) = sp(ctx).getString("freq", "daily") ?: "daily"
    fun setFreq(ctx: Context, v: String) = sp(ctx).edit().putString("freq", v).apply()
    fun wifiOnly(ctx: Context) = sp(ctx).getBoolean("wifi", true)
    fun setWifiOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("wifi", v).apply()
    fun chargingOnly(ctx: Context) = sp(ctx).getBoolean("charging", false)
    fun setChargingOnly(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("charging", v).apply()
    fun keep(ctx: Context) = sp(ctx).getInt("keep", 7)
    fun setKeep(ctx: Context, v: Int) = sp(ctx).edit().putInt("keep", v).apply()

    // ---- OneDrive ----
    fun clientId(ctx: Context) = sp(ctx).getString("odClient", "") ?: ""
    fun setClientId(ctx: Context, v: String) = sp(ctx).edit().putString("odClient", v.trim()).apply()
    fun odAccount(ctx: Context) = sp(ctx).getString("odAccount", "") ?: ""
    fun odAccess(ctx: Context) = decrypt(sp(ctx).getString("odAccess", null))
    fun odRefresh(ctx: Context) = decrypt(sp(ctx).getString("odRefresh", null))
    fun odExpires(ctx: Context) = sp(ctx).getLong("odExp", 0L)
    fun saveOdTokens(ctx: Context, access: String, refresh: String?, expiresAt: Long) {
        val e = sp(ctx).edit().putString("odAccess", encrypt(access)).putLong("odExp", expiresAt)
        if (refresh != null) e.putString("odRefresh", encrypt(refresh))
        e.apply()
    }
    fun setOdAccount(ctx: Context, v: String) = sp(ctx).edit().putString("odAccount", v).apply()
    fun clearOneDrive(ctx: Context) = sp(ctx).edit().remove("odAccess").remove("odRefresh").remove("odExp").remove("odAccount").apply()
    /** 登录进行中：PKCE 原文和防伪造的 state（去浏览器登录时 App 可能被系统关掉，所以存起来） */
    fun savePending(ctx: Context, verifier: String, state: String) = sp(ctx).edit().putString("pkce", encrypt(verifier)).putString("state", state).apply()
    fun pendingVerifier(ctx: Context) = decrypt(sp(ctx).getString("pkce", null))
    fun pendingState(ctx: Context) = sp(ctx).getString("state", null)
    fun clearPending(ctx: Context) = sp(ctx).edit().remove("pkce").remove("state").apply()

    // ---- WebDAV ----
    fun davUrl(ctx: Context) = sp(ctx).getString("davUrl", "") ?: ""
    fun davUser(ctx: Context) = sp(ctx).getString("davUser", "") ?: ""
    fun davFolder(ctx: Context) = sp(ctx).getString("davFolder", "日迹备份") ?: "日迹备份"
    fun davPass(ctx: Context) = decrypt(sp(ctx).getString("davPass", null)) ?: ""
    fun saveDav(ctx: Context, url: String, user: String, pass: String, folder: String) = sp(ctx).edit()
        .putString("davUrl", url.trim()).putString("davUser", user.trim()).putString("davPass", encrypt(pass))
        .putString("davFolder", folder.trim().ifEmpty { "日迹备份" }).apply()

    // ---- 上次结果 ----
    fun lastAt(ctx: Context) = sp(ctx).getLong("lastAt", 0L)
    fun lastError(ctx: Context) = sp(ctx).getString("lastErr", "") ?: ""
    fun setResult(ctx: Context, error: String?) {
        val e = sp(ctx).edit().putString("lastErr", error ?: "")
        if (error == null) e.putLong("lastAt", System.currentTimeMillis())
        e.apply()
    }

    // ---- 系统密钥库加密（AES-GCM，密钥不出手机）----
    private const val ALIAS = "rijii_cloud"
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return g.generateKey()
    }
    private fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding"); c.init(Cipher.ENCRYPT_MODE, key())
        return Base64.encodeToString(c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }
    private fun decrypt(enc: String?): String? {
        if (enc.isNullOrEmpty()) return null
        return try {
            val all = Base64.decode(enc, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, all, 0, 12))
            String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
        } catch (e: Exception) { null }
    }
}
