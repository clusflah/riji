package app.birthdayreminder.domain

/**
 * 隐私空间的上锁状态。纯逻辑，时间由调用方传进来（方便测试）。
 *
 * - 验证通过后解锁；
 * - App 退到后台时记下时间；回到前台时，如果在后台待了超过 GRACE_MS（1 分钟）就重新上锁。
 *   给 1 分钟余量，是因为选图片、选位置、输入锁屏密码这些操作也会短暂离开 App，不能一离开就踢出去；
 * - 也可以手动立刻上锁。
 */
class PrivacyLock(private val graceMs: Long = GRACE_MS) {
    var unlocked: Boolean = false
        private set
    private var backgroundAt: Long? = null

    fun unlock() { unlocked = true; backgroundAt = null }

    fun lock() { unlocked = false; backgroundAt = null }

    fun onBackground(now: Long) { if (unlocked) backgroundAt = now }

    /** 回到前台：返回 true 表示刚刚因为超时重新上锁（界面要退出隐私相关页面） */
    fun onForeground(now: Long): Boolean {
        val at = backgroundAt ?: return false
        backgroundAt = null
        if (unlocked && now - at >= graceMs) { unlocked = false; return true }
        return false
    }

    companion object {
        const val GRACE_MS = 60_000L
    }
}

/** 私密相关的固定文字 */
object PrivacyText {
    const val TITLE = "隐私空间"
    const val HOW_TO_ENTER = "进入方法：在日历首页长按顶部的年月标题约 2 秒，再验证指纹或锁屏密码。App 里没有其它入口，请记住这个方法。"
}
