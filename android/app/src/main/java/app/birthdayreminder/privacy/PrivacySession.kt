package app.birthdayreminder.privacy

import android.app.Activity
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.birthdayreminder.domain.PrivacyLock
import app.birthdayreminder.domain.PrivacyText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** 整个 App 共用的隐私空间解锁状态（界面订阅 unlocked，上锁时自动退出隐私相关页面） */
object PrivacySession {
    private val lock = PrivacyLock()
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked

    val isUnlocked: Boolean get() = lock.unlocked

    fun unlock() { lock.unlock(); _unlocked.value = true }
    fun lock() { lock.lock(); _unlocked.value = false }
    fun onBackground(now: Long = System.currentTimeMillis()) = lock.onBackground(now)

    /** 回到前台；返回 true 表示因为离开太久刚刚上锁 */
    fun onForeground(now: Long = System.currentTimeMillis()): Boolean {
        val relocked = lock.onForeground(now)
        _unlocked.value = lock.unlocked
        return relocked
    }
}

/** 用指纹 / 面容 / 手机锁屏密码验证身份 */
object PrivacyAuth {
    private const val AUTHS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL

    /** 手机有没有设置锁屏（没有的话没法验证） */
    fun available(activity: FragmentActivity): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHS) == BiometricManager.BIOMETRIC_SUCCESS

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFail: (String) -> Unit
    ) {
        val prompt = BiometricPrompt(activity, ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    // 用户自己取消的不算错误，不弹提示
                    if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                        errorCode != BiometricPrompt.ERROR_CANCELED) onFail(errString.toString())
                }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(AUTHS)      // 允许锁屏密码时不能再设“取消”按钮，系统自带
            .build()
        prompt.authenticate(info)
    }
}

/**
 * 显示私密内容时：禁止截屏录屏，最近任务里显示空白。
 * 用计数：切换页面时两个私密页面会短暂同时存在，先离开的那个不能把保护关掉。
 */
@Composable
fun SecureWindow(enabled: Boolean) {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(enabled) {
        if (enabled) SecureCounter.acquire(activity)
        onDispose { if (enabled) SecureCounter.release(activity) }
    }
}

internal object SecureCounter {
    private var count = 0
    fun acquire(a: Activity) {
        if (count++ == 0) a.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun release(a: Activity) {
        if (count > 0 && --count == 0) a.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
