package app.birthdayreminder.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import androidx.compose.ui.unit.dp
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 直接照抄 www/index.html 里 :root 的 CSS 变量色值，保证换成原生后视觉不跳变
object LiColors {
    // 浅色（日迹的设计系统：简洁、轻盈、留白；蓝色只做强调）
    val tgBgLight = Color(0xFFFFFFFF)          // 页面、顶栏、弹窗
    val tgAccentLight = Color(0xFF4A90E2)      // 主色：按钮、选中、今天、链接
    val cardLight = Color(0xFFF6F8FA)          // 卡片 / 辅助背景（无边框，只靠浅底色、圆角和间距）
    val fillLight = Color(0xFFEEF1F4)          // 浅色填充（表头、代码底色等）
    val subLight = Color(0xFF6E737B)          // 次要文字（#7A7F87 稍加深，白底上对比度达标）
    val hairLight = Color(0xFFECEFF2)          // 分隔线
    val surfaceLight = Color(0xFFFFFFFF)
    val textLight = Color(0xFF202124)
    val mutedLight = Color(0xFF8E8E9A)
    val lineLight = Color(0xFFEBEBF0)
    val brandLight = Color(0xFFFF4D6D)
    val brandSoftLight = Color(0xFFFFE9EE)
    val goldLight = Color(0xFFF2A900)
    val goldDark = Color(0xFFFFD25E)
    val blueLight = Color(0xFF3D8BFF)
    val dangerLight = Color(0xFFE5384F)

    // 深色
    val tgBgDark = Color(0xFF121417)
    val tgAccentDark = Color(0xFF6AA8F0)
    val cardDark = Color(0xFF1C1F24)
    val tgDialogDark = Color(0xFF1E2228)
    val fillDark = Color(0xFF262A31)

    // 特殊日期的少量颜色（别的地方不用新颜色）
    val birthday = Color(0xFFF28A6B)           // 生日：粉橙（珊瑚色），和经期的玫红区分开
    val reminder = Color(0xFF8B7BE6)           // 提醒、待办：紫（月历圆点和图标同色）
    val diary = Color(0xFF4A90E2)              // 日记：蓝（和提醒区分开）
    val period = Color(0xFFE5486B)             // 经期：玫红
    val fertile = Color(0xFF3FA37A)            // 易孕期 / 排卵日：绿
    val term = Color(0xFF4A90E2)               // 节气 / 节日：蓝
    val holiday = Color(0xFFF2994A)            // 假期：橙
    val done = Color(0xFF34A853)               // 完成：绿
    val subDark = Color(0xFF9AA0A6)
    val hairDark = Color(0xFF2A2E35)
    val surfaceDark = Color(0xFF1A1A1F)
    val textDark = Color(0xFFE8EAED)
    val mutedDark = Color(0xFF8A8A96)
    val lineDark = Color(0xFF2B2B33)
    val brandDark = Color(0xFFFF6B85)
    val brandSoftDark = Color(0xFF3A1F29)
    val blueDark = Color(0xFF6AA5FF)
    val dangerDark = Color(0xFFFF6B7D)
}

@Composable
fun LiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    // 日迹的设计系统：浅色顶栏、留白、无边框的浅底卡片，蓝色只做强调
    val scheme = if (darkTheme) {
        darkColorScheme(
            background = LiColors.tgBgDark,
            surface = LiColors.tgBgDark,
            onBackground = LiColors.textDark,
            onSurface = LiColors.textDark,
            primary = LiColors.tgAccentDark,
            onPrimary = Color.White,
            primaryContainer = Color(0xFF1F3550),
            onPrimaryContainer = Color(0xFFD6E8FB),
            secondary = LiColors.tgAccentDark,
            // 选项块（FilterChip）选中时用的颜色：浅蓝底 + 蓝字（不再是默认的淡紫色；紫色只留给 AI）
            secondaryContainer = Color(0xFF1F3550),
            onSecondaryContainer = Color(0xFFCFE3FA),
            tertiary = LiColors.goldDark,
            error = LiColors.dangerDark,
            outline = LiColors.lineDark,
            outlineVariant = LiColors.hairDark,
            onSurfaceVariant = LiColors.subDark,
            surfaceVariant = LiColors.fillDark,
            surfaceContainerLowest = LiColors.cardDark,
            surfaceContainerLow = LiColors.tgDialogDark,       // 底部弹出层
            surfaceContainer = LiColors.tgDialogDark,          // 菜单
            surfaceContainerHigh = LiColors.tgDialogDark,      // 弹窗
            surfaceContainerHighest = LiColors.fillDark
        )
    } else {
        lightColorScheme(
            background = LiColors.tgBgLight,
            surface = LiColors.tgBgLight,
            onBackground = LiColors.textLight,
            onSurface = LiColors.textLight,
            primary = LiColors.tgAccentLight,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFEAF2FC),
            onPrimaryContainer = Color(0xFF0B3D6E),
            secondary = LiColors.tgAccentLight,
            secondaryContainer = Color(0xFFE8F2FC),
            onSecondaryContainer = Color(0xFF1C5FA8),
            tertiary = LiColors.goldLight,
            error = LiColors.dangerLight,
            outline = LiColors.lineLight,
            outlineVariant = LiColors.hairLight,
            onSurfaceVariant = LiColors.subLight,
            surfaceVariant = LiColors.fillLight,
            surfaceContainerLowest = LiColors.cardLight,
            surfaceContainerLow = LiColors.tgBgLight,
            surfaceContainer = LiColors.tgBgLight,
            surfaceContainerHigh = LiColors.tgBgLight,
            surfaceContainerHighest = LiColors.fillLight
        )
    }
    // 圆角：小元素 8、卡片 14、弹窗 / 底部弹出层 20
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(14.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )
    // 字号层级（中文阅读）：大标题 22 / 区块标题 17 / 正文 16 / 次要 15 / 说明 13 / 最小 12
    val base = Typography()
    val typography = Typography(
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp),
        titleMedium = base.titleMedium.copy(fontSize = 17.sp, lineHeight = 24.sp),
        titleSmall = base.titleSmall.copy(fontSize = 15.sp, lineHeight = 21.sp),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontSize = 13.sp, lineHeight = 19.sp),
        labelLarge = base.labelLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = base.labelMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp)
    )
    MaterialTheme(colorScheme = scheme, shapes = shapes, typography = typography, content = content)
}
