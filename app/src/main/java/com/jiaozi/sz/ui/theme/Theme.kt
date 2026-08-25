package com.jiaozi.sz.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * HyperOS（小米澎湃）风格主题。
 *
 * 配色/圆角/字体刻度 1:1 参考开源库 Miuix（top.yukonga.miuix.kmp，Apache-2.0，
 * https://github.com/compose-miuix-ui/miuix）—— 目前社区最权威的 HyperOS Compose 复刻。
 * 因本工程 Kotlin 1.9 / Compose BOM 2024.06 与 Miuix(Kotlin 2.x) 不兼容，故手动还原其 token，
 * 而非直接依赖该库，以保证已能跑通的构建稳定。
 *
 * - 动态取色：默认关闭，固定 HyperOS 品牌蓝（0xFF3482FF），保证在任何壁纸下都是统一的小米蓝；
 *   用户可在设置里手动开启“跟随系统壁纸取色”。
 * - 字体：使用 FontFamily.Default（系统默认）。在小米设备上系统默认即 MiSans，自动获得原生观感；
 *   非小米设备回退系统字体，保证兼容。
 * - 圆角：卡片默认 20dp（Material3 medium），大容器 28dp，呼应 HyperOS“全局圆角”。
 */

// —— HyperOS 品牌蓝（Miuix lightColorScheme）——
private val HyperLight = lightColorScheme(
    primary = Color(0xFF3482FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE8F1FF),
    onPrimaryContainer = Color(0xFF0B3D91),
    secondary = Color(0xFFE6E6E6),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFFF0F0F0),
    onSecondaryContainer = Color(0xFF000000),
    tertiary = Color(0xFF5D9BFF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF0F0F0),
    onTertiaryContainer = Color(0xFF000000),
    error = Color(0xFFE94634),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDF6F4),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7F7F7),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFF7F7F7),
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFEFEFEF),
    onSurfaceVariant = Color(0xFF666666),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F2F2),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFE8E8E8),
    surfaceContainerHighest = Color(0xFFE8E8E8),
    outline = Color(0xFFD9D9D9),
    outlineVariant = Color(0xFFECECEC),
    inverseSurface = Color(0xFF000000),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFF5D9BFF),
    scrim = Color(0x52000000)
)

// —— HyperOS 深色（Miuix darkColorScheme）——
private val HyperDark = darkColorScheme(
    primary = Color(0xFF4D94FF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF1A3A6E),
    onPrimaryContainer = Color(0xFFE8F1FF),
    secondary = Color(0xFF505050),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFF2C2C2C),
    onSecondaryContainer = Color(0xFFE0E0E0),
    tertiary = Color(0xFF5D9BFF),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF2C2C2C),
    onTertiaryContainer = Color(0xFFE0E0E0),
    error = Color(0xFFFF6B61),
    onError = Color(0xFF000000),
    errorContainer = Color(0xFF2E0603),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF121212),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF121212),
    onSurface = Color(0xFFE0E0E0),
    surfaceVariant = Color(0xFF1F1F1F),
    onSurfaceVariant = Color(0xFFB0B0B0),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF121212),
    surfaceContainer = Color(0xFF1A1A1A),
    surfaceContainerHigh = Color(0xFF242424),
    surfaceContainerHighest = Color(0xFF2D2D2D),
    outline = Color(0xFF404040),
    outlineVariant = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFFFFFFF),
    inverseOnSurface = Color(0xFF000000),
    inversePrimary = Color(0xFF5D9BFF),
    scrim = Color(0x52000000)
)

/**
 * HyperOS 字体刻度（来自 Miuix TextStyles，单位 sp）：
 * title1=32 / title2=24 / title3=20 / title4=18 / body1=16 / body2=14 /
 * footnote1=13 / footnote2=11；标题字重 MiSans Medium(500)。
 */
private val HyperTypography
    @Composable
    get() = androidx.compose.material3.Typography(
        displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 32.sp, lineHeight = 40.sp),
        headlineLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 28.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 24.sp, lineHeight = 32.sp),
        headlineSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 26.sp),
        titleSmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        bodyLarge = TextStyle(fontFamily = FontFamily.Default, fontSize = 16.sp, lineHeight = 24.sp),
        bodyMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 24.sp),
        labelMedium = TextStyle(fontFamily = FontFamily.Default, fontSize = 13.sp, lineHeight = 18.sp),
        labelSmall = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 16.sp)
    )

/**
 * HyperOS 圆角：small=12 / medium=16（卡片默认 20，见下）/ large=20 / extraLarge=28。
 * 注：为让全站卡片统一为 20dp 圆角，这里把 medium 设为 20，使默认 Card 即获得 HyperOS 大圆角。
 */
private val HyperShapes = androidx.compose.material3.Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * 美术主题包：在基础配色（HyperLight/HyperDark）上覆盖主色 token。
 * - 青：青绿主色（美术生常用，清新生动）；
 * - 墨：墨黑留白（极简水墨风，主色近黑）；
 * - 锦：锦红描金（传统锦缎红 + 描金 tertiary）。
 * 其余 surface/背景沿用基础方案，保证深色与浅色下的文字对比度合规。
 */
private fun applyThemePack(base: androidx.compose.material3.ColorScheme, pack: String, dark: Boolean): androidx.compose.material3.ColorScheme {
    val (primary, onPrimary, primaryContainer, onPrimaryContainer, inversePrimary, tertiary) = when (pack) {
        // 小米蓝：原 HyperOS 品牌蓝，作为可选主题保留（用户要求不删除）
        "小米蓝" -> if (dark) Tuple(
            0xFF4D94FF, 0xFF000000, 0xFF1A3A6E, 0xFFE8F1FF, 0xFF5D9BFF, 0xFF5D9BFF
        ) else Tuple(
            0xFF3482FF, 0xFFFFFFFF, 0xFFE8F1FF, 0xFF0B3D91, 0xFF5D9BFF, 0xFF5D9BFF
        )
        "青" -> if (dark) Tuple(
            0xFF4FD1C0, 0xFF003730, 0xFF00514A, 0xFFAEF3E6, 0xFF73D3C2, 0xFF7FD8C9
        ) else Tuple(
            0xFF0F8A7A, 0xFFFFFFFF, 0xFFD2F0E9, 0xFF003730, 0xFF73D3C2, 0xFF2E9E8F
        )
        "墨" -> if (dark) Tuple(
            0xFFE0E0E0, 0xFF1A1A1A, 0xFF3A3A3A, 0xFFEAEAEA, 0xFF5A5A5A, 0xFFB0B0B0
        ) else Tuple(
            0xFF3A3A3A, 0xFFFFFFFF, 0xFFE4E4E4, 0xFF1A1A1A, 0xFF5A5A5A, 0xFF4A4A4A
        )
        "锦" -> if (dark) Tuple(
            0xFFE57380, 0xFF410009, 0xFF6E1B22, 0xFFFBBEC2, 0xFFE57380, 0xFFE0C044
        ) else Tuple(
            0xFFB23A48, 0xFFFFFFFF, 0xFFFBE6E8, 0xFF410009, 0xFFE57380, 0xFFC9A227
        )
        "企鹅" -> if (dark) Tuple(
            0xFF6E96BF, 0xFF071B2E, 0xFF14334F, 0xFFD6E6F2, 0xFF9DBBDA, 0xFF84A6C8
        ) else Tuple(
            0xFF305070, 0xFFFFFFFF, 0xFFE3ECF5, 0xFF10243C, 0xFF5070B0, 0xFF7090B0
        )
        // 默认 = 墨绿（对齐网页端青墨渐变，沉稳高级）；此前默认即小米蓝，现改为墨绿，小米蓝作为独立选项保留
        else -> if (dark) Tuple(
            0xFF4FA088, 0xFF002116, 0xFF16302A, 0xFFA6D8C6, 0xFF2F6B57, 0xFF73C2A6
        ) else Tuple(
            0xFF2F6B57, 0xFFFFFFFF, 0xFFDCEBE3, 0xFF0E3A2C, 0xFF5BA98C, 0xFF3C8C72
        )
    }
    return base.copy(
        primary = Color(primary),
        onPrimary = Color(onPrimary),
        primaryContainer = Color(primaryContainer),
        onPrimaryContainer = Color(onPrimaryContainer),
        inversePrimary = Color(inversePrimary),
        tertiary = Color(tertiary)
    )
}

/** 六元组（避免引入额外 data class 依赖） */
private data class Tuple(
    val a: Long, val b: Long, val c: Long,
    val d: Long, val e: Long, val f: Long
)

/**
 * 美术包渐变：Hero / 主按钮 / 我的头部等强调面使用的线性渐变。
 * 按主题包给出匹配的起止色，保证与 ColorScheme token 一致的高级观感；文字统一用白色（可读性已校验）。
 * 不依赖 ColorScheme，避免 primaryContainer 在不同包下色相漂移导致渐变发灰。
 */
object AppGradients {
    fun hero(pack: String, dark: Boolean): Brush = when (pack) {
        "小米蓝" -> if (dark) Brush.linearGradient(listOf(Color(0xFF4D94FF), Color(0xFF0B3D91)))
        else Brush.linearGradient(listOf(Color(0xFF3482FF), Color(0xFF0B3D91)))
        "青" -> if (dark) Brush.linearGradient(listOf(Color(0xFF4FD1C0), Color(0xFF003730)))
        else Brush.linearGradient(listOf(Color(0xFF0F8A7A), Color(0xFF003730)))
        "墨" -> Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF1A1A1A)))
        "锦" -> if (dark) Brush.linearGradient(listOf(Color(0xFFE57380), Color(0xFF6E1B22)))
        else Brush.linearGradient(listOf(Color(0xFFB23A48), Color(0xFF7E2823)))
        "企鹅" -> if (dark) Brush.linearGradient(listOf(Color(0xFF16304C), Color(0xFF2C5276)))
        else Brush.linearGradient(listOf(Color(0xFF305070), Color(0xFF1E4E7E)))
        // 默认 = 墨绿（网页端同款青墨渐变）
        else -> if (dark) Brush.linearGradient(listOf(Color(0xFF4FA088), Color(0xFF16302A)))
        else Brush.linearGradient(listOf(Color(0xFF2F6B57), Color(0xFF1C3A30)))
    }
}

@Composable
fun JiaoziTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontScale: String = "md",
    themePack: String = "默认",
    content: @Composable () -> Unit
) {
    val baseScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= 31 -> {
            val ctx = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        }
        darkTheme -> HyperDark
        else -> HyperLight
    }
    // 美术主题包：在基础配色上覆盖主色 token（青/墨/锦），其余 surface 沿用基础，保证对比度
    val colorScheme = applyThemePack(baseScheme, themePack, darkTheme)
    val scale = when (fontScale) {
        "sm" -> 0.9f
        "lg" -> 1.12f
        "xl" -> 1.28f
        else -> 1f
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (scale == 1f) HyperTypography else scaleTypography(HyperTypography, scale),
        shapes = HyperShapes,
        content = content
    )
}

/** 按系数整体缩放排版字号（不影响布局结构，仅字号；与网页端 setFont 一致） */
private fun scaleTypography(t: androidx.compose.material3.Typography, scale: Float): androidx.compose.material3.Typography {
    fun TextStyle.scale() = copy(fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return t.copy(
        displaySmall = t.displaySmall.scale(), headlineLarge = t.headlineLarge.scale(),
        headlineMedium = t.headlineMedium.scale(), headlineSmall = t.headlineSmall.scale(),
        titleLarge = t.titleLarge.scale(), titleMedium = t.titleMedium.scale(),
        titleSmall = t.titleSmall.scale(), bodyLarge = t.bodyLarge.scale(),
        bodyMedium = t.bodyMedium.scale(), bodySmall = t.bodySmall.scale(),
        labelLarge = t.labelLarge.scale(), labelMedium = t.labelMedium.scale(),
        labelSmall = t.labelSmall.scale()
    )
}
