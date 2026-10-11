package app.birthdayreminder.ui.common

import android.app.Activity
import android.os.Build
import app.birthdayreminder.domain.DisplayLogic
import app.birthdayreminder.notify.Prefs

/**
 * 高刷新率：告诉系统这个窗口想用屏幕支持的最高刷新率（同分辨率、至少 90Hz）。
 * 关掉开关时把偏好清掉，交回系统自动决定。系统 / 厂商仍可能在省电模式、过热时降低刷新率。
 */
fun applyRefreshRate(activity: Activity) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val window = activity.window ?: return
    val attrs = window.attributes
    if (!Prefs.highRefresh(activity)) {
        if (attrs.preferredDisplayModeId != 0 || attrs.preferredRefreshRate != 0f) {
            attrs.preferredDisplayModeId = 0
            attrs.preferredRefreshRate = 0f
            window.attributes = attrs
        }
        return
    }
    @Suppress("DEPRECATION")
    val display = (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) activity.display else activity.windowManager.defaultDisplay) ?: return
    val toMode = { m: android.view.Display.Mode -> DisplayLogic.Mode(m.modeId, m.physicalWidth, m.physicalHeight, m.refreshRate) }
    val id = DisplayLogic.pickHighRefresh(display.supportedModes.map(toMode), display.mode?.let(toMode)) ?: return
    val rate = display.supportedModes.first { it.modeId == id }.refreshRate
    if (attrs.preferredDisplayModeId != id) {
        attrs.preferredDisplayModeId = id
        attrs.preferredRefreshRate = rate
        window.attributes = attrs
    }
}
