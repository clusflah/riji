package app.birthdayreminder.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 液体玻璃（Liquid Glass）视觉开关与光学渲染：
 *  - 底层铺一层柔和的环境流光晕染（LiquidGlassBackdrop），让上面的半透明卡片有光可透
 *  - 卡片、按钮、选中格、标签胶囊通过 drawWithCache 画出四层光学细节：
 *      1. 半透明对角折射底色（左上亮透 → 右下微冷透，可叠强调色）
 *      2. 顶部弯月面高光弧（模拟水滴表面张力的微凸反光）
 *      3. 底部内折射焦散微光
 *      4. 左上与右下双斜向高光折射边（各向异性玻璃切面反光）
 *  - 关掉开关时全部退回原本的纯色简洁风，各页面不用写两套布局。
 */
val LocalLiquidGlass = staticCompositionLocalOf { true }

/**
 * 页面最底层的环境流光底幕：
 * 纯白或纯黑底色上半透明卡片看不出通透感；在四角和中部铺几团很淡的冰晶蓝、柔光紫、薄荷青、暖珊瑚光晕，
 * 列表上下滚动时玻璃卡片会自然透出底下的冷暖过渡。全部在 drawWithCache 里算好 Brush，滑动零开销。
 */
@Composable
fun LiquidGlassBackdrop(dark: Boolean, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier.drawWithCache {
            val w = size.width.coerceAtLeast(1f)
            val h = size.height.coerceAtLeast(1f)
            val r = maxOf(w, h)
            val base = if (dark) {
                Brush.linearGradient(
                    colors = listOf(Color(0xFF0D1118), Color(0xFF111722), Color(0xFF0E131C)),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            } else {
                Brush.linearGradient(
                    colors = listOf(Color(0xFFF3F7FD), Color(0xFFEEF4FB), Color(0xFFF6F4FB)),
                    start = Offset.Zero,
                    end = Offset(w, h)
                )
            }
            // 左上：天青冰蓝光晕
            val orb1 = Brush.radialGradient(
                colors = listOf(
                    if (dark) Color(0xFF2B6CB0).copy(alpha = 0.28f) else Color(0xFF64B5F6).copy(alpha = 0.22f),
                    Color.Transparent
                ),
                center = Offset(w * 0.15f, h * 0.12f),
                radius = r * 0.62f
            )
            // 右中偏上：柔光紫晕染
            val orb2 = Brush.radialGradient(
                colors = listOf(
                    if (dark) Color(0xFF6B46C1).copy(alpha = 0.20f) else Color(0xFFA78BFA).copy(alpha = 0.16f),
                    Color.Transparent
                ),
                center = Offset(w * 0.88f, h * 0.30f),
                radius = r * 0.55f
            )
            // 左下：薄荷青水光
            val orb3 = Brush.radialGradient(
                colors = listOf(
                    if (dark) Color(0xFF287A7A).copy(alpha = 0.18f) else Color(0xFF4FD1C5).copy(alpha = 0.14f),
                    Color.Transparent
                ),
                center = Offset(w * 0.12f, h * 0.72f),
                radius = r * 0.58f
            )
            // 右下：暖珊瑚微光
            val orb4 = Brush.radialGradient(
                colors = listOf(
                    if (dark) Color(0xFF97266D).copy(alpha = 0.14f) else Color(0xFFFF8FA3).copy(alpha = 0.13f),
                    Color.Transparent
                ),
                center = Offset(w * 0.85f, h * 0.88f),
                radius = r * 0.52f
            )
            onDrawBehind {
                drawRect(base)
                drawRect(orb1)
                drawRect(orb2)
                drawRect(orb3)
                drawRect(orb4)
            }
        }
    )
}

/**
 * 液体玻璃卡片与容器表面：
 * 开启液体玻璃时画出半透明折射层、顶部曲面高光和双斜向亮边；关闭时退回 fallbackColor（默认 Tg.card）。
 */
@Composable
fun Modifier.liquidGlassSurface(
    shape: Shape = RoundedCornerShape(14.dp),
    tint: Color? = null,
    tintAlpha: Float = 0.14f,
    borderWidth: Dp = 1.dp,
    fallbackColor: Color? = null
): Modifier {
    val glass = LocalLiquidGlass.current
    val dark = isDarkUi()
    val fallback = fallbackColor ?: (if (tint != null) tint.copy(alpha = tintAlpha) else Tg.card)
    if (!glass) return this.clip(shape).background(fallback)

    return this.clip(shape).drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = borderWidth.toPx()

        // 1. 玻璃主折射层（左上亮透 -> 右下微冷透，可混入强调色）
        val cTopStart: Color
        val cMid: Color
        val cBottomEnd: Color
        if (dark) {
            val b1 = Color(0xFF2B3444).copy(alpha = 0.72f)
            val b2 = Color(0xFF1E2430).copy(alpha = 0.56f)
            val b3 = Color(0xFF242C3B).copy(alpha = 0.64f)
            cTopStart = if (tint != null) tint.copy(alpha = tintAlpha * 1.15f).compositeOver(b1) else b1
            cMid = if (tint != null) tint.copy(alpha = tintAlpha * 0.85f).compositeOver(b2) else b2
            cBottomEnd = if (tint != null) tint.copy(alpha = tintAlpha * 1.35f).compositeOver(b3) else b3
        } else {
            val b1 = Color.White.copy(alpha = 0.82f)
            val b2 = Color(0xFFF7FAFF).copy(alpha = 0.64f)
            val b3 = Color(0xFFEDF4FF).copy(alpha = 0.74f)
            cTopStart = if (tint != null) tint.copy(alpha = tintAlpha * 0.85f).compositeOver(b1) else b1
            cMid = if (tint != null) tint.copy(alpha = tintAlpha * 0.65f).compositeOver(b2) else b2
            cBottomEnd = if (tint != null) tint.copy(alpha = tintAlpha * 1.25f).compositeOver(b3) else b3
        }
        val baseBrush = Brush.linearGradient(
            0.0f to cTopStart,
            0.5f to cMid,
            1.0f to cBottomEnd,
            start = Offset.Zero,
            end = Offset(w, h)
        )

        // 2. 顶部液态张力高光弧（模拟微凸水滴表面的镜面反光）
        val sheenHeight = (h * 0.46f).coerceAtLeast(1f)
        val sheenBrush = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (dark) 0.13f else 0.56f),
                Color.White.copy(alpha = 0f)
            ),
            startY = 0f,
            endY = sheenHeight
        )

        // 3. 底部内折射焦散微光
        val causticStart = (h * 0.70f).coerceAtMost(h - 1f)
        val causticColor = (tint ?: if (dark) Color(0xFF6AA8F0) else Color(0xFF64B5F6))
            .copy(alpha = if (dark) 0.09f else 0.07f)
        val causticBrush = Brush.verticalGradient(
            colors = listOf(Color.Transparent, causticColor),
            startY = causticStart,
            endY = h
        )

        // 4. 双斜向高光折射外框（左上强高光 -> 侧边色散过渡 -> 右下次级折射高光）
        val rimTint = tint ?: if (dark) Color(0xFF7BB4F7) else Color(0xFF8CC2FF)
        val borderBrush = if (dark) {
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = 0.42f),
                0.35f to Color.White.copy(alpha = 0.10f),
                0.68f to rimTint.copy(alpha = 0.18f),
                1.0f to Color.White.copy(alpha = 0.28f),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        } else {
            Brush.linearGradient(
                0.0f to Color.White.copy(alpha = 0.96f),
                0.35f to Color.White.copy(alpha = 0.38f),
                0.68f to rimTint.copy(alpha = 0.26f),
                1.0f to Color.White.copy(alpha = 0.80f),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        }

        onDrawBehind {
            drawRect(baseBrush)
            drawRect(sheenBrush, size = Size(w, sheenHeight))
            drawRect(causticBrush)
            drawOutline(outline, brush = borderBrush, style = Stroke(width = strokePx))
        }
    }
}

/**
 * 悬浮按钮（FAB）等高饱和按钮的液态水滴光泽层：
 * 在主色按钮上叠加顶部饱满的水滴反光弧、底部青白焦散光和晶莹的边缘折射环。
 */
@Composable
fun Modifier.liquidGlassButton(
    shape: Shape = RoundedCornerShape(16.dp),
    lightTint: Boolean = false
): Modifier {
    if (!LocalLiquidGlass.current) return this
    val dark = isDarkUi()
    return this.clip(shape).drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val outline = shape.createOutline(size, layoutDirection, this)
        val strokePx = 1.2.dp.toPx()

        val sheenH = (h * 0.48f).coerceAtLeast(1f)
        val sheen = Brush.verticalGradient(
            colors = listOf(
                Color.White.copy(alpha = if (lightTint) (if (dark) 0.22f else 0.65f) else 0.42f),
                Color.White.copy(alpha = 0f)
            ),
            startY = 0f,
            endY = sheenH
        )
        val caustic = Brush.verticalGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFFB3E5FC).copy(alpha = if (lightTint) 0.20f else 0.30f)
            ),
            startY = h * 0.62f,
            endY = h
        )
        val rim = Brush.linearGradient(
            0.0f to Color.White.copy(alpha = if (dark) 0.62f else 0.90f),
            0.45f to Color.White.copy(alpha = 0.22f),
            1.0f to Color(0xFFD6EEFF).copy(alpha = if (dark) 0.48f else 0.72f),
            start = Offset.Zero,
            end = Offset(w, h)
        )
        onDrawWithContent {
            drawContent()
            drawRect(sheen, size = Size(w, sheenH))
            drawRect(caustic)
            drawOutline(outline, brush = rim, style = Stroke(width = strokePx))
        }
    }
}

/**
 * 顶栏 / 标签栏的液体玻璃底色：半透明冰晶渐变 + 底部一条细微的高光折射线。
 */
@Composable
fun Modifier.liquidGlassBar(fallbackColor: Color): Modifier {
    if (!LocalLiquidGlass.current) return this.background(fallbackColor)
    val dark = isDarkUi()
    return this.drawWithCache {
        val w = size.width.coerceAtLeast(1f)
        val h = size.height.coerceAtLeast(1f)
        val bg = if (dark) {
            Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1B212D).copy(alpha = 0.76f),
                    Color(0xFF151A24).copy(alpha = 0.62f)
                )
            )
        } else {
            Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.84f),
                    Color(0xFFF5F9FF).copy(alpha = 0.68f)
                )
            )
        }
        val line = Brush.horizontalGradient(
            0.0f to Color.White.copy(alpha = if (dark) 0.06f else 0.35f),
            0.5f to Color.White.copy(alpha = if (dark) 0.22f else 0.85f),
            1.0f to Color.White.copy(alpha = if (dark) 0.06f else 0.35f)
        )
        val linePx = 0.75.dp.toPx()
        onDrawBehind {
            drawRect(bg)
            drawLine(line, Offset(0f, h - linePx / 2), Offset(w, h - linePx / 2), strokeWidth = linePx)
        }
    }
}
