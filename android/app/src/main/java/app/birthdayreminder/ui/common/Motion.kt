package app.birthdayreminder.ui.common

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/**
 * 日迹的动效（参照 iOS）：统一的缓动曲线和时长，所有过渡共用，改一处全局生效。
 */
object Motion {
    /** iOS 推入页面常用的曲线：起步快、落地很缓，显得轻 */
    val Ease = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)
    /** 普通的缓入缓出（淡入淡出、展开收起） */
    val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

    const val PAGE = 380          // 页面切换
    const val MONTH = 320         // 月历翻页
    const val TAB = 260           // 标签 / 子页切换
    const val FADE = 180

    // ---------------- 页面切换（导航）：新页从右边推进来，旧页向左退 1/4 并变暗 ----------------
    fun pushEnter(): EnterTransition = slideInHorizontally(tween(PAGE, easing = Ease)) { it } + fadeIn(tween(PAGE / 2))
    fun pushExit(): ExitTransition = slideOutHorizontally(tween(PAGE, easing = Ease)) { -it / 4 } + fadeOut(tween(PAGE), targetAlpha = 0.6f)
    fun popEnter(): EnterTransition = slideInHorizontally(tween(PAGE, easing = Ease)) { -it / 4 } + fadeIn(tween(PAGE), initialAlpha = 0.6f)
    fun popExit(): ExitTransition = slideOutHorizontally(tween(PAGE, easing = Ease)) { it } + fadeOut(tween(PAGE / 2, delayMillis = PAGE / 2))

    // ---------------- 月历翻页：整页滑过去，带一点淡入淡出 ----------------
    fun month(forward: Boolean): ContentTransform {
        val dir = if (forward) 1 else -1
        return (slideInHorizontally(tween(MONTH, easing = Ease)) { w -> dir * w } + fadeIn(tween(MONTH))) togetherWith
            (slideOutHorizontally(tween(MONTH, easing = Ease)) { w -> -dir * w / 2 } + fadeOut(tween(MONTH / 2)))
    }

    /** 拖动没过线时，弹回原位用的弹簧（不过冲，略带一点回弹感） */
    val snapBack = spring<Float>(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow)

    // ---------------- 标签切换：按方向滑入 1/5 宽度 + 淡入，轻而不晃 ----------------
    fun tab(forward: Boolean): ContentTransform {
        val dir = if (forward) 1 else -1
        return (slideInHorizontally(tween(TAB, easing = Ease)) { w -> dir * w / 5 } + fadeIn(tween(TAB))) togetherWith
            (slideOutHorizontally(tween(TAB, easing = Ease)) { w -> -dir * w / 5 } + fadeOut(tween(FADE)))
    }

    // ---------------- 内容刷新（比如换了选中的日期）：淡入并轻微上移 ----------------
    fun refresh(): ContentTransform =
        (fadeIn(tween(TAB, easing = Standard)) + slideInVertically(tween(TAB, easing = Ease)) { it / 24 }) togetherWith fadeOut(tween(FADE / 2))

    // ---------------- 折叠 / 展开 ----------------
    fun expand(): EnterTransition = expandVertically(tween(TAB, easing = Ease)) + fadeIn(tween(TAB))
    fun collapse(): ExitTransition = shrinkVertically(tween(FADE + 40, easing = Standard)) + fadeOut(tween(FADE))

    /** 拖动阻力：手指拖得越远，内容动得越少（像 iOS 的橡皮筋） */
    fun rubber(dx: Float, width: Float): Float {
        if (width <= 0f) return dx
        val x = kotlin.math.abs(dx) / width
        val d = (1f - 1f / (x * 0.9f + 1f)) * width      // 起步几乎 1:1 跟手，越远阻力越大
        return if (dx < 0) -d else d
    }
}
