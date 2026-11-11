package app.birthdayreminder.ui.common

import android.os.Build
import android.view.View
import android.view.ViewParent
import android.view.Window
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider

/**
 * 液态玻璃（Liquid Glass）材质与光影系统：
 *  1. 页面底层：纯净白底（#FFFFFF → #F8FAFC），上半屏日历区留白保对比度，仅在右上轻掠处、日程流与右下角悬浮按钮区布置高明度弥散圆光斑
 *  2. 玻璃卡片：清透纯白填充（66%~70%）+ 精细 1px 线性渐变内描边（左上 90% 白 → 中间 22% 白 → 右下 48% 白）+ 顶部微凸白色内发光 + 镂空外弥散投影
 *  3. 悬浮按钮：去掉系统灰黑脏阴影与厚白外框，采用同色系彩色弥散投影 + 半透明渐变色 + 顶部水滴内发光
 *  4. 底部弹窗：联动底层页面 50px+ 高斯模糊与 145% 饱和度提亮（消除文字笔画残影）+ 86% 纯白填充 + 15% 轻透遮罩 + 顶部 1px 纯白高光弧线
 */
val LocalLiquidGlass = staticCompositionLocalOf { true }

object LiquidGlassState {
    /** 当前打开的玻璃底部弹窗数量；大于 0 时主界面自动开启大半径高斯模糊，彻底消除底部文字重影 */
    var activeSheets by mutableIntStateOf(0)
}

private fun findDialogWindow(view: View): Window? {
    var p: ViewParent? = view.parent
    while (p != null) {
        if (p is DialogWindowProvider) return p.window
        p = p.parent
    }
    return null
}

/**
 * Android 12+ 景深模糊 + 145% 饱和度增强（纯白 R=G=B 在饱和度矩阵下保持纯白不变，底层彩斑更鲜亮清透）。
 */
@RequiresApi(Build.VERSION_CODES.S)
fun createSheetBackdropRenderEffect(blurPx: Float, saturation: Float): RenderEffect {
    val blur = android.graphics.RenderEffect.createBlurEffect(
        blurPx,
        blurPx,
        android.graphics.Shader.TileMode.CLAMP
    )
    val cm = android.graphics.ColorMatrix().apply { setSaturation(saturation) }
    val sat = android.graphics.RenderEffect.createColorFilterEffect(
        android.graphics.ColorMatrixColorFilter(cm)
    )
    return android.graphics.RenderEffect.createChainEffect(sat, blur).asComposeRenderEffect()
}

/**
 * 在底部弹窗（ModalBottomSheet）内部调用：
 * 打开期间让底层页面进入大半径景深模糊，并在支持跨窗口模糊的系统上同步设置窗口级模糊，杜绝日历数字透底重影。
 */
@Composable
fun RegisterLiquidGlassSheet() {
    if (!LocalLiquidGlass.current) return
    val view = LocalView.current
    DisposableEffect(view) {
        LiquidGlassState.activeSheets++
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            findDialogWindow(view)?.let { win ->
                runCatching {
                    val blurPx = (52f * view.resources.displayMetrics.density).toInt()
                    win.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    win.attributes = win.attributes.apply { blurBehindRadius = blurPx }
                    win.setBackgroundBlurRadius(blurPx)
                }
            }
        }
        onDispose {
            LiquidGlassState.activeSheets = (LiquidGlassState.activeSheets - 1).coerceAtLeast(0)
        }
    }
}

/** 底部弹窗容器底色：支持硬件模糊时用 86% 清透纯白，不支持模糊的老系统降级为 97% 瓷白避免透字 */
@Composable
fun liquidGlassSheetContainerColor(): Color {
    val dark = isDarkUi()
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    return if (dark) {
        Color(0xFF181D27).copy(alpha = if (canBlur) 0.88f else 0.97f)
    } else {
        Color.White.copy(alpha = if (canBlur) 0.86f else 0.97f)
    }
}

/** 底部弹窗背后的遮罩色：浅色下仅 15% 浓度，靠弹窗自身高光边与柔影拉开空间层级，顶部不再发闷 */
@Composable
fun liquidGlassScrimColor(): Color =
    Color.Black.copy(alpha = if (isDarkUi()) 0.36f else 0.15f)

/** 底部弹窗顶部的轻巧拖拽短条 */
@Composable
fun LiquidSheetDragHandle() {
    Box(
        modifier = Modifier
            .padding(top = 12.dp, bottom = 8.dp)
            .size(width = 36.dp, height = 4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isDarkUi()) 0.32f else 0.22f))
    )
}

/**
 * 页面底层的局部弥散圆光斑（Mesh Gradient）：
 * 底层保持纯白 / 极浅冷白（#FFFFFF → #F8FAFC），日历文字区留白不铺灰蓝底；
 * 仅在右上角轻掠处、下方日程流与右下角悬浮按钮区放置高明度天青蓝、蜜桃粉、薄荷绿弥散圆光斑。
 */
@Composable
fun LiquidGlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val base = if (dark) {
                Brush.verticalGradient(
                    0.0f to Color(0xFF0D1015),
                    0.45f to Color(0xFF0F131A),
                    1.0f to Color(0xFF111620)
                )
            } else {
                Brush.verticalGradient(
                    0.0f to Color(0xFFFFFFFF),
                    0.42f to Color(0xFFFFFFFF),
                    1.0f to Color(0xFFF8FAFC)
                )
            }
            // 右上角轻掠的一抹晴空天青（半径收敛在右上角，不染灰中左部日历数字）
            val topRightOrb = Brush.radialGradient(
                0.0f to (if (dark) Color(0xFF1D4ED8).copy(alpha = 0.22f) else Color(0xFF38BDF8).copy(alpha = 0.16f)),
                0.55f to (if (dark) Color(0xFF1E3A8A).copy(alpha = 0.08f) else Color(0xFFBAE6FD).copy(alpha = 0.05f)),
                1.0f to Color.Transparent,
                center = Offset(w * 0.94f, h * 0.11f),
                radius = w * 0.56f
            )
            // 日程流中右侧：高明度清透蜜桃粉弥散圆光斑
            val peachOrb = Brush.radialGradient(
                0.0f to (if (dark) Color(0xFF9D174D).copy(alpha = 0.18f) else Color(0xFFFDA4AF).copy(alpha = 0.24f)),
                0.52f to (if (dark) Color(0xFF831843).copy(alpha = 0.07f) else Color(0xFFFECDD3).copy(alpha = 0.10f)),
                1.0f to Color.Transparent,
                center = Offset(w * 0.88f, h * 0.62f),
                radius = w * 0.82f
            )
            // 日程流左下侧：清爽薄荷绿弥散圆光斑
            val mintOrb = Brush.radialGradient(
                0.0f to (if (dark) Color(0xFF115E59).copy(alpha = 0.18f) else Color(0xFF6EE7B7).copy(alpha = 0.20f)),
                0.52f to (if (dark) Color(0xFF134E4A).copy(alpha = 0.07f) else Color(0xFFA7F3D0).copy(alpha = 0.08f)),
                1.0f to Color.Transparent,
                center = Offset(w * 0.10f, h * 0.75f),
                radius = w * 0.78f
            )
            // 右下角悬浮按钮区：高饱和天青蓝主光斑
            val bottomRightOrb = Brush.radialGradient(
                0.0f to (if (dark) Color(0xFF2563EB).copy(alpha = 0.28f) else Color(0xFF60A5FA).copy(alpha = 0.26f)),
                0.52f to (if (dark) Color(0xFF1E40AF).copy(alpha = 0.11f) else Color(0xFFBFDBFE).copy(alpha = 0.11f)),
                1.0f to Color.Transparent,
                center = Offset(w * 0.88f, h * 0.92f),
                radius = w * 0.82f
            )
            onDrawBehind {
                drawRect(base)
                drawRect(topRightOrb)
                drawRect(mintOrb)
                drawRect(peachOrb)
                drawRect(bottomRightOrb)
            }
        }
    )
}

/**
 * 镂空式柔和彩色弥散外投影：
 *  1. 必须在 clip 之前调用，让阴影向组件四周自然晕染
 *  2. 通过 ClipOp.Difference 挖空组件自身内部区域，绝不把阴影画在半透明玻璃底下导致卡片内部发灰发脏
 */
@Composable
fun Modifier.liquidShadow(
    color: Color,
    blurRadius: Dp = 16.dp,
    offsetY: Dp = 4.dp,
    shape: Shape = RoundedCornerShape(14.dp)
): Modifier {
    if (!LocalLiquidGlass.current || color.alpha <= 0.004f) return this
    return this.drawWithCache {
        val blurPx = blurRadius.toPx()
        val dyPx = offsetY.toPx()
        val rPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 14.dp.toPx()
        val inset = 0.5f
        val selfBody = Path().apply {
            if (size.width > 1f && size.height > 1f) {
                addRoundRect(
                    RoundRect(
                        left = inset,
                        top = inset,
                        right = size.width - inset,
                        bottom = size.height - inset,
                        cornerRadius = CornerRadius((rPx - inset).coerceAtLeast(0f))
                    )
                )
            }
        }
        val paint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && blurPx > 0.5f) {
            android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color.toArgb()
                this.maskFilter = android.graphics.BlurMaskFilter(blurPx, android.graphics.BlurMaskFilter.Blur.NORMAL)
            }
        } else null

        onDrawBehind {
            clipPath(selfBody, ClipOp.Difference) {
                if (paint != null) {
                    drawIntoCanvas { canvas ->
                        canvas.nativeCanvas.drawRoundRect(
                            0f,
                            dyPx,
                            size.width,
                            size.height + dyPx,
                            rPx,
                            rPx,
                            paint
                        )
                    }
                } else {
                    drawRoundRect(
                        color = color.copy(alpha = color.alpha * 0.45f),
                        topLeft = Offset(0f, dyPx * 0.5f),
                        size = size,
                        cornerRadius = CornerRadius(rPx, rPx)
                    )
                }
            }
        }
    }
}

/**
 * 通用液态玻璃卡片表面（日程卡片、待办卡片、设置分组、列表条等）：
 *  - 浅色下采用清透纯白填充 rgba(255,255,255,0.66)（上亮下透微凸体积感，彻底去掉灰蓝脏底）
 *  - 精细 1px 线性渐变内描边：左上 90% 白 → 中间 22% 白 → 右下 48% 白
 *  - 顶部微弱白色内发光（Y: 1dp, Blur: 2dp, rgba(255,255,255,0.8)）
 *  - 空气感冷调弥散投影：0 4dp 20dp rgba(100, 120, 160, 0.06)
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color? = null,
    tintAlpha: Float = 0.06f,
    shadowAlpha: Float = 0.06f,
    whiteFillAlpha: Float = 0.66f,
    fallbackColor: Color? = null
): Modifier {
    val glass = LocalLiquidGlass.current
    val dark = isDarkUi()
    val fallback = fallbackColor ?: (if (tint != null) tint.copy(alpha = tintAlpha * 1.6f) else Tg.card)
    if (!glass) return this.clip(shape).background(fallback)

    val shadowColor = if (dark) {
        Color.Black.copy(alpha = (shadowAlpha * 3.0f).coerceAtMost(0.26f))
    } else {
        (tint ?: Color(0xFF6478A0)).copy(alpha = shadowAlpha)
    }

    return this
        .liquidShadow(color = shadowColor, blurRadius = 18.dp, offsetY = 4.dp, shape = shape)
        .clip(shape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 14.dp.toPx()
            val strokePx = maxOf(1f, 0.8.dp.toPx())
            val half = strokePx / 2f

            // 1. 清透纯净的玻璃基底填充（顶部更亮、底部微透，营造微凸水滴体积感）
            val cStart: Color
            val cEnd: Color
            if (dark) {
                val b1 = Color(0xFF212836).copy(alpha = 0.74f)
                val b2 = Color(0xFF1A202C).copy(alpha = 0.66f)
                cStart = if (tint != null) tint.copy(alpha = tintAlpha * 1.6f).compositeOver(b1) else b1
                cEnd = if (tint != null) tint.copy(alpha = tintAlpha * 1.2f).compositeOver(b2) else b2
            } else {
                val b1 = Color.White.copy(alpha = (whiteFillAlpha + 0.05f).coerceAtMost(0.94f))
                val b2 = Color.White.copy(alpha = (whiteFillAlpha - 0.02f).coerceAtLeast(0.55f))
                cStart = if (tint != null) tint.copy(alpha = tintAlpha).compositeOver(b1) else b1
                cEnd = if (tint != null) tint.copy(alpha = tintAlpha * 0.7f).compositeOver(b2) else b2
            }
            val fillBrush = Brush.verticalGradient(colors = listOf(cStart, cEnd))

            // 2. 顶部微弱白色内发光（Y: 1px, Blur: 2px, rgba(255,255,255,0.8)）
            val innerGlowH = 3.dp.toPx().coerceAtMost(h * 0.35f)
            val innerGlowBrush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.14f else 0.80f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = innerGlowH
            )

            // 3. 精细 1px 线性渐变内描边：左上 90% 白 → 中间 22% 白 → 右下 48% 白
            val borderBrush = if (dark) {
                Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.38f),
                    0.5f to Color.White.copy(alpha = 0.08f),
                    1.0f to Color.White.copy(alpha = 0.22f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            } else {
                Brush.linearGradient(
                    0.0f to Color.White.copy(alpha = 0.92f),
                    0.5f to Color.White.copy(alpha = 0.22f),
                    1.0f to Color.White.copy(alpha = 0.50f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            }

            onDrawBehind {
                drawRect(fillBrush)
                drawRect(innerGlowBrush, size = Size(w, innerGlowH))
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 弹窗宫格小卡片专属表面（AddTile）：
 * 无需二次模糊；统一用纯白半透明 rgba(255,255,255,0.70) + 1px 纯白高光描边 + 极轻投影（0 2dp 10dp rgba(0,0,0,0.03)），
 * 让色彩只集中在中间的圆形图标上，干净清爽。
 */
@Composable
fun Modifier.liquidGlassTile(
    cornerRadius: Dp = 16.dp,
    fallbackColor: Color
): Modifier {
    val shape = RoundedCornerShape(cornerRadius)
    if (!LocalLiquidGlass.current) return this.clip(shape).background(fallbackColor)
    val dark = isDarkUi()
    return this
        .liquidShadow(
            color = Color.Black.copy(alpha = if (dark) 0.16f else 0.03f),
            blurRadius = 10.dp,
            offsetY = 2.dp,
            shape = shape
        )
        .clip(shape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = cornerRadius.toPx()
            val strokePx = maxOf(1f, 0.8.dp.toPx())
            val half = strokePx / 2f
            val fill = if (dark) Color(0xFF262E3D).copy(alpha = 0.70f) else Color.White.copy(alpha = 0.70f)
            val border = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (dark) 0.28f else 0.92f),
                0.5f to Color.White.copy(alpha = if (dark) 0.12f else 0.65f),
                1.0f to Color.White.copy(alpha = if (dark) 0.20f else 0.82f),
                start = Offset.Zero,
                end = Offset(w, h)
            )
            onDrawBehind {
                drawRect(fill)
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = border,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 圆形图标与头像底色（EventIcon / TgAvatar）：
 * 纯净色相轻透圆底 + 顶部细微受光弧边，不混灰底、不加外黑影，让图标色彩明快清澈。
 */
@Composable
fun Modifier.liquidIconCircle(color: Color, fallbackAlpha: Float = 0.12f): Modifier {
    if (!LocalLiquidGlass.current) return this.clip(CircleShape).background(color.copy(alpha = fallbackAlpha))
    val dark = isDarkUi()
    return this
        .clip(CircleShape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = minOf(w, h) / 2f
            val strokePx = maxOf(1f, 0.7.dp.toPx())
            val half = strokePx / 2f
            val fill = Brush.verticalGradient(
                colors = listOf(
                    color.copy(alpha = if (dark) 0.22f else 0.11f),
                    color.copy(alpha = if (dark) 0.16f else 0.16f)
                )
            )
            val rim = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (dark) 0.22f else 0.78f),
                    color.copy(alpha = if (dark) 0.10f else 0.12f)
                )
            )
            onDrawBehind {
                drawRect(fill)
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = rim,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 标签栏（AppTabs）选中态的圆润液态玻璃胶囊滑块：
 * 垫在文字下方渲染，带轻微冷蓝投影、清透高白胶囊底、顶部内发光与 1px 高光描边，取代生硬的底蓝横线。
 */
@Composable
fun Modifier.liquidTabPill(primary: Color): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = isDarkUi()
    return this
        .liquidShadow(
            color = primary.copy(alpha = if (dark) 0.24f else 0.12f),
            blurRadius = 10.dp,
            offsetY = 3.dp,
            shape = CircleShape
        )
        .clip(CircleShape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = minOf(w, h) / 2f
            val strokePx = maxOf(1f, 0.8.dp.toPx())
            val half = strokePx / 2f
            val fill = if (dark) {
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF253248).copy(alpha = 0.88f), Color(0xFF1D283A).copy(alpha = 0.80f))
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.90f),
                        Color(0xFFF0F7FF).copy(alpha = 0.82f)
                    )
                )
            }
            val topGlowH = 3.dp.toPx().coerceAtMost(h * 0.4f)
            val topGlow = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (dark) 0.20f else 0.90f), Color.Transparent),
                startY = 0f,
                endY = topGlowH
            )
            val border = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (dark) 0.42f else 0.96f),
                0.5f to primary.copy(alpha = if (dark) 0.20f else 0.18f),
                1.0f to Color.White.copy(alpha = if (dark) 0.24f else 0.68f),
                start = Offset.Zero,
                end = Offset(w, h)
            )
            onDrawBehind {
                drawRect(fill)
                drawRect(topGlow, size = Size(w, topGlowH))
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = border,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 日历选中格（如“11 号”方块）：
 * 清透冰蓝微凸填充（上亮下润，彻底去掉灰暗凹陷感）+ 顶部白色内高光 + 1px 亮白渐变描边。
 */
@Composable
fun Modifier.liquidDaySelected(
    isToday: Boolean,
    primary: Color,
    shape: Shape = RoundedCornerShape(12.dp)
): Modifier {
    val fallback = if (isToday) primary.copy(alpha = 0.16f) else primary.copy(alpha = 0.09f)
    if (!LocalLiquidGlass.current) return this.clip(shape).background(fallback)
    val dark = isDarkUi()
    return this
        .liquidShadow(
            color = primary.copy(alpha = if (isToday) 0.16f else 0.09f),
            blurRadius = 8.dp,
            offsetY = 2.dp,
            shape = shape
        )
        .clip(shape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 12.dp.toPx()
            val strokePx = maxOf(1f, 0.8.dp.toPx())
            val half = strokePx / 2f

            // 顶部冰白透亮、底部清澈晴空蓝，形成微凸饱满水滴感（不含任何灰分）
            val fillBrush = if (dark) {
                Brush.verticalGradient(
                    colors = listOf(
                        primary.copy(alpha = if (isToday) 0.34f else 0.22f),
                        primary.copy(alpha = if (isToday) 0.22f else 0.14f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    colors = listOf(
                        if (isToday) Color(0xFFEAF4FF).copy(alpha = 0.92f) else Color(0xFFF2F8FF).copy(alpha = 0.85f),
                        if (isToday) Color(0xFFCCE4FF).copy(alpha = 0.86f) else Color(0xFFDCEEFF).copy(alpha = 0.76f)
                    )
                )
            }
            val topHighlightH = 3.dp.toPx().coerceAtMost(h * 0.35f)
            val topHighlight = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = if (dark) 0.24f else 0.92f), Color.Transparent),
                startY = 0f,
                endY = topHighlightH
            )
            val borderBrush = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (dark) 0.48f else 0.96f),
                0.5f to primary.copy(alpha = if (dark) 0.24f else 0.22f),
                1.0f to Color.White.copy(alpha = if (dark) 0.28f else 0.68f),
                start = Offset.Zero,
                end = Offset(w, h)
            )
            onDrawBehind {
                drawRect(fillBrush)
                drawRect(topHighlight, size = Size(w, topHighlightH))
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 悬浮按钮（+ 号、AI 星星按钮）的液态水滴材质：
 *  - 去掉系统灰黑脏阴影与生硬厚白边
 *  - 使用同色系彩色弥散投影（蓝色 + 号用 rgba(59, 130, 246, 0.26)，Y: 6dp, Blur: 16dp）
 *  - 半透明渐变色填充（78%~86%）+ 顶部柔和白色微凸内发光（inset 0 1.5dp 2.5dp #FFF）+ 精细 1px 内亮边
 */
@Composable
fun Modifier.liquidFab(
    shape: Shape = RoundedCornerShape(16.dp),
    primary: Color,
    secondaryStyle: Boolean = false
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = isDarkUi()
    val shadowColor = if (secondaryStyle) {
        Color(0xFF3B82F6).copy(alpha = if (dark) 0.28f else 0.20f)
    } else {
        Color(0xFF3B82F6).copy(alpha = if (dark) 0.36f else 0.26f)
    }
    return this
        .liquidShadow(
            color = shadowColor,
            blurRadius = if (secondaryStyle) 14.dp else 16.dp,
            offsetY = if (secondaryStyle) 5.dp else 6.dp,
            shape = shape
        )
        .clip(shape)
        .drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val rPx = (shape as? CornerBasedShape)?.topStart?.toPx(size, this) ?: 16.dp.toPx()
            val strokePx = maxOf(1f, 0.8.dp.toPx())
            val half = strokePx / 2f

            val fillBrush = if (secondaryStyle) {
                if (dark) {
                    Brush.linearGradient(
                        colors = listOf(Color(0xFF233856).copy(alpha = 0.85f), Color(0xFF1B2B44).copy(alpha = 0.78f)),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                } else {
                    Brush.linearGradient(
                        colors = listOf(Color.White.copy(alpha = 0.88f), Color(0xFFE3F0FF).copy(alpha = 0.80f)),
                        start = Offset.Zero,
                        end = Offset(w, h)
                    )
                }
            } else {
                Brush.linearGradient(
                    0.0f to Color(0xFF60A5FA).copy(alpha = 0.80f),
                    1.0f to Color(0xFF3B82F6).copy(alpha = 0.86f),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            }

            // 顶部微凸白色内发光（inset 0 1.5dp 2.5dp #FFF）
            val insetGlowH = 3.5.dp.toPx().coerceAtMost(h * 0.38f)
            val insetGlow = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = if (secondaryStyle) (if (dark) 0.28f else 0.92f) else 0.78f),
                    Color.Transparent
                ),
                startY = 0f,
                endY = insetGlowH
            )

            // 精细 1px 渐变内亮边（绝不画厚重白外框）
            val borderBrush = Brush.linearGradient(
                0.0f to Color.White.copy(alpha = if (secondaryStyle) (if (dark) 0.42f else 0.95f) else 0.72f),
                0.5f to Color.White.copy(alpha = 0.18f),
                1.0f to Color.White.copy(alpha = if (secondaryStyle) (if (dark) 0.22f else 0.55f) else 0.38f),
                start = Offset.Zero,
                end = Offset(w, h)
            )

            onDrawBehind {
                drawRect(fillBrush)
                drawRect(insetGlow, size = Size(w, insetGlowH))
                if (w > strokePx && h > strokePx) {
                    drawRoundRect(
                        brush = borderBrush,
                        topLeft = Offset(half, half),
                        size = Size(w - strokePx, h - strokePx),
                        cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                        style = Stroke(width = strokePx)
                    )
                }
            }
        }
}

/**
 * 日期选择器等内部实心主色小胶囊的顶部微凸内发光。
 */
@Composable
fun Modifier.liquidGlassButton(
    shape: Shape = RoundedCornerShape(16.dp)
): Modifier {
    if (!LocalLiquidGlass.current) return this
    return this.clip(shape).drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val glowH = 2.5.dp.toPx().coerceAtMost(size.height * 0.35f)
        val glow = Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.55f), Color.Transparent),
            startY = 0f,
            endY = glowH
        )
        onDrawWithContent {
            drawContent()
            drawRect(glow, size = Size(w, glowH))
        }
    }
}

/**
 * 底部弹窗顶部边缘的 1px 纯白高光弧线：沿着弹窗 28dp 顶圆角上沿勾勒清亮空间边界。
 */
@Composable
fun Modifier.liquidSheetTopHighlight(cornerRadius: Dp = 28.dp): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = isDarkUi()
    return this.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val rPx = cornerRadius.toPx()
        val strokePx = maxOf(1f, 0.9.dp.toPx())
        val half = strokePx / 2f
        val topRim = Brush.verticalGradient(
            0.0f to Color.White.copy(alpha = if (dark) 0.45f else 0.98f),
            0.16f to Color.White.copy(alpha = if (dark) 0.10f else 0.30f),
            0.40f to Color.Transparent
        )
        onDrawWithContent {
            drawContent()
            if (w > strokePx && h > strokePx) {
                drawRoundRect(
                    brush = topRim,
                    topLeft = Offset(half, half),
                    size = Size(w - strokePx, h + rPx),
                    cornerRadius = CornerRadius((rPx - half).coerceAtLeast(0f)),
                    style = Stroke(width = strokePx)
                )
            }
        }
    }
}

/**
 * 顶栏底色：纯净通透、不画生硬横切线，与纯白顶层自然过渡。
 */
@Composable
fun Modifier.liquidGlassBar(fallbackColor: Color): Modifier {
    if (!LocalLiquidGlass.current) return this.background(fallbackColor)
    val dark = isDarkUi()
    return this.drawWithCache {
        val bg = if (dark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF0E1117).copy(alpha = 0.86f),
                    Color(0xFF0E1117).copy(alpha = 0.72f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.88f),
                    Color.White.copy(alpha = 0.70f)
                )
            )
        }
        onDrawBehind { drawRect(bg) }
    }
}
